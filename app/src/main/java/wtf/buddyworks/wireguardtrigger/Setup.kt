package wtf.buddyworks.wireguardtrigger

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import wtf.buddyworks.wireguardtrigger.wg.WireGuard

/** Everything the app needs from the system and from WireGuard, checked on every resume. */
data class SetupState(
    val wireGuardInstalled: Boolean,
    val controlPermission: Boolean,
    /**
     * Android blocks background apps from starting services, so a battery-optimised WireGuard
     * receives "tunnel up" but cannot start its VPN service. "Unrestricted" lifts that.
     */
    val wireGuardUnrestricted: Boolean,
    val preciseLocation: Boolean,
    val backgroundLocation: Boolean,
    val locationOn: Boolean,
    val remoteControlConfirmed: Boolean,
    val batteryUnrestricted: Boolean,
) {
    /** Without these the app can't read Wi-Fi names or switch the tunnel. */
    val requiredDone: Boolean
        get() = wireGuardInstalled && controlPermission && wireGuardUnrestricted && preciseLocation && backgroundLocation && locationOn

    /** True when the Wi-Fi name is readable, in the foreground at least. */
    val canReadWifiName: Boolean
        get() = preciseLocation && locationOn

    val allDone: Boolean
        get() = requiredDone && remoteControlConfirmed && batteryUnrestricted

    companion object {
        fun read(context: Context, remoteControlConfirmed: Boolean): SetupState {
            val power = context.getSystemService(PowerManager::class.java)
            return SetupState(
                wireGuardInstalled = WireGuard.isInstalled(context),
                controlPermission = WireGuard.hasPermission(context),
                wireGuardUnrestricted = power.isIgnoringBatteryOptimizations(WireGuard.PACKAGE),
                preciseLocation = context.granted(Manifest.permission.ACCESS_FINE_LOCATION),
                backgroundLocation = context.granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                locationOn = context.getSystemService(LocationManager::class.java).isLocationEnabled,
                remoteControlConfirmed = remoteControlConfirmed,
                batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
            )
        }
    }
}

private fun Context.granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

object SystemScreens {
    fun appDetails(context: Context, packageName: String = context.packageName) = context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun locationSettings(context: Context) = context.startActivity(
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    /**
     * The system dialog that exempts the app from battery optimisation ("Unrestricted"). Play
     * restricts this for apps it lists; this app ships on GitHub, and a network watcher that
     * gets put to sleep would silently stop switching.
     */
    @SuppressLint("BatteryLife")
    fun batteryExemption(context: Context) {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(request) }.onFailure { appDetails(context) }
    }
}
