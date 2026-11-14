package wtf.buddyworks.wireguardtrigger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import wtf.buddyworks.wireguardtrigger.triggerApp

/** Restarts the service after a reboot or an app update, if automatic switching is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (context.triggerApp.store.config.value.enabled) TriggerService.start(context)
    }
}
