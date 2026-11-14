package wtf.buddyworks.wireguardtrigger.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wtf.buddyworks.wireguardtrigger.Labels
import wtf.buddyworks.wireguardtrigger.R
import wtf.buddyworks.wireguardtrigger.SetupState
import wtf.buddyworks.wireguardtrigger.data.ActivityEntry
import wtf.buddyworks.wireguardtrigger.data.Config
import wtf.buddyworks.wireguardtrigger.data.Decision
import wtf.buddyworks.wireguardtrigger.data.Link
import wtf.buddyworks.wireguardtrigger.data.LinkType
import wtf.buddyworks.wireguardtrigger.data.NetSnapshot
import wtf.buddyworks.wireguardtrigger.data.Place
import wtf.buddyworks.wireguardtrigger.ui.components.GroupCard
import wtf.buddyworks.wireguardtrigger.ui.components.IconBadge
import wtf.buddyworks.wireguardtrigger.ui.components.InsetDivider
import wtf.buddyworks.wireguardtrigger.ui.components.ListRow
import wtf.buddyworks.wireguardtrigger.ui.components.PillButton
import wtf.buddyworks.wireguardtrigger.ui.components.RowTextInset
import wtf.buddyworks.wireguardtrigger.ui.components.SectionHeader
import wtf.buddyworks.wireguardtrigger.ui.components.SwitchRow
import wtf.buddyworks.wireguardtrigger.ui.components.ToolbarIcon
import wtf.buddyworks.wireguardtrigger.ui.theme.OneUi

