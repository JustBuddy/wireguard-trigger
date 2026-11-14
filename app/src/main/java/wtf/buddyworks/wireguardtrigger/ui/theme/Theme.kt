package wtf.buddyworks.wireguardtrigger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One UI 9 surfaces, sampled from Samsung's own apps on a Galaxy Z Fold8. The accent follows
 * the system colour palette (Settings › Wallpaper and style), as Samsung's apps do.
 */
@Immutable
data class OneUiColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textSection: Color,
    val divider: Color,
    val selection: Color,
    val highlight: Color,
    val pill: Color,
    val track: Color,
    val accent: Color,
    val onAccent: Color,
    /** Tunnel on, trusted network. */
    val good: Color,
    /** Needs attention. */
    val warn: Color,
    val danger: Color,
    val scrim: Color,
)

/** One UI's default blue, used when no system palette is available. */
private val OneUiBlue = Color(0xFF387AF5)

private fun lightColors(accent: Color, onAccent: Color) = OneUiColors(
    isDark = false,
    background = Color(0xFFF0F0F0),
    card = Color(0xFFFCFCFC),
    textPrimary = Color(0xFF010101),
    // One UI's own greys (#7A7A7A, #909090) are too faint for small text, so these are darkened
    // to reach WCAG AA (4.5:1) on the background, cards, highlights and pills.
    textSecondary = Color(0xFF666666),
    textSection = Color(0xFF6D6D6D),
    divider = Color(0xFFE4E4E7),
    selection = Color(0xFFDDDDDD),
    highlight = Color(0xFFEDEDED),
    pill = Color(0xFFE5E5E5),
    track = Color(0xFFE6E6E8),
    accent = accent,
    onAccent = onAccent,
    good = Color(0xFF14824A),
    warn = Color(0xFFA65A06),
    danger = Color(0xFFD93025),
    scrim = Color(0x33000000),
)

private fun darkColors(accent: Color, onAccent: Color) = OneUiColors(
    isDark = true,
    background = Color(0xFF000000),
    card = Color(0xFF171717),
    textPrimary = Color(0xFFFAFAFA),
    textSecondary = Color(0xFF9C9C9C),
    textSection = Color(0xFF8C8C8C),
    divider = Color(0xFF2A2A2A),
    selection = Color(0xFF333333),
    highlight = Color(0xFF242424),
    pill = Color(0xFF262626),
    track = Color(0xFF272729),
    accent = accent,
    onAccent = onAccent,
    good = Color(0xFF3DD68C),
    warn = Color(0xFFFFB45C),
    danger = Color(0xFFFF6B5E),
    scrim = Color(0x66000000),
)

val LocalOneUiColors = staticCompositionLocalOf { lightColors(OneUiBlue, Color.White) }

/** Text styles sized to match One UI 9 (the system font on Samsung devices is One UI Sans). */
@Immutable
data class OneUiType(
    val largeTitle: TextStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
    val toolbarTitle: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    val headline: TextStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    val itemTitle: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
    val itemSubtitle: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    val itemValue: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    val section: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    val body: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    val caption: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    val button: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    val nav: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
)

val LocalOneUiType = staticCompositionLocalOf { OneUiType() }

object OneUi {
    val colors: OneUiColors
        @Composable @ReadOnlyComposable get() = LocalOneUiColors.current
    val type: OneUiType
        @Composable @ReadOnlyComposable get() = LocalOneUiType.current

    val cardShape = RoundedCornerShape(26.dp)
    val paneShape = RoundedCornerShape(28.dp)
    val cardRadius = 26.dp
}

@Composable
fun TriggerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = remember(dark, context) {
        val system = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (dark) darkColors(system.primary, system.onPrimary) else lightColors(system.primary, system.onPrimary)
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.card, onSurface = colors.textPrimary,
            surfaceContainer = colors.card, surfaceContainerHigh = colors.card, onSurfaceVariant = colors.textSecondary,
            outlineVariant = colors.divider,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.card, onSurface = colors.textPrimary,
            surfaceContainer = colors.card, surfaceContainerHigh = colors.card, onSurfaceVariant = colors.textSecondary,
            outlineVariant = colors.divider,
        )
    }
    CompositionLocalProvider(
        LocalOneUiColors provides colors,
        LocalOneUiType provides OneUiType(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
