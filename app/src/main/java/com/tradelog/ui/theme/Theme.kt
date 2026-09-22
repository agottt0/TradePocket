package com.tradelog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tradelog.data.prefs.ThemeMode

/**
 * Data-visualisation colours, kept separate from the Material scheme.
 *
 * [gain] and [loss] are chosen by the user's [ColorTheme]; [buy] and [sell] stay blue/orange
 * because a buy is not a gain — conflating the two would paint every purchase as a loss.
 */
data class VizColors(
    /** Buy marks. Blue/orange, validated for CVD: worst pair deltaE 24.7 light / 26.8 dark. */
    val buy: Color,
    val sell: Color,
    /** Profit. Themed; in [ColorTheme.MONO] this is plain ink. */
    val gain: Color,
    /** Loss. Themed; in [ColorTheme.MONO] this is plain ink. */
    val loss: Color,
    /**
     * Chart fill for a profit bar. Never plain ink — a mono *text* theme still needs bars to be
     * visible, so [ColorTheme.MONO] uses the neutral blue/gray pair here.
     */
    val gainFill: Color,
    val lossFill: Color,
    val surface: Color,
    val plane: Color,
    val primaryInk: Color,
    val secondaryInk: Color,
    val mutedInk: Color,
    val gridline: Color,
    val baseline: Color,
    val warning: Color,
    val critical: Color,
    /**
     * Status "good". Reserved and never themed — a system state like "all mail parsed" is not
     * a profit, so it must not flip colour when the gain/loss theme changes.
     */
    val ok: Color,
    val hairline: Color,
)

/**
 * Gain/loss steps per theme.
 *
 * The red/green pairs were picked for lightness separation, not just hue: red and green are the
 * pair colour-vision deficiency collapses hardest, and only a lightness gap survives it. The
 * chosen light steps (`#961a1a` / `#15a815`) clear every gate including deuteranope
 * separation (deltaE 19.2). The dark steps sit in the 6-8 warn band (deltaE 6.0), which is
 * permitted here because the sign and the bar's direction always carry the same meaning.
 */
private data class SignColors(
    val gain: Color,
    val loss: Color,
    val gainFill: Color,
    val lossFill: Color,
)

private fun signColors(theme: ColorTheme, dark: Boolean): SignColors {
    val red = if (dark) Color(0xFFE66767) else Color(0xFF961A1A)
    val green = if (dark) Color(0xFF15A815) else Color(0xFF15A815)
    val ink = if (dark) Color(0xFFFFFFFF) else Color(0xFF0B0B0B)
    // Neutral chart pair for the mono theme: the validated blue, and a recessive gray.
    val neutralUp = if (dark) Color(0xFF3987E5) else Color(0xFF2A78D6)
    val neutralDown = if (dark) Color(0xFF898781) else Color(0xFF898781)

    return when (theme) {
        ColorTheme.MONO -> SignColors(
            gain = ink,
            loss = ink,
            gainFill = neutralUp,
            lossFill = neutralDown,
        )

        ColorTheme.RED_GAIN -> SignColors(
            gain = red,
            loss = green,
            gainFill = red,
            lossFill = green,
        )

        ColorTheme.GREEN_GAIN -> SignColors(
            gain = green,
            loss = red,
            gainFill = green,
            lossFill = red,
        )
    }
}

private fun lightViz(theme: ColorTheme): VizColors {
    val signs = signColors(theme, dark = false)
    return VizColors(
        buy = Color(0xFF2A78D6),
        sell = Color(0xFFEB6834),
        gain = signs.gain,
        loss = signs.loss,
        gainFill = signs.gainFill,
        lossFill = signs.lossFill,
        surface = Color(0xFFFCFCFB),
        plane = Color(0xFFF9F9F7),
        primaryInk = Color(0xFF0B0B0B),
        secondaryInk = Color(0xFF52514E),
        mutedInk = Color(0xFF898781),
        gridline = Color(0xFFE1E0D9),
        baseline = Color(0xFFC3C2B7),
        warning = Color(0xFFFAB219),
        critical = Color(0xFFD03B3B),
        ok = Color(0xFF0CA30C),
        hairline = Color(0x1A0B0B0B),
    )
}

private fun darkViz(theme: ColorTheme): VizColors {
    val signs = signColors(theme, dark = true)
    return VizColors(
        buy = Color(0xFF3987E5),
        sell = Color(0xFFD95926),
        gain = signs.gain,
        loss = signs.loss,
        gainFill = signs.gainFill,
        lossFill = signs.lossFill,
        surface = Color(0xFF1A1A19),
        plane = Color(0xFF0D0D0D),
        primaryInk = Color(0xFFFFFFFF),
        secondaryInk = Color(0xFFC3C2B7),
        mutedInk = Color(0xFF898781),
        gridline = Color(0xFF2C2C2A),
        baseline = Color(0xFF383835),
        warning = Color(0xFFFAB219),
        critical = Color(0xFFE66767),
        ok = Color(0xFF0CA30C),
        hairline = Color(0x1AFFFFFF),
    )
}

val LocalVizColors = staticCompositionLocalOf { lightViz(ColorTheme.DEFAULT) }

object Viz {
    val colors: VizColors
        @Composable @ReadOnlyComposable
        get() = LocalVizColors.current

    val cardCorner = 16.dp
    val gutter = 16.dp

    /** Colour for a signed figure. Zero counts as a gain so it is never painted as a loss. */
    @Composable @ReadOnlyComposable
    fun signColor(value: Double): Color =
        if (value >= 0) LocalVizColors.current.gain else LocalVizColors.current.loss

    /** Chart fill for a signed figure — always a real colour, even in the mono theme. */
    @Composable @ReadOnlyComposable
    fun signFill(value: Double): Color =
        if (value >= 0) LocalVizColors.current.gainFill else LocalVizColors.current.lossFill
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2A78D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE2FB),
    onPrimaryContainer = Color(0xFF0D366B),
    secondary = Color(0xFF52514E),
    background = Color(0xFFF9F9F7),
    onBackground = Color(0xFF0B0B0B),
    surface = Color(0xFFFCFCFB),
    onSurface = Color(0xFF0B0B0B),
    surfaceVariant = Color(0xFFF0EFEC),
    onSurfaceVariant = Color(0xFF52514E),
    outline = Color(0xFFC3C2B7),
    outlineVariant = Color(0xFFE1E0D9),
    error = Color(0xFFD03B3B),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF3987E5),
    onPrimary = Color(0xFF08203D),
    primaryContainer = Color(0xFF184F95),
    onPrimaryContainer = Color(0xFFCDE2FB),
    secondary = Color(0xFFC3C2B7),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1A1A19),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF262625),
    onSurfaceVariant = Color(0xFFC3C2B7),
    outline = Color(0xFF383835),
    outlineVariant = Color(0xFF2C2C2A),
    error = Color(0xFFE66767),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun TradeLogTheme(
    colorTheme: ColorTheme = ColorTheme.DEFAULT,
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    content: @Composable () -> Unit,
) {
    // An explicit choice overrides the system; SYSTEM is the only mode that reads it, so
    // toggling dark mode in the picker takes effect without touching device settings.
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val viz = if (darkTheme) darkViz(colorTheme) else lightViz(colorTheme)
    CompositionLocalProvider(LocalVizColors provides viz) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