/** Tunnel state, where the phone is, and the master switch. */
@Composable
fun StatusCard(
    net: NetSnapshot,
    decision: Decision?,
    config: Config,
    setup: SetupState,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = OneUi.colors
    GroupCard(modifier) {
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crossfade(net.vpnActive, animationSpec = tween(250), label = "shield") { on ->
                IconBadge(
                    if (on) Icons.Rounded.GppGood else Icons.Outlined.Shield,
                    if (on) colors.good else colors.textSecondary,
                    size = 56.dp,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (net.vpnActive) R.string.tunnel_on else R.string.tunnel_off),
                    style = OneUi.type.headline,
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                // Network and verdict get one line each, so neither a long network name nor a
                // long translation pushes the other out, and the card never changes height.
                Text(
                    Labels.network(context, decision),
                    style = OneUi.type.itemSubtitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Labels.trust(context, decision),
                    style = OneUi.type.itemSubtitle,
                    color = when (decision?.place) {
                        Place.Trusted -> colors.good
                        Place.Untrusted -> colors.warn
                        else -> colors.textSecondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        InsetDivider(start = 20.dp)
        val ready = setup.requiredDone && config.tunnel.isNotEmpty()
        SwitchRow(
            title = stringResource(R.string.auto_switch),
            subtitle = when {
                !setup.requiredDone -> stringResource(R.string.auto_switch_needs_setup)
                config.tunnel.isEmpty() -> stringResource(R.string.auto_switch_needs_tunnel)
                else -> stringResource(R.string.auto_switch_summary, config.tunnel)
            },
            checked = config.enabled,
            onCheckedChange = onEnabledChange,
            // Switching off always works; switching on needs a finished setup.
            enabled = ready || config.enabled,
        )
    }
}

enum class TestState { Idle, Running, Failed }

/** The setup steps that are still open, required ones first. Hidden once everything is done. */
@Composable
fun SetupSection(
    setup: SetupState,
    config: Config,
    actions: SetupActions,
    testState: TestState,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OneUi.colors
    val steps = buildList<@Composable () -> Unit> {
        if (!setup.wireGuardInstalled) {
            add { SetupRow(Icons.Rounded.Download, R.string.setup_install, R.string.setup_install_summary, R.string.action_install, actions.installWireGuard) }
        } else if (!setup.controlPermission) {
            add { SetupRow(Icons.Rounded.VpnKey, R.string.setup_control, R.string.setup_control_summary, R.string.action_allow, actions.allowControl) }
        }
        if (setup.wireGuardInstalled && !setup.wireGuardUnrestricted) {
            add { SetupRow(Icons.Rounded.BatteryChargingFull, R.string.setup_wireguard_battery, R.string.setup_wireguard_battery_summary, R.string.action_open, actions.unrestrictWireGuard) }
        }
        if (!setup.preciseLocation) {
            add { SetupRow(Icons.Rounded.MyLocation, R.string.setup_location, R.string.setup_location_summary, R.string.action_allow, actions.allowPreciseLocation) }
        } else if (!setup.backgroundLocation) {
            add { SetupRow(Icons.Rounded.MyLocation, R.string.setup_background, R.string.setup_background_summary, R.string.action_allow, actions.allowBackgroundLocation) }
        }
        if (!setup.locationOn) {
            add { SetupRow(Icons.Rounded.LocationOn, R.string.setup_location_on, R.string.setup_location_on_summary, R.string.action_turn_on, actions.turnOnLocation) }
        }
        if (setup.wireGuardInstalled && setup.controlPermission && !setup.remoteControlConfirmed) {
            add {
                ListRow(
                    title = stringResource(R.string.setup_remote),
                    icon = Icons.Rounded.SettingsRemote,
                    iconTint = colors.warn,
                    subtitle = when {
                        config.tunnel.isEmpty() -> stringResource(R.string.setup_remote_needs_tunnel)
                        testState == TestState.Failed -> stringResource(R.string.setup_remote_failed)
                        else -> stringResource(R.string.setup_remote_summary)
                    },
                    subtitleColor = if (testState == TestState.Failed) colors.danger else colors.textSecondary,
                ) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(stringResource(R.string.action_open), actions.openWireGuard, filled = false)
                        if (config.tunnel.isNotEmpty()) {
                            Box(contentAlignment = Alignment.Center) {
                                PillButton(stringResource(R.string.action_test), { if (testState != TestState.Running) onTest() })
                                if (testState == TestState.Running) {
                                    CircularProgressIndicator(Modifier.size(20.dp), color = colors.onAccent, strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!setup.batteryUnrestricted) {
            add { SetupRow(Icons.Rounded.BatteryChargingFull, R.string.setup_battery, R.string.setup_battery_summary, R.string.action_allow, actions.allowBattery, recommended = true) }
        }
    }
    if (steps.isEmpty()) return
    Column(modifier) {
        SectionHeader(stringResource(R.string.section_setup))
        GroupCard {
            steps.forEachIndexed { index, step ->
                if (index > 0) InsetDivider(start = RowTextInset)
                step()
            }
        }
    }
}

@Composable
private fun SetupRow(
    icon: ImageVector,
    title: Int,
    summary: Int,
    action: Int,
    onClick: () -> Unit,
    recommended: Boolean = false,
) {
    val colors = OneUi.colors
    ListRow(
        title = stringResource(title),
        icon = icon,
        iconTint = if (recommended) colors.textSecondary else colors.warn,
        subtitle = if (recommended) stringResource(R.string.setup_recommended, stringResource(summary)) else stringResource(summary),
    ) {
        PillButton(stringResource(action), onClick, filled = !recommended)
    }
}

/** Trusted Wi-Fi networks, with a shortcut for the one the phone is on now. */
@Composable
fun NetworksSection(
    net: NetSnapshot,
    config: Config,
    onAdd: (String) -> Unit,
    onAddManually: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OneUi.colors
    val connected = net.links.filter { it.type == LinkType.Wifi }.mapNotNull { it.ssid }.toSet()
    val addable = connected.filterNot { it in config.trusted }.sorted()
    Column(modifier) {
        SectionHeader(stringResource(R.string.section_trusted))
        GroupCard {
            addable.forEach { ssid ->
                ListRow(
                    title = stringResource(R.string.add_network, ssid),
                    icon = Icons.Rounded.Add,
                    iconTint = colors.accent,
                    subtitle = stringResource(R.string.connected_now),
                    onClick = { onAdd(ssid) },
                )
                InsetDivider(start = RowTextInset)
            }
            config.trusted.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { ssid ->
                val isCurrent = ssid in connected
                ListRow(
                    title = ssid,
                    icon = Icons.Rounded.Wifi,
                    iconTint = if (isCurrent) colors.good else colors.textSecondary,
                    subtitle = if (isCurrent) stringResource(R.string.connected_now) else null,
                    subtitleColor = colors.good,
                    singleLine = true,
                ) {
                    ToolbarIcon(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.remove_network, ssid), { onRemove(ssid) }, tint = colors.textSecondary)
                }
                InsetDivider(start = RowTextInset)
            }
            ListRow(
                title = stringResource(R.string.add_manually),
                icon = Icons.Rounded.Add,
                iconTint = colors.textSecondary,
                onClick = onAddManually,
            )
        }
        if (config.trusted.isEmpty()) {
            Text(
                stringResource(R.string.trusted_empty),
                style = OneUi.type.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
}

/** Which tunnel to switch, and whether to switch it off at home. */
@Composable
fun TunnelSection(
    config: Config,
    onEditName: () -> Unit,
    onOffOnTrustedChange: (Boolean) -> Unit,
    onOpenWireGuard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OneUi.colors
    Column(modifier) {
        SectionHeader(stringResource(R.string.section_tunnel))
        GroupCard {
            ListRow(
                title = config.tunnel.ifEmpty { stringResource(R.string.tunnel_name_unset) },
                titleColor = if (config.tunnel.isEmpty()) colors.warn else colors.textPrimary,
                icon = Icons.Rounded.VpnKey,
                iconTint = if (config.tunnel.isEmpty()) colors.warn else colors.textSecondary,
                subtitle = stringResource(R.string.tunnel_name_summary),
                singleLine = true,
                onClick = onEditName,
            ) {
                ToolbarIcon(Icons.Rounded.Edit, stringResource(R.string.tunnel_name_edit), onEditName, tint = colors.textSecondary)
            }
            InsetDivider(start = 20.dp)
            SwitchRow(
                title = stringResource(R.string.off_on_trusted),
                subtitle = stringResource(R.string.off_on_trusted_summary),
                checked = config.offOnTrusted,
                onCheckedChange = onOffOnTrustedChange,
            )
            InsetDivider(start = 20.dp)
            ListRow(
                title = stringResource(R.string.open_wireguard),
                onClick = onOpenWireGuard,
            ) {
                ToolbarIcon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.open_wireguard), onOpenWireGuard, tint = colors.textSecondary)
            }
        }
    }
}

/** What the app switched, newest first. */
@Composable
fun ActivitySection(
    entries: List<ActivityEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = OneUi.colors
    Column(modifier) {
        SectionHeader(
            stringResource(R.string.section_activity),
            trailing = if (entries.isNotEmpty()) stringResource(R.string.action_clear) else null,
            onClick = if (entries.isNotEmpty()) onClear else null,
        )
        GroupCard {
            if (entries.isEmpty()) {
                ListRow(title = stringResource(R.string.activity_empty), titleColor = colors.textSecondary)
            }
            entries.take(MAX_SHOWN).forEachIndexed { index, entry ->
                if (index > 0) InsetDivider(start = RowTextInset)
                val (icon, tint) = when (entry.kind) {
                    ActivityEntry.Kind.TurnedOn -> Icons.Rounded.ArrowUpward to colors.good
                    ActivityEntry.Kind.TurnedOff -> Icons.Rounded.ArrowDownward to colors.textSecondary
                    else -> Icons.Rounded.WarningAmber to colors.warn
                }
                ListRow(
                    title = Labels.activity(context, entry.kind),
                    icon = icon,
                    iconTint = tint,
                    subtitle = "${linkLabel(entry.link)} · ${Labels.time(context, entry.time)}",
                )
            }
        }
    }
}

@Composable
private fun linkLabel(link: Link?): String = Labels.link(LocalContext.current, link)

private const val MAX_SHOWN = 20
