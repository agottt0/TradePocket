package com.tradelog.ui.trades

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tradelog.data.db.Side
import com.tradelog.data.db.Trade
import com.tradelog.ui.EmptyState
import com.tradelog.ui.Format
import com.tradelog.ui.Panel
import com.tradelog.ui.SideChip
import com.tradelog.ui.theme.Viz

private enum class TradeFilter(val label: String) {
    ALL("全部"),
    BUY("买入"),
    SELL("卖出"),
    REVIEW("待核对"),
}

@Composable
fun TradesScreen(
    trades: List<Trade>,
    onMarkReviewed: (Trade) -> Unit,
    onDelete: (Trade) -> Unit,
    onAddManual: (
        tradeDate: Long,
        symbol: String,
        name: String?,
        side: Side,
        quantity: Double,
        price: Double,
        currency: String,
        fees: Double,
    ) -> Unit,
    onEdit: (
        original: Trade,
        tradeDate: Long,
        symbol: String,
        name: String?,
        side: Side,
        quantity: Double,
        price: Double,
        currency: String,
        fees: Double,
    ) -> Unit,
    showManualSheet: Boolean,
    onManualSheetDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(TradeFilter.ALL) }
    var detail by remember { mutableStateOf<Trade?>(null) }
    var editing by remember { mutableStateOf<Trade?>(null) }

    val visible = when (filter) {
        TradeFilter.ALL -> trades
        TradeFilter.BUY -> trades.filter { it.side == Side.BUY }
        TradeFilter.SELL -> trades.filter { it.side == Side.SELL }
        TradeFilter.REVIEW -> trades.filter { it.needsReview }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TradeFilter.entries.forEach { f ->
                    val count = when (f) {
                        TradeFilter.ALL -> trades.size
                        TradeFilter.BUY -> trades.count { it.side == Side.BUY }
                        TradeFilter.SELL -> trades.count { it.side == Side.SELL }
                        TradeFilter.REVIEW -> trades.count { it.needsReview }
                    }
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = {
                            Text("${f.label} $count", style = MaterialTheme.typography.labelMedium)
                        },
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    title = "没有匹配的记录",
                    body = if (trades.isEmpty()) "同步邮件后，成交记录会出现在这里。" else "换一个筛选条件试试。",
                )
            }
        } else {
            items(visible, key = { it.id }) { trade ->
                TradeCard(trade = trade, onClick = { detail = trade })
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    if (showManualSheet) {
        ManualTradeSheet(
            onDismiss = onManualSheetDismiss,
            onSubmit = onAddManual,
        )
    }

    detail?.let { trade ->
        TradeDetailDialog(
            trade = trade,
            onDismiss = { detail = null },
            onEdit = {
                detail = null
                editing = trade
            },
            onMarkReviewed = {
                onMarkReviewed(trade)
                detail = null
            },
            onDelete = {
                onDelete(trade)
                detail = null
            },
        )
    }

    // The same form as hand entry, pre-filled — see ManualTradeSheet for why they are shared.
    editing?.let { original ->
        ManualTradeSheet(
            existing = original,
            onDismiss = { editing = null },
            onSubmit = { date, symbol, name, side, qty, price, currency, fees ->
                onEdit(original, date, symbol, name, side, qty, price, currency, fees)
            },
        )
    }
}

@Composable
private fun TradeCard(trade: Trade, onClick: () -> Unit) {
    Panel(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = trade.symbol,
                        style = MaterialTheme.typography.titleSmall,
                        color = Viz.colors.primaryInk,
                    )
                    trade.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Viz.colors.secondaryInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                SideChip(isBuy = trade.side == Side.BUY)
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MiniField("数量", Format.quantity(trade.quantity), Modifier.weight(1f))
                MiniField("成交价", Format.price(trade.price), Modifier.weight(1f))
                MiniField("费用", Format.money(trade.fees), Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = Format.date(trade.tradeDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = Viz.colors.mutedInk,
                )
                Text(
                    text = Format.signedMoney(trade.netAmount, trade.currency),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (trade.side == Side.BUY) Viz.colors.buy else Viz.colors.sell,
                )
            }

            if (trade.needsReview) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "解析不完整，点开核对",
                    style = MaterialTheme.typography.labelSmall,
                    color = Viz.colors.warning,
                )
            }
        }
    }
}

@Composable
private fun MiniField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Viz.colors.mutedInk)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = Viz.colors.primaryInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TradeDetailDialog(
    trade: Trade,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onMarkReviewed: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = trade.symbol + (trade.name?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                DetailRow("方向", if (trade.side == Side.BUY) "买入" else "卖出")
                DetailRow("成交时间", Format.stamp(trade.tradeDate))
                DetailRow("数量", Format.quantity(trade.quantity))
                DetailRow("成交价", Format.price(trade.price))
                DetailRow("成交金额", Format.money(trade.gross, trade.currency))
                DetailRow("费用合计", Format.money(trade.fees, trade.currency))
                DetailRow("净现金流", Format.signedMoney(trade.netAmount, trade.currency))
                trade.market?.let { DetailRow("市场", it) }
                trade.orderRef?.let { DetailRow("交易编号", it) }
                trade.accountRef?.let { DetailRow("账户", it) }
                DetailRow("导入时间", Format.stamp(trade.importedAt))
                trade.rawSubject?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "邮件标题：$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = Viz.colors.mutedInk,
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onEdit) { Text("编辑") }
                // Marking reviewed is only offered when there is something to review; editing
                // already clears the flag, so the two never compete for the same tap.
                if (trade.needsReview) {
                    TextButton(onClick = onMarkReviewed) { Text("已核对") }
                } else {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这笔记录？") },
            text = {
                Text(
                    "只会删除本地这一条记录，邮箱里的邮件不会动。" +
                        "如果之后做全量重扫，这笔会被重新导入。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Viz.colors.secondaryInk)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Viz.colors.primaryInk)
    }
}
