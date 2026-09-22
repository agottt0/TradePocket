package com.tradelog.ui.returns

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tradelog.ui.Format
import com.tradelog.ui.LegendItem
import com.tradelog.ui.theme.Viz
import kotlin.math.abs
import kotlin.math.max

data class ProfitBar(
    val label: String,
    val profit: Double,
    val closedTrades: Int,
)

/**
 * Profit by period as a diverging bar chart, centred on zero.
 *
 * Diverging because the reader's question is polarity first — did this month make or lose
 * money — and magnitude second. Bars grow right from a shared zero line for gains and left
 * for losses, so the sign is carried by direction as well as hue, and the figure is direct-
 * labelled: colour is never the only channel.
 *
 * One shared scale across all periods, so bar lengths are comparable. All rows are one
 * currency; mixing currencies on one axis is never allowed.
 */
@Composable
fun ProfitByPeriodChart(
    bars: List<ProfitBar>,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val maxMagnitude = bars.maxOfOrNull { abs(it.profit) } ?: 0.0
    val scale = max(maxMagnitude, 1.0)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "各期已实现盈亏 · $currency",
                style = MaterialTheme.typography.titleSmall,
                color = Viz.colors.primaryInk,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendItem(Viz.colors.gainFill, "盈利")
                LegendItem(Viz.colors.lossFill, "亏损")
            }
        }

        Spacer(Modifier.height(12.dp))

        bars.forEach { bar ->
            DivergingRow(
                label = bar.label,
                value = bar.profit,
                fraction = (abs(bar.profit) / scale).toFloat(),
                currency = currency,
                closedTrades = bar.closedTrades,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DivergingRow(
    label: String,
    value: Double,
    fraction: Float,
    currency: String,
    closedTrades: Int,
) {
    val isGain = value >= 0
    val color = Viz.signFill(value)
    val description = "$label ${if (isGain) "盈利" else "亏损"} " +
        "${Format.money(abs(value), currency)}，$closedTrades 笔平仓"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Viz.colors.secondaryInk,
            modifier = Modifier.width(52.dp),
        )

        // The plot area: two mirrored halves meeting at a zero line in the middle.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                // Loss half — fills from the centre leftwards.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (!isGain) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                .height(14.dp)
                                .background(
                                    color,
                                    RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                                ),
                        )
                    }
                }
                // The zero line, always visible so the centre is unambiguous.
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Viz.colors.baseline),
                )
                // Gain half — fills from the centre rightwards.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (isGain) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                .height(14.dp)
                                .background(
                                    color,
                                    RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                                ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = if (value == 0.0) "0.00" else Format.signedMoney(value),
            style = MaterialTheme.typography.labelSmall,
            color = Viz.colors.secondaryInk,
            textAlign = TextAlign.End,
            modifier = Modifier.width(76.dp),
        )
    }
}
