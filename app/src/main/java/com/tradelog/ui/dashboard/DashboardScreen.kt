package com.tradelog.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tradelog.data.db.Side
import com.tradelog.data.db.Trade
import com.tradelog.data.perf.MonthlyTurnover
import com.tradelog.ui.CurrencySummaryRow
import com.tradelog.ui.DashboardState
import com.tradelog.ui.EmptyState
import com.tradelog.ui.Format
import com.tradelog.ui.FxState
import com.tradelog.ui.Panel
import com.tradelog.ui.SideChip
import com.tradelog.ui.theme.Viz
import java.util.Calendar

/**
 * The overview: where things stand, in as few lines as possible.
 *
 * One row per currency — profit, percentage, holdings — then this month's turnover and the
 * exchange-rate card, then anything needing attention, then the latest fills. Every deeper cut
 * (per-stock results, monthly chart, annualized return, holdings detail) lives on the returns
 * screen, so the two screens never restate each other.
 *
 * Profit has no date-range filter: it is a running total whose value does not depend on a
 * window. Turnover is the opposite — pure activity — so it gets exactly one window, the
 * current calendar month, converted to HKD at today's rate.
 */
@Composable
fun DashboardScreen(
    state: DashboardState,
    onOpenTrades: () -> Unit,
    onOpenReturns: () -> Unit,
    onRefreshFx: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.performance.isEmpty()) {
            item {
                EmptyState(
                    title = if (state.tradeCount == 0) "还没有交易记录" else "暂无可统计的数据",
                    body = "配置好邮箱后点右下角同步，或到「流水」页手动添加买入和卖出。",
                )
            }
        } else {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "总盈亏",
                                style = MaterialTheme.typography.titleSmall,
                                color = Viz.colors.primaryInk,
                            )
                            Text(
                                "详细统计",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = onOpenReturns),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "已实现盈亏，按币种分开计算，不做汇率换算。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Viz.colors.mutedInk,
                        )
                        Spacer(Modifier.height(12.dp))

                        state.performance.forEachIndexed { index, perf ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = Viz.colors.hairline,
                                )
                            }
                            CurrencySummaryRow(perf)
                        }
                    }
                }
            }
        }

        if (state.tradeCount > 0) {
            item { MonthlyTurnoverCard(state.monthly) }
        }

        item { FxRateCard(state.fx, onRefresh = onRefreshFx) }

        if (state.reviewCount > 0) {
            item {
                Panel(Modifier.fillMaxWidth().clickable(onClick = onOpenTrades)) {
                    Column {
                        Text(
                            "有 ${state.reviewCount} 笔待核对",
                            style = MaterialTheme.typography.titleSmall,
                            color = Viz.colors.warning,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "这些邮件缺少字段，解析不完整。点这里到「流水」页逐笔确认或补充。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Viz.colors.secondaryInk,
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "最近成交",
                    style = MaterialTheme.typography.titleMedium,
                    color = Viz.colors.primaryInk,
                )
                Text(
                    "查看全部",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onOpenTrades)
                        .padding(top = 2.dp),
                )
            }
        }

        if (state.recent.isEmpty()) {
            item {
                EmptyState(
                    title = "暂无成交记录",
                    body = "配置好邮箱后，点右下角同步按钮抓取邮件。",
                )
            }
        } else {
            items(state.recent, key = { it.id }) { trade ->
                RecentTradeRow(trade, onClick = onOpenTrades)
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

/**
 * This month's traded volume — buys plus sells, gross of fees, in HKD at the current rate.
 *
 * Shown even at zero: "nothing traded this month" is itself the answer the card exists to give.
 */
@Composable
private fun MonthlyTurnoverCard(monthly: MonthlyTurnover) {
    val month = remember { Calendar.getInstance().get(Calendar.MONTH) + 1 }
    Panel(Modifier.fillMaxWidth()) {
        Column {
            Text(
                "本月交易额",
                style = MaterialTheme.typography.titleSmall,
                color = Viz.colors.primaryInk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$month 月 · 买卖合计，非港币按当前汇率折算为 HKD。",
                style = MaterialTheme.typography.labelSmall,
                color = Viz.colors.mutedInk,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = Format.money(monthly.totalHkd, "HKD"),
                style = MaterialTheme.typography.headlineSmall,
                color = Viz.colors.primaryInk,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (monthly.tradeCount == 0) {
                    "本月暂无成交"
                } else {
                    "买入 " + Format.compact(monthly.buyHkd) +
                        " · 卖出 " + Format.compact(monthly.sellHkd) +
                        " · ${monthly.tradeCount} 笔"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Viz.colors.secondaryInk,
            )
            if (monthly.hasUnconverted) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "部分交易缺少汇率，未计入合计。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Viz.colors.warning,
                )
            }
        }
    }
}

/** The three pairs the card can show. The main line reads base-first: "1 USD = 7.09 CNY". */
private enum class FxPair(val label: String, val base: String, val quote: String) {
    CNY_USD("人民币/美元", "USD", "CNY"),
    CNY_HKD("人民币/港币", "CNY", "HKD"),
    USD_HKD("美元/港币", "USD", "HKD"),
}

/**
 * Exchange rates, switchable between the three currencies the ledger actually sees.
 *
 * Defaults to CNY/USD. Both directions are always shown, so whichever way the reader thinks
 * of "the rate" — 7.09 or 0.141 — it is on the card without a tap.
 */
@Composable
private fun FxRateCard(fx: FxState, onRefresh: () -> Unit) {
    var pair by remember { mutableStateOf(FxPair.CNY_USD) }
    val snapshot = fx.snapshot

    Panel(Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "汇率",
                    style = MaterialTheme.typography.titleSmall,
                    color = Viz.colors.primaryInk,
                )
                if (fx.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新汇率",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onRefresh),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FxPair.entries.forEach { entry ->
                    PairChip(
                        label = entry.label,
                        selected = entry == pair,
                        onClick = { pair = entry },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val forward = snapshot?.rate(pair.base, pair.quote)
            val backward = snapshot?.rate(pair.quote, pair.base)
            if (forward != null && backward != null) {
                Text(
                    text = "1 ${pair.base} = ${Format.rate(forward)} ${pair.quote}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    color = Viz.colors.primaryInk,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "1 ${pair.quote} = ${Format.rate(backward)} ${pair.base}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = Viz.colors.secondaryInk,
                    maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append("更新于 ").append(Format.relative(snapshot.fetchedAt))
                        append(" · 数据源每日更新")
                        if (fx.failed) append(" · 刷新失败，显示缓存")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (fx.failed) Viz.colors.warning else Viz.colors.mutedInk,
                )
            } else {
                Text(
                    text = if (fx.loading) "正在获取汇率…" else "暂无汇率数据，请检查网络后点右上角刷新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fx.failed && !fx.loading) Viz.colors.warning else Viz.colors.secondaryInk,
                )
            }
        }
    }
}

/** Selector chip for one currency pair: filled when active, hairline-quiet when not. */
@Composable
private fun PairChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        Viz.colors.plane
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else Viz.colors.secondaryInk,
        )
    }
}

@Composable
private fun RecentTradeRow(trade: Trade, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = trade.symbol + (trade.name?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                        color = Viz.colors.primaryInk,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${Format.date(trade.tradeDate)} · ${Format.quantity(trade.quantity)} 股 @ ${Format.price(trade.price)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Viz.colors.secondaryInk,
                        maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    SideChip(isBuy = trade.side == Side.BUY)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = Format.signedMoney(trade.netAmount, trade.currency),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (trade.side == Side.BUY) Viz.colors.buy else Viz.colors.sell,
                    )
                }
            }
            if (trade.needsReview) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "解析不完整，请核对",
                    style = MaterialTheme.typography.labelSmall,
                    color = Viz.colors.warning,
                )
            }
        }
    }
}
