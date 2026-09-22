package com.tradelog.ui.returns

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradelog.data.perf.CurrencyPerformance
import com.tradelog.data.perf.PeriodPerformance
import com.tradelog.data.perf.SymbolPerformance
import com.tradelog.ui.CurrencyResultCard
import com.tradelog.ui.EmptyState
import com.tradelog.ui.Format
import com.tradelog.ui.HoldingsList
import com.tradelog.ui.Panel
import com.tradelog.ui.StatTile
import com.tradelog.ui.theme.Viz
import java.util.Locale

private enum class Grain(val label: String) {
    MONTH("按月"),
    YEAR("按年"),
}

/**
 * The detailed view: every figure the app can derive, for one currency at a time.
 *
 * The overview answers "where do I stand"; this screen answers "how did I get there" — return
 * rates including the time-weighted annualized figure, win rate, best and worst round trips,
 * profit per period as a chart and a table, and a per-stock breakdown.
 *
 * Currencies never mix: there are no exchange rates here, so a figure spanning them would be
 * meaningless. The currency chips pick exactly one.
 */
@Composable
fun ReturnsScreen(
    performance: List<CurrencyPerformance>,
    modifier: Modifier = Modifier,
) {
    var currencyIndex by remember { mutableStateOf(0) }
    var grain by remember { mutableStateOf(Grain.MONTH) }

    if (performance.isEmpty()) {
        Column(modifier.padding(16.dp)) {
            EmptyState(
                title = "还没有可统计的收益",
                body = "收益要在平仓后才能算出来。同步邮件或手动录入买入和卖出记录后，" +
                    "这里会显示回报率、年化、各期盈亏和每只股票的明细。",
            )
        }
        return
    }

    val selected = performance[currencyIndex.coerceIn(0, performance.lastIndex)]
    val periods = if (grain == Grain.MONTH) selected.byMonth else selected.byYear

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (performance.size > 1) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    performance.forEachIndexed { index, p ->
                        FilterChip(
                            selected = index == currencyIndex,
                            onClick = { currencyIndex = index },
                            label = { Text(p.currency, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }

        item { CurrencyResultCard(selected) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    valueColor = selected.totalRoi?.let { Viz.signColor(it) },
                    label = "总回报率",
                    value = selected.totalRoi?.let { Format.percent(it) } ?: "—",
                    caption = "盈亏 ÷ 已平仓成本",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    valueColor = selected.annualizedReturn?.let { Viz.signColor(it) },
                    label = "年化回报率",
                    value = selected.annualizedReturn?.let { Format.percent(it) } ?: "—",
                    caption = selected.averageHoldingDays
                        ?.let { "平均持有 " + Format.holdingPeriod(it) }
                        ?: "需平仓记录",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "胜率",
                    value = selected.winRate?.let {
                        String.format(Locale.US, "%.0f%%", it * 100)
                    } ?: "—",
                    caption = "${selected.winCount} 盈 / ${selected.lossCount} 亏",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "平仓笔数",
                    value = selected.realized.size.toString(),
                    caption = if (selected.openBySymbol.isEmpty()) {
                        "无持仓"
                    } else {
                        "另持 ${selected.openBySymbol.size} 只"
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Best and worst are the two round trips worth naming: an average hides both.
        if (selected.realized.size > 1) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    selected.bestTrade?.let { best ->
                        StatTile(
                            valueColor = Viz.signColor(best.profit),
                            label = "最佳一笔",
                            value = Format.signedMoney(best.profit),
                            caption = best.symbol + (best.roi?.let { " · " + Format.percent(it) } ?: ""),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    selected.worstTrade?.let { worst ->
                        StatTile(
                            valueColor = Viz.signColor(worst.profit),
                            label = "最差一笔",
                            value = Format.signedMoney(worst.profit),
                            caption = worst.symbol + (worst.roi?.let { " · " + Format.percent(it) } ?: ""),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (selected.openBySymbol.isNotEmpty()) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    HoldingsList(selected)
                }
            }
        }

        if (periods.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Grain.entries.forEach { g ->
                        FilterChip(
                            selected = grain == g,
                            onClick = { grain = g },
                            label = { Text(g.label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }

            item {
                Panel(Modifier.fillMaxWidth()) {
                    ProfitByPeriodChart(
                        bars = periods.map { period ->
                            ProfitBar(
                                label = periodLabel(period, periods.size),
                                profit = period.profit,
                                closedTrades = period.closedTrades,
                            )
                        },
                        currency = selected.currency,
                    )
                }
            }

            // Every chart in this app has a readable equivalent, so the figures are never
            // only available as bar lengths.
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            if (grain == Grain.MONTH) "月度明细" else "年度明细",
                            style = MaterialTheme.typography.titleSmall,
                            color = Viz.colors.primaryInk,
                        )
                        Spacer(Modifier.height(10.dp))
                        PeriodTableHeader()
                        periods.asReversed().forEach { PeriodTableRow(it) }
                    }
                }
            }
        }

        if (selected.bySymbol.any { it.closedTrades > 0 }) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "各股已实现盈亏 · ${selected.currency}",
                            style = MaterialTheme.typography.titleSmall,
                            color = Viz.colors.primaryInk,
                        )
                        Spacer(Modifier.height(10.dp))
                        SymbolTableHeader()
                        selected.bySymbol
                            .filter { it.closedTrades > 0 }
                            .forEach { SymbolTableRow(it) }
                    }
                }
            }
        }

        if (selected.unmatchedSales.isNotEmpty()) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "有 ${selected.unmatchedSales.size} 笔卖出找不到对应买入",
                            style = MaterialTheme.typography.titleSmall,
                            color = Viz.colors.warning,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "这些卖出的持仓可能早于已导入的记录。它们的收益无法计算，" +
                                "所以没有计入上面的统计——不会当成零成本的纯利润。" +
                                "到「流水」页补录对应的买入记录后统计会自动更新。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Viz.colors.secondaryInk,
                        )
                        Spacer(Modifier.height(8.dp))
                        selected.unmatchedSales.take(5).forEach { sale ->
                            Text(
                                "${sale.symbol} · ${Format.quantity(sale.quantity)} 股 · " +
                                    Format.date(sale.sellDate),
                                style = MaterialTheme.typography.labelSmall,
                                color = Viz.colors.mutedInk,
                            )
                        }
                    }
                }
            }
        }

        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "关于这些数字",
                        style = MaterialTheme.typography.titleSmall,
                        color = Viz.colors.primaryInk,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "只统计已实现盈亏：买入后卖出才算，按先买先卖（FIFO）配对，" +
                            "盈亏归入卖出所在的月份。已经全部卖出的股票只剩这笔已实现结果，" +
                            "不再带成本，所以不会继续显示成亏损。\n\n" +
                            "总回报率 = 已实现盈亏 ÷ 已平仓部分的成本。仍在持仓的成本不算进分母，" +
                            "因为那部分还没有产生任何回报。\n\n" +
                            "年化回报率按资金实际占用时间加权：同样的收益，" +
                            "持有半年的年化约为持有一年的两倍；仓位大的权重也更高。\n\n" +
                            "持仓中的股票只显示成本和均价，因为 App 读的是成交邮件，" +
                            "没有实时报价，无法计算浮动盈亏。\n\n" +
                            "不同币种完全分开统计，不做汇率换算。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Viz.colors.secondaryInk,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

