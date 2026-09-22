package com.tradelog.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tradelog.data.prefs.ThemeMode
import com.tradelog.ui.Panel
import com.tradelog.ui.theme.ColorTheme
import com.tradelog.ui.theme.Viz

/**
 * Appearance settings: light/dark mode and the gain/loss colour convention.
 *
 * Both apply the moment they are tapped — there is no save button, because a preview the user
 * has to confirm is a worse preview than the screen itself changing.
 */
@Composable
fun AppearanceScreen(
    colorTheme: ColorTheme,
    themeMode: ThemeMode,
    onColorThemeChange: (ColorTheme) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        Panel(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle("深浅主题")
                ThemeMode.entries.forEach { mode ->
                    OptionRow(
                        title = mode.label,
                        subtitle = mode.description,
                        selected = mode == themeMode,
                        onClick = { onThemeModeChange(mode) },
                    )
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle("涨跌配色")
                ColorTheme.entries.forEach { theme ->
                    OptionRow(
                        title = theme.label,
                        subtitle = theme.description,
                        selected = theme == colorTheme,
                        onClick = { onColorThemeChange(theme) },
                        trailing = { ThemeSwatch(theme) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "配色只影响显示，不改变任何金额或正负号。" +
                        "带正负号的数字始终表达同样的涨跌，所以即使分不清颜色也能读。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Viz.colors.mutedInk,
                )
            }
        }

        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Viz.colors.primaryInk)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Viz.colors.mutedInk)
        }
        trailing?.invoke()
    }
}

/**
 * Shows what a gain and a loss will look like under [theme].
 *
 * Renders signed figures rather than bare colour chips: the sign is what actually carries the
 * meaning, and the swatch should demonstrate that, not just the hue.
 */
@Composable
private fun ThemeSwatch(theme: ColorTheme) {
    val (up, down) = when (theme) {
        // Mono puts no colour on numbers, so both samples are plain ink.
        ColorTheme.MONO -> Viz.colors.primaryInk to Viz.colors.primaryInk
        ColorTheme.RED_GAIN -> SampleRed to SampleGreen
        ColorTheme.GREEN_GAIN -> SampleGreen to SampleRed
    }
    Column(horizontalAlignment = Alignment.End) {
        Text("+12.3%", style = MaterialTheme.typography.labelMedium, color = up)
        Text("-4.5%", style = MaterialTheme.typography.labelMedium, color = down)
    }
}

/** Sample steps for the picker: the validated light-mode pair, used only in this preview. */
private val SampleRed = Color(0xFF961A1A)
private val SampleGreen = Color(0xFF15A815)

@Composable
internal fun SectionTitle(text: String) {
    Column {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Viz.colors.primaryInk)
        Spacer(Modifier.height(8.dp))
    }
}
