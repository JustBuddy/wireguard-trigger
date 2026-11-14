package wtf.buddyworks.wireguardtrigger.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import wtf.buddyworks.wireguardtrigger.SystemScreens
import wtf.buddyworks.wireguardtrigger.wg.WireGuard

/** What each setup step's button does. */
class SetupActions(
    val installWireGuard: () -> Unit,
    val allowControl: () -> Unit,
    val unrestrictWireGuard: () -> Unit,
    val allowPreciseLocation: () -> Unit,
    val allowBackgroundLocation: () -> Unit,
    val turnOnLocation: () -> Unit,
    val allowBattery: () -> Unit,
)

@Composable
fun rememberSetupActions(): SetupActions {
    val context = LocalContext.current
    val activity = LocalActivity.current

    // Once a permission is denied for good, Android stops showing its dialog: send the user
    // to the app's settings page instead.
    val onResult: (Map<String, Boolean>) -> Unit = { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty() && activity != null && denied.none { activity.shouldShowRationale(it) }) {
            SystemScreens.appDetails(context)
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), onResult)

    return SetupActions(
        installWireGuard = { WireGuard.openStorePage(context) },
        allowControl = { permissions.launch(arrayOf(WireGuard.PERMISSION_CONTROL_TUNNELS)) },
        // Android only offers the one-tap exemption dialog for the calling app itself.
        unrestrictWireGuard = { SystemScreens.appDetails(context, WireGuard.PACKAGE) },
        allowPreciseLocation = {
            permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        // Android opens the location settings page for this one, where "Allow all the time" is.
        allowBackgroundLocation = { permissions.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) },
        turnOnLocation = { SystemScreens.locationSettings(context) },
        allowBattery = { SystemScreens.batteryExemption(context) },
    )
}

private fun Activity.shouldShowRationale(permission: String) = shouldShowRequestPermissionRationale(permission)
