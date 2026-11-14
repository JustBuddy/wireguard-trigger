package wtf.buddyworks.wireguardtrigger

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import wtf.buddyworks.wireguardtrigger.data.NetSnapshot
import wtf.buddyworks.wireguardtrigger.data.Store
import wtf.buddyworks.wireguardtrigger.net.networkFlow
import wtf.buddyworks.wireguardtrigger.service.Notifications
import wtf.buddyworks.wireguardtrigger.wg.WireGuard

class TriggerApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store: Store by lazy { Store(this) }

    // Bumped after permission changes: callbacks registered without location access never
    // learn the Wi-Fi name, so the monitor has to register again.
    private val monitorGeneration = MutableStateFlow(0)

    /** Shared by the screen and the service, so both see the same snapshot. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val network: StateFlow<NetSnapshot> by lazy {
        monitorGeneration
            .flatMapLatest { networkFlow(this) }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), NetSnapshot.Unknown)
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
    }

    fun restartNetworkMonitor() {
        monitorGeneration.value++
    }

    /**
     * Flips the tunnel and flips it back, to find out whether WireGuard obeys our commands.
     * Returns true when the VPN changed state within the timeout.
     */
    suspend fun testRemoteControl(): Boolean {
        val tunnel = store.config.value.tunnel
        if (tunnel.isEmpty()) return false
        val before = network.first { it.ready }.vpnActive
        if (WireGuard.setTunnel(this, tunnel, up = !before) != WireGuard.SendResult.Sent) return false
        val changed = withTimeoutOrNull(RESPONSE_TIMEOUT_MS) { network.first { it.vpnActive != before } } != null
        WireGuard.setTunnel(this, tunnel, up = before)
        if (changed) store.setRemoteControlConfirmed(true)
        return changed
    }

    companion object {
        /** How long WireGuard gets to bring a tunnel up or down. */
        const val RESPONSE_TIMEOUT_MS = 12_000L
    }
}

val Context.triggerApp: TriggerApp
    get() = applicationContext as TriggerApp
