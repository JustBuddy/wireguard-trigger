package wtf.buddyworks.wireguardtrigger.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
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
 * Android only adds the Wi-Fi name to callbacks registered while the app holds precise
 * location, so collectors restart this flow after a permission change.
 */
fun networkFlow(context: Context): Flow<NetSnapshot> = callbackFlow {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val links = LinkedHashMap<Network, Link>()
    val vpns = HashSet<Network>()
    // Callbacks run on ConnectivityThread; this lock keeps the two maps and the emission in step.
    val lock = Any()
    var linksReady = false

    fun emit() {
        trySend(NetSnapshot(links.values.toList(), vpnActive = vpns.isNotEmpty(), ready = linksReady))
    }

    val linkCallback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = synchronized(lock) {
            links[network] = linkOf(caps)
            linksReady = true
            emit()
        }

        override fun onLost(network: Network) = synchronized(lock) {
            links.remove(network)
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
    }
}.conflate()

private const val READY_TIMEOUT_MS = 1_000L

private fun linkOf(caps: NetworkCapabilities): Link = when {
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
        Link(LinkType.Wifi, Rules.cleanSsid((caps.transportInfo as? WifiInfo)?.ssid))
    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Link(LinkType.Ethernet)
    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Link(LinkType.Cellular)
    else -> Link(LinkType.Other)
}
