package com.tradelog.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tradelog.data.db.Side
import com.tradelog.data.db.Trade
import com.tradelog.ui.CurrencySummaryRow
import com.tradelog.ui.DashboardState
import com.tradelog.ui.EmptyState
import com.tradelog.ui.Format
import com.tradelog.ui.Panel
import com.tradelog.ui.SideChip
import com.tradelog.ui.theme.Viz

/**
 * The overview: where things stand, in as few lines as possible.
 *
 * One row per currency — profit, percentage, holdings — then anything needing attention, then
 * the latest fills. Every deeper cut (per-stock results, monthly chart, annualized return,
 * holdings detail) lives on the returns screen, so the two screens never restate each other.
 *
 * There is no date-range filter: total profit is a running total whose value does not depend on
 * a window, and turnover — how much was traded lately — is activity, not result.
 */
@Composable
fun DashboardScreen(
    state: DashboardState,
    onOpenTrades: () -> Unit,
    onOpenReturns: () -> Unit,
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