/**
 * A period's axis label. Monthly buckets spanning more than a year get the year prefixed, so
 * two different Januaries are not drawn with the same label.
 */
private fun periodLabel(period: PeriodPerformance, total: Int): String =
    if (period.month == null) {
        period.year.toString()
    } else if (total > 12) {
        "%02d/%02d".format(period.year % 100, period.month)
    } else {
        "%02d月".format(period.month)
    }

@Composable
private fun PeriodTableHeader() {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        TableCell("期间", 1.1f, Viz.colors.mutedInk)
        TableCell("成本", 1.2f, Viz.colors.mutedInk, end = true)
        TableCell("盈亏", 1.2f, Viz.colors.mutedInk, end = true)
        TableCell("回报", 0.9f, Viz.colors.mutedInk, end = true)
    }
}

@Composable
private fun PeriodTableRow(period: PeriodPerformance) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableCell(period.key, 1.1f, Viz.colors.primaryInk)
        TableCell(Format.compact(period.cost), 1.2f, Viz.colors.secondaryInk, end = true)
        TableCell(
            Format.signedMoney(period.profit),
            1.2f,
            Viz.signColor(period.profit),
            end = true,
        )
        TableCell(
            period.roi?.let { Format.percent(it) } ?: "—",
            0.9f,
            period.roi?.let { Viz.signColor(it) } ?: Viz.colors.mutedInk,
            end = true,
        )
    }
}

@Composable
private fun SymbolTableHeader() {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        TableCell("股票", 1.3f, Viz.colors.mutedInk)
        TableCell("成本", 1.1f, Viz.colors.mutedInk, end = true)
        TableCell("盈亏", 1.2f, Viz.colors.mutedInk, end = true)
        TableCell("回报", 0.9f, Viz.colors.mutedInk, end = true)
    }
}

@Composable
private fun SymbolTableRow(symbol: SymbolPerformance) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1.3f)) {
            Text(
                text = symbol.symbol,
                style = MaterialTheme.typography.labelSmall,
                color = Viz.colors.primaryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Says outright whether anything is still held, so a closed row is unambiguous.
            Text(
                text = if (symbol.isClosed) {
                    "已清仓"
                } else {
                    "持 " + Format.quantity(symbol.openQuantity) + " 股"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Viz.colors.mutedInk,
                maxLines = 1,
            )
        }
        TableCell(Format.compact(symbol.realizedCost), 1.1f, Viz.colors.secondaryInk, end = true)
        TableCell(
            Format.signedMoney(symbol.realizedProfit),
            1.2f,
            Viz.signColor(symbol.realizedProfit),
            end = true,
        )
        TableCell(
            symbol.realizedRoi?.let { Format.percent(it) } ?: "—",
            0.9f,
            symbol.realizedRoi?.let { Viz.signColor(it) } ?: Viz.colors.mutedInk,
            end = true,
        )
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    color: Color,
    end: Boolean = false,
) {
    Text(
        text = text,
        // Tabular figures so the numeric columns line up digit-for-digit.
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        modifier = Modifier.weight(weight),
    )
}
