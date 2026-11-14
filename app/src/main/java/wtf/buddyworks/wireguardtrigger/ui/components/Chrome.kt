package wtf.buddyworks.wireguardtrigger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wtf.buddyworks.wireguardtrigger.ui.theme.OneUi

val ToolbarHeight = 64.dp

/**
 * A scrolling pane with a One UI app bar. When [expandable], the list starts with a large
 * title occupying the top third of the screen (One UI's "viewing area"), which scrolls away
 * and hands over to the small toolbar title.
 */
@Composable
fun CollapsingPane(
    title: String,
    subtitle: String?,
    expandable: Boolean,
    listState: LazyListState,
    topInset: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val headerHeight = if (expandable) (maxHeight * 0.36f - ToolbarHeight).coerceAtLeast(104.dp) else 0.dp
        val headerPx = with(LocalDensity.current) { headerHeight.toPx() }
        val collapse by remember(expandable, headerPx, listState) {
            derivedStateOf {
                when {
                    !expandable -> 1f
                    listState.firstVisibleItemIndex > 0 -> 1f
                    else -> (listState.firstVisibleItemScrollOffset / headerPx).coerceIn(0f, 1f)
                }
            }
        }
        // Like One UI, the large title never rests half-collapsed: it snaps open or shut.
        LaunchedEffect(listState, expandable, headerPx) {
            if (!expandable) return@LaunchedEffect
            snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                val offset = listState.firstVisibleItemScrollOffset
                if (!scrolling && listState.firstVisibleItemIndex == 0 && offset > 0 && offset < headerPx) {
                    listState.animateScrollToItem(0, if (offset < headerPx / 2) 0 else headerPx.toInt())
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topInset + ToolbarHeight, bottom = bottomPadding),
        ) {
            if (expandable) {
                item(key = "large-title", contentType = "large-title") {
                    LargeTitle(
                        title = title,
                        subtitle = subtitle,
                        height = headerHeight,
                        modifier = Modifier.graphicsLayer { alpha = (1f - collapse * 1.7f).coerceIn(0f, 1f) },
                    )
                }
            }
            content()
        }
        Toolbar(
            title = title,
            topInset = topInset,
            titleAlpha = { if (expandable) ((collapse - 0.6f) / 0.4f).coerceIn(0f, 1f) else 1f },
            navigationIcon = navigationIcon,
            actions = actions,
        )
    }
}

@Composable
fun Toolbar(
    title: String,
    topInset: Dp,
    titleAlpha: () -> Float = { 1f },
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(OneUi.colors.background)
            .padding(top = topInset)
            .height(ToolbarHeight)
            .padding(start = if (navigationIcon != null) 8.dp else 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null) {
            navigationIcon()
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            style = OneUi.type.toolbarTitle,
            color = OneUi.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = titleAlpha() },
        )
        actions()
    }
}

@Composable
private fun LargeTitle(title: String, subtitle: String?, height: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .height(height)
            .padding(start = 24.dp, end = 24.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(title, style = OneUi.type.largeTitle, color = OneUi.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = OneUi.type.body, color = OneUi.colors.textSecondary, maxLines = 2)
        }
    }
}
