package wtf.buddyworks.wireguardtrigger.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import wtf.buddyworks.wireguardtrigger.TriggerApp
import wtf.buddyworks.wireguardtrigger.data.ActivityEntry
import wtf.buddyworks.wireguardtrigger.data.Decision
import wtf.buddyworks.wireguardtrigger.data.Place
import wtf.buddyworks.wireguardtrigger.data.Rules
import wtf.buddyworks.wireguardtrigger.data.TunnelAction
import wtf.buddyworks.wireguardtrigger.triggerApp
import wtf.buddyworks.wireguardtrigger.wg.WireGuard

/**
 * Watches networks while automatic switching is on and tells WireGuard to bring the tunnel up
 * or down when the rules change their mind.
 *
 * A command is only sent when the wanted state changes (or the tunnel name does). If the user
 * switches the tunnel by hand, the app leaves it alone until the next network change.
 */
class TriggerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastSent: Pair<TunnelAction, String>? = null
    private var verifyJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notification = Notifications.service(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifications.ID_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ID_SERVICE, notification)
        }
        run(triggerApp)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @OptIn(FlowPreview::class)
    private fun run(app: TriggerApp) {
        val store = app.store

        // Stop when the user turns automatic switching off.
        scope.launch {
            store.config.first { !it.enabled }
            stopSelf()
        }

        // Act on rule changes. The debounce rides out Wi-Fi roaming and the moment between
        // leaving Wi-Fi and mobile data taking over.
        scope.launch {
            combine(app.network, store.config) { net, config -> Rules.decide(net, config)?.let { it to config.tunnel } }
                .filterNotNull()
                .distinctUntilChanged()
                .debounce(SETTLE_MS)
                .collect { (decision, tunnel) -> apply(app, decision, tunnel) }
        }
    }

    private fun apply(app: TriggerApp, decision: Decision, tunnel: String) {
        val action = decision.action
        if (action == null) {
            // Trusted Wi-Fi with "turn off" disabled: the user may switch the tunnel off by hand
            // here, so the next untrusted network must send "on" again.
            if (decision.place == Place.Trusted) lastSent = null
            return
        }
        if (tunnel.isEmpty()) return
        val key = action to tunnel
        if (key == lastSent) return
        lastSent = key

        val up = action == TunnelAction.Up
        val vpnBefore = app.network.value.vpnActive
        val result = WireGuard.setTunnel(this, tunnel, up)
        val kind = when (result) {
            WireGuard.SendResult.Sent -> if (up) ActivityEntry.Kind.TurnedOn else ActivityEntry.Kind.TurnedOff
            WireGuard.SendResult.NotInstalled -> ActivityEntry.Kind.WireGuardMissing
            WireGuard.SendResult.NoPermission -> ActivityEntry.Kind.PermissionMissing
        }
        Log.i(TAG, "${action.name} '$tunnel' (${decision.place}): $result")
        // The command is sent even when the VPN is already in the wanted state (it may be a
        // different tunnel), but only real changes and failures go into the activity list.
        if (result != WireGuard.SendResult.Sent || vpnBefore != up) {
            app.store.log(ActivityEntry(System.currentTimeMillis(), kind, decision.link, tunnel))
        }

        // WireGuard never answers, so watch the VPN to see whether it obeyed. When the VPN was
        // already in the wanted state there is nothing to learn.
        verifyJob?.cancel()
        if (result != WireGuard.SendResult.Sent || vpnBefore == up) return
        verifyJob = scope.launch {
            val obeyed = withTimeoutOrNull(TriggerApp.RESPONSE_TIMEOUT_MS) { app.network.first { it.vpnActive == up } } != null
            if (obeyed) {
                app.store.setRemoteControlConfirmed(true)
            } else {
                app.store.log(ActivityEntry(System.currentTimeMillis(), ActivityEntry.Kind.NoResponse, decision.link, tunnel))
            }
        }
    }

    companion object {
        private const val TAG = "TriggerService"
        private const val SETTLE_MS = 2_000L

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, TriggerService::class.java))
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Could not start from the background", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TriggerService::class.java))
        }
    }
}
