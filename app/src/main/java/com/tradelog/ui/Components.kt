package com.tradelog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradelog.data.perf.CurrencyPerformance
import com.tradelog.ui.theme.Viz
import java.util.Locale

/** Card surface used by every panel, so elevation and hairlines stay consistent. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Viz.cardCorner))
            .background(Viz.colors.surface)
            .border(1.dp, Viz.colors.hairline, RoundedCornerShape(Viz.cardCorner))
            .padding(16.dp),
    ) { content() }
}

/**
 * Stat tile: label, value, optional caption.
 *
 * The value uses proportional figures at display size (tabular digits look loose there);
 * only the tables use tabular alignment.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    captionColor: Color? = null,
    valueColor: Color? = null,
) {
    Panel(modifier = modifier) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Viz.colors.secondaryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = valueColor ?: Viz.colors.primaryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (caption != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = captionColor ?: Viz.colors.mutedInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A single legend entry: swatch plus its name, so identity never rests on colour alone. */
@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Viz.colors.secondaryInk,
        )
    }
}

/** Buy/sell chip: colour plus the word, never colour alone. */
@Composable
fun SideChip(isBuy: Boolean) {
    val color = if (isBuy) Viz.colors.buy else Viz.colors.sell
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (isBuy) "买入" else "卖出",
                style = MaterialTheme.typography.labelSmall,
                color = Viz.colors.primaryInk,
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Panel(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Viz.colors.primaryInk)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = Viz.colors.secondaryInk)
        }
    }
}

/**
 * One currency's headline result: total realized profit, its percentage, and the cost of what
 * is still held.
 *
 * The percentage is realized profit over the cost of the *closed* positions only. Open cost is
 * shown beside it but never folded into the denominator: shares still held have not returned
 * anything yet, and dividing by them would drag a real gain toward zero.
 */
@Composable
fun CurrencyResultCard(perf: CurrencyPerformance, modifier: Modifier = Modifier) {
    Panel(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "总盈亏 · ${perf.currency}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Viz.colors.secondaryInk,
                )
                Text(
                    text = perf.currency,
                    style = MaterialTheme.typography.labelSmall,
                    color = Viz.colors.mutedInk,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (perf.realized.isEmpty()) "0.00" else Format.signedMoney(perf.totalProfit),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (perf.realized.isEmpty()) {
                        Viz.colors.primaryInk
                    } else {
                        Viz.signColor(perf.totalProfit)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                perf.totalRoi?.let { roi ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Format.percent(roi),
                        style = MaterialTheme.typography.titleMedium,
                        color = Viz.signColor(roi),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = if (perf.realized.isEmpty()) {
                    "尚无平仓记录，卖出后才会产生盈亏"
                } else {
                    buildString {
                        append("成本 ").append(Format.compact(perf.totalCost))
                        append(" · ").append(perf.realized.size).append(" 笔平仓")
                        perf.winRate?.let {
                            append(" · 胜率 ")
                            append(String.format(Locale.US, "%.0f%%", it * 100))
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Viz.colors.mutedInk,
            )

            if (perf.openBySymbol.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "持仓成本",
                        style = MaterialTheme.typography.labelSmall,
                        color = Viz.colors.secondaryInk,
                    )
                    Text(
                        text = Format.money(perf.openCost, perf.currency),
                        style = MaterialTheme.typography.labelMedium,
                        color = Viz.colors.primaryInk,
                    )
                }
            }
        }
    }
}

/**
 * Current holdings for one currency: what is still held, and at what average cost.
 *
 * No profit column. There are no market prices in this app, so an unrealized figure would have
 * to be invented; the realized result for the same stock is shown in the closed list instead.
 */
@Composable
fun HoldingsList(perf: CurrencyPerformance, modifier: Modifier = Modifier) {
    val holdings = perf.openBySymbol
    Column(modifier = modifier) {
        Text(
            text = "当前持仓 · ${perf.currency}",
            style = MaterialTheme.typography.titleSmall,
            color = Viz.colors.primaryInk,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "只显示成本。App 读的是成交邮件，没有实时报价，无法计算浮动盈亏。",
            style = MaterialTheme.typography.labelSmall,
            color = Viz.colors.mutedInk,
        )
        Spacer(Modifier.height(10.dp))

        holdings.forEach { holding ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = holding.symbol,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Viz.colors.primaryInk,
                        maxLines = 1,
                    )
                    holding.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Viz.colors.mutedInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Format.quantity(holding.openQuantity) + " 股",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = Viz.colors.primaryInk,
                    )
                    Text(
                        text = holding.averageOpenPrice
                            ?.let { "均价 " + Format.price(it) }
                            ?: "",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = Viz.colors.mutedInk,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = Format.compact(holding.openCost),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = Viz.colors.secondaryInk,
                )
            }
        }
    }
}

/**
 * One currency, one line: profit, its percentage, and how much is still held.
 *
 * This is the overview's unit. It answers "where do I stand" and stops there — the per-stock
 * table, the period chart and the annualized figure all live on the returns screen, so the two
 * screens do not restate each other.
 */
@Composable
fun CurrencySummaryRow(perf: CurrencyPerformance, modifier: Modifier = Modifier) {
    val hasClosed = perf.realized.isNotEmpty()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = perf.currency,
                style = MaterialTheme.typography.titleSmall,
                color = Viz.colors.primaryInk,
            )
            Text(
                text = if (perf.openBySymbol.isEmpty()) {
                    "无持仓"
                } else {
                    "持 " + perf.openBySymbol.size + " 只 · 成本 " + Format.compact(perf.openCost)
                },
                style = MaterialTheme.typography.labelSmall,
                color = Viz.colors.mutedInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (hasClosed) Format.signedMoney(perf.totalProfit) else "0.00",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = if (hasClosed) Viz.signColor(perf.totalProfit) else Viz.colors.primaryInk,
                maxLines = 1,
            )
            Text(
                text = perf.totalRoi?.let { Format.percent(it) } ?: "尚无平仓",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = perf.totalRoi?.let { Viz.signColor(it) } ?: Viz.colors.mutedInk,
                maxLines = 1,
            )
        }
    }
}
