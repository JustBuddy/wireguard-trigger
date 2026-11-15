package wtf.buddyworks.wireguardtrigger.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import wtf.buddyworks.wireguardtrigger.data.Link
import wtf.buddyworks.wireguardtrigger.data.LinkType
import wtf.buddyworks.wireguardtrigger.data.NetSnapshot
import wtf.buddyworks.wireguardtrigger.data.Rules

/**
 * Emits the phone's non-VPN networks (with Wi-Fi names where Android reveals them) and whether
 * a VPN is up.
 *
 * Underlying networks and VPNs are watched with separate callbacks: once the tunnel is up it
 * becomes the default network, but the rules are about the Wi-Fi beneath it.
 *
 * Wi-Fi names come from a separate, short-lived callback. Android counts every callback that
 * carries location info as a location access (and shows the location indicator for it). Wi-Fi
 * signal and bandwidth updates arrive several times a minute, so a permanent location callback
 * kept the indicator on. A [Network] handle stands for one Wi-Fi connection and its name never
 * changes, so the name is looked up once per new Wi-Fi network and then kept.
 *
 * Android only adds the Wi-Fi name for apps that hold precise location, so collectors restart
 * this flow after a permission change.
 */
fun networkFlow(context: Context): Flow<NetSnapshot> = callbackFlow {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val links = LinkedHashMap<Network, Link>()
    val vpns = HashSet<Network>()
    // Wi-Fi name per network once it has been looked up. A null value means Android hid it.
    val names = HashMap<Network, String?>()
    // Callbacks run on ConnectivityThread; this lock keeps the maps and the emission in step.
    val lock = Any()
    var linksReady = false
    var lookup: ConnectivityManager.NetworkCallback? = null
    var lookupTimeout: Job? = null

    fun namePending() = links.any { (network, link) -> link.type == LinkType.Wifi && network !in names }

    fun emit() {
        // Until the name is known the rules can't tell trusted from untrusted Wi-Fi.
        val ready = linksReady && !namePending()
        trySend(NetSnapshot(links.values.toList(), vpnActive = vpns.isNotEmpty(), ready = ready))
    }

    fun stopLookup() {
        lookup?.let { cm.unregisterNetworkCallback(it) }
        lookup = null
        lookupTimeout?.cancel()
        lookupTimeout = null
    }

    fun setName(network: Network, ssid: String?) {
        names[network] = ssid
        links[network]?.let { links[network] = it.copy(ssid = ssid) }
    }

    fun startLookup() {
        if (lookup != null) return
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = synchronized(lock) {
                if (lookup !== this) return@synchronized
                setName(network, ssidOf(caps))
                if (!namePending()) stopLookup()
                emit()
            }
        }
        lookup = callback
        cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
        // Existing networks are reported right after registering. Should one never show up,
        // treat its name as hidden rather than keep the lookup (and location access) running.
        lookupTimeout = launch {
            delay(LOOKUP_TIMEOUT_MS)
            synchronized(lock) {
                if (lookup !== callback) return@synchronized
                links.forEach { (network, link) -> if (link.type == LinkType.Wifi && network !in names) setName(network, null) }
                stopLookup()
                emit()
            }
        }
    }

    // No location info here, so the frequent capability updates cost nothing.
    val linkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = synchronized(lock) {
            val link = linkOf(caps, names[network])
            links[network] = link
            linksReady = true
            if (link.type == LinkType.Wifi && network !in names) startLookup()
            emit()
        }

        override fun onLost(network: Network) = synchronized(lock) {
            links.remove(network)
            names.remove(network)
            if (!namePending()) stopLookup()
            emit()
        }

        override fun onUnavailable() = synchronized(lock) {
            linksReady = true
            emit()
        }
    }
    val vpnCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = synchronized(lock) {
            vpns += network
            emit()
        }

        override fun onLost(network: Network) = synchronized(lock) {
            vpns -= network
            emit()
        }
    }

    // The default request already excludes VPNs (NOT_VPN) and asks for INTERNET.
    cm.registerNetworkCallback(NetworkRequest.Builder().build(), linkCallback)
    cm.registerNetworkCallback(
        NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build(),
        vpnCallback,
    )
    // Existing networks are reported right after registering. With none at all no callback
    // fires, so the snapshot counts as complete after a short wait.
    launch {
        delay(READY_TIMEOUT_MS)
        synchronized(lock) {
            if (!linksReady) {
                linksReady = true
                emit()
            }
        }
    }
    awaitClose {
        cm.unregisterNetworkCallback(linkCallback)
        cm.unregisterNetworkCallback(vpnCallback)
        synchronized(lock) { stopLookup() }
    }
}.conflate()

private const val READY_TIMEOUT_MS = 1_000L
private const val LOOKUP_TIMEOUT_MS = 3_000L

private fun ssidOf(caps: NetworkCapabilities): String? = Rules.cleanSsid((caps.transportInfo as? WifiInfo)?.ssid)

private fun linkOf(caps: NetworkCapabilities, ssid: String?): Link = when {
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Link(LinkType.Wifi, ssid)
    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Link(LinkType.Ethernet)
    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Link(LinkType.Cellular)
    else -> Link(LinkType.Other)
}
