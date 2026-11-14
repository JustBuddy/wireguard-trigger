package wtf.buddyworks.wireguardtrigger.wg

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri

/**
 * The official WireGuard app's remote control, as used by Tasker and similar apps. It only obeys
 * when "Allow remote control apps" is on in its settings, and gives no reply either way: the
 * caller watches the VPN state to find out whether it worked.
 */
object WireGuard {
    const val PACKAGE = "com.wireguard.android"
    const val PERMISSION_CONTROL_TUNNELS = "com.wireguard.android.permission.CONTROL_TUNNELS"
    private const val ACTION_UP = "com.wireguard.android.action.SET_TUNNEL_UP"
    private const val ACTION_DOWN = "com.wireguard.android.action.SET_TUNNEL_DOWN"
    private const val EXTRA_TUNNEL = "tunnel"

    enum class SendResult { Sent, NotInstalled, NoPermission }

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
    }.isSuccess

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION_CONTROL_TUNNELS) == PackageManager.PERMISSION_GRANTED

    fun setTunnel(context: Context, tunnel: String, up: Boolean): SendResult {
        if (!isInstalled(context)) return SendResult.NotInstalled
        if (!hasPermission(context)) return SendResult.NoPermission
        val intent = Intent(if (up) ACTION_UP else ACTION_DOWN)
            .setPackage(PACKAGE)
            .putExtra(EXTRA_TUNNEL, tunnel)
            // Lets WireGuard's receiver start promptly even when its process is asleep.
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        context.sendBroadcast(intent)
        return SendResult.Sent
    }

    /** Opens the WireGuard app, or its store page when it is missing. */
    fun open(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (launch != null) {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            openStorePage(context)
        }
    }

    fun openStorePage(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$PACKAGE".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$PACKAGE".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
