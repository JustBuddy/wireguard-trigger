package wtf.buddyworks.wireguardtrigger.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.launch
import wtf.buddyworks.wireguardtrigger.R
import wtf.buddyworks.wireguardtrigger.SetupState
import wtf.buddyworks.wireguardtrigger.data.Rules
import wtf.buddyworks.wireguardtrigger.service.TriggerService
import wtf.buddyworks.wireguardtrigger.triggerApp
import wtf.buddyworks.wireguardtrigger.ui.components.CardMargin
import wtf.buddyworks.wireguardtrigger.ui.components.CollapsingPane
import wtf.buddyworks.wireguardtrigger.ui.components.TextFieldDialog
import wtf.buddyworks.wireguardtrigger.ui.components.ToolbarHeight
import wtf.buddyworks.wireguardtrigger.ui.theme.OneUi
import wtf.buddyworks.wireguardtrigger.wg.WireGuard

private enum class Dialog { None, TunnelName, AddNetwork }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.triggerApp
    val store = app.store
    val config by store.config.collectAsStateWithLifecycle()
    val net by app.network.collectAsStateWithLifecycle()
    val activity by store.activity.collectAsStateWithLifecycle()
    val confirmed by store.remoteControlConfirmed.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var testState by remember { mutableStateOf(TestState.Idle) }
    var dialog by rememberSaveable { mutableStateOf(Dialog.None) }
    // Set when the remote-control step sends the user to WireGuard: test on the way back.
    var testOnReturn by rememberSaveable { mutableStateOf(false) }

    val onTest: () -> Unit = {
        testState = TestState.Running
        scope.launch { testState = if (app.testRemoteControl()) TestState.Idle else TestState.Failed }
    }

    // Permissions and system switches change in other screens: re-check on every return.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        if (testOnReturn) {
            testOnReturn = false
            if (testState != TestState.Running) onTest()
        }
        onPauseOrDispose { }
    }
    val setup = remember(resumes, confirmed) { SetupState.read(context, confirmed) }
    // A new location grant only reaches network callbacks registered after it.
    LaunchedEffect(setup.canReadWifiName, setup.backgroundLocation) { app.restartNetworkMonitor() }
    LaunchedEffect(config.enabled) {
        if (config.enabled) TriggerService.start(context) else TriggerService.stop(context)
    }

    val decision = Rules.decide(net, config)
    val actions = rememberSetupActions()

    fun LazyListScope.status() = item(key = "status") {
        StatusCard(net, decision, config, setup, store::setEnabled, Modifier.padding(horizontal = CardMargin))
    }
    fun LazyListScope.setupSteps() = item(key = "setup") {
        SetupSection(
            setup, config, actions, testState, onTest,
            onEnterTunnel = { dialog = Dialog.TunnelName },
            onOpenRemoteControl = {
                testOnReturn = true
                WireGuard.open(context)
            },
            modifier = Modifier.padding(horizontal = CardMargin),
        )
    }
    fun LazyListScope.networks() = item(key = "networks") {
        NetworksSection(
            net, config,
            onAdd = store::addTrusted,
            onAddManually = { dialog = Dialog.AddNetwork },
            onRemove = store::removeTrusted,
            modifier = Modifier.padding(horizontal = CardMargin),
        )
    }
    fun LazyListScope.tunnel() = item(key = "tunnel") {
        TunnelSection(
            config,
            onEditName = { dialog = Dialog.TunnelName },
            onOffOnTrustedChange = store::setOffOnTrusted,
            onOpenWireGuard = { WireGuard.open(context) },
            modifier = Modifier.padding(horizontal = CardMargin),
        )
    }
    fun LazyListScope.history() = item(key = "activity") {
        ActivitySection(activity, store::clearActivity, Modifier.padding(horizontal = CardMargin))
    }

    val fold = rememberFoldingFeature()
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val title = stringResource(R.string.app_name)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(OneUi.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        val split = computeSplit(maxWidth, fold, density)
        if (split == null) {
            // Cover screen or a narrow window: one scrolling column.
            CollapsingPane(
                title = title,
                subtitle = null,
                expandable = maxHeight >= 560.dp,
                listState = rememberLazyListState(),
                topInset = topInset,
                bottomPadding = bottomInset + 24.dp,
            ) {
                status()
                setupSteps()
                networks()
                tunnel()
                history()
            }
        } else {
            // Inner screen: status and settings on one side of the hinge, networks and
            // activity on the other.
            Row(Modifier.fillMaxSize()) {
                CollapsingPane(
                    title = title,
                    subtitle = null,
                    expandable = false,
                    listState = rememberLazyListState(),
                    topInset = topInset,
                    bottomPadding = bottomInset + 24.dp,
                    modifier = Modifier
                        .width(split.leftEnd)
                        .fillMaxHeight(),
                ) {
                    status()
                    setupSteps()
                    tunnel()
                }
                Spacer(Modifier.width(split.rightStart - split.leftEnd))
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(top = topInset + ToolbarHeight - SectionTopGap, bottom = bottomInset + 24.dp),
                ) {
                    networks()
                    history()
                }
            }
        }
    }

    when (dialog) {
        Dialog.None -> Unit
        Dialog.TunnelName -> TextFieldDialog(
            title = stringResource(R.string.tunnel_name_dialog),
            label = stringResource(R.string.tunnel_name_label),
            supportingText = stringResource(R.string.tunnel_name_summary),
            initial = config.tunnel,
            confirmText = stringResource(R.string.action_save),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                store.setTunnel(it)
                // A different tunnel has not been tested yet.
                testState = TestState.Idle
                dialog = Dialog.None
            },
            onDismiss = { dialog = Dialog.None },
        )
        Dialog.AddNetwork -> TextFieldDialog(
            title = stringResource(R.string.add_manually),
            label = stringResource(R.string.network_name_label),
            initial = "",
            confirmText = stringResource(R.string.action_add),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                store.addTrusted(it)
                dialog = Dialog.None
            },
            onDismiss = { dialog = Dialog.None },
        )
    }
}

/** The section header's own top padding, so the right pane's first header lines up with the toolbar. */
private val SectionTopGap = 20.dp

private class Split(val leftEnd: Dp, val rightStart: Dp)

private val PaneGap = 8.dp

/** Two panes on wide windows, split at the hinge when there is one; null for a single column. */
private fun computeSplit(width: Dp, fold: FoldingFeature?, density: Density): Split? {
    if (width < 720.dp) return null
    if (fold != null && fold.orientation == FoldingFeature.Orientation.VERTICAL) {
        val left = with(density) { fold.bounds.left.toDp() }
        val right = with(density) { fold.bounds.right.toDp() }
        if (left >= 340.dp && width - right >= 340.dp) return Split(left - PaneGap, right + PaneGap)
    }
    val half = width / 2
    return Split(half - PaneGap, half + PaneGap)
}

@Composable
private fun rememberFoldingFeature(): FoldingFeature? {
    val activity = LocalActivity.current ?: return null
    val flow = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val info: WindowLayoutInfo? by flow.collectAsStateWithLifecycle(initialValue = null)
    return info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
}
