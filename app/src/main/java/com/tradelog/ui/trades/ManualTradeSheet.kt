package com.tradelog.ui.trades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tradelog.data.db.Side
import com.tradelog.data.db.Trade
import com.tradelog.ui.Format
import com.tradelog.ui.theme.Viz
import java.util.Calendar

/**
 * One form for both jobs: entering a trade the mailbox did not deliver, and correcting one it
 * delivered wrong.
 *
 * [existing] switches it into edit mode — every field is pre-filled from that trade and the
 * submit button updates it in place instead of inserting a row. Sharing the form is what keeps
 * the two paths honest: the same validation and the same live net-cash preview apply, so an
 * edit cannot produce a figure the entry path would have rejected.
 *
 * Kept to a single sheet: side, symbol, quantity, price, currency and date. Optional fields
 * (name, fees) sit behind a disclosure so the common path stays short — but the disclosure
 * starts open when editing a trade that already carries either, so nothing already recorded is
 * hidden from someone about to change it.
 *
 * Validation is deliberately strict about numbers — a mistyped price silently poisons every
 * total downstream — but permissive about everything else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTradeSheet(
    onDismiss: () -> Unit,
    onSubmit: (
        tradeDate: Long,
        symbol: String,
        name: String?,
        side: Side,
        quantity: Double,
        price: Double,
        currency: String,
        fees: Double,
    ) -> Unit,
    existing: Trade? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val editing = existing != null

    var side by remember { mutableStateOf(existing?.side ?: Side.BUY) }
    var symbol by remember { mutableStateOf(existing?.symbol ?: "") }
    var quantity by remember { mutableStateOf(existing?.quantity?.let { plain(it) } ?: "") }
    var price by remember { mutableStateOf(existing?.price?.let { plain(it) } ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: "USD") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    // Zero fees are the norm on an execution notice, so an empty field reads better than "0".
    var fees by remember { mutableStateOf(existing?.fees?.takeIf { it > 0 }?.let { plain(it) } ?: "") }
    // Open by default when there is already something in there to see.
    var showOptional by remember {
        mutableStateOf(existing != null && (!existing.name.isNullOrBlank() || existing.fees > 0))
    }

    // A date is enough: buckets are monthly and yearly, and holding periods are in days.
    val seed = remember {
        Calendar.getInstance().apply { existing?.let { timeInMillis = it.tradeDate } }
    }
    var year by remember { mutableStateOf(seed.get(Calendar.YEAR).toString()) }
    var month by remember { mutableStateOf((seed.get(Calendar.MONTH) + 1).toString().padStart(2, '0')) }
    var day by remember { mutableStateOf(seed.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')) }

    val qtyValue = quantity.trim().toDoubleOrNull()
    val priceValue = price.trim().toDoubleOrNull()
    val feeValue = fees.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() }
    // Editing something else about a trade must not move its timestamp: toMillis normalises to
    // midday, which would otherwise silently shift an imported fill's recorded time.
    val typedMillis = toMillis(year, month, day)
    val dateMillis = if (existing != null && typedMillis != null &&
        sameCalendarDay(existing.tradeDate, typedMillis)
    ) {
        existing.tradeDate
    } else {
        typedMillis
    }

    val symbolError = symbol.isNotBlank() && symbol.trim().length > 12
    val qtyError = quantity.isNotBlank() && (qtyValue == null || qtyValue <= 0)
    val priceError = price.isNotBlank() && (priceValue == null || priceValue <= 0)
    val feeError = fees.isNotBlank() && (feeValue == null || feeValue < 0)
    val dateError = typedMillis == null

    val canSubmit = symbol.isNotBlank() && !symbolError &&
        qtyValue != null && qtyValue > 0 &&
        priceValue != null && priceValue > 0 &&
        feeValue != null && feeValue >= 0 &&
        currency.trim().length == 3 &&
        dateMillis != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                if (editing) "编辑交易" else "手动添加交易",
                style = MaterialTheme.typography.titleMedium,
                color = Viz.colors.primaryInk,
            )
            if (editing) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "改动只影响本地这一条记录。统计会立即重算；" +
                        "如果之后做全量重扫，同一封邮件不会覆盖你的修改。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Viz.colors.mutedInk,
                )
            }
            Spacer(Modifier.height(14.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(Side.BUY to "买入", Side.SELL to "卖出").forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = side == value,
                        onClick = { side = value },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = if (value == Side.BUY) {
                                Viz.colors.buy.copy(alpha = 0.16f)
                            } else {
                                Viz.colors.sell.copy(alpha = 0.16f)
                            },
                            activeContentColor = Viz.colors.primaryInk,
                        ),
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.trimStart() },
                    label = { Text("代码") },
                    placeholder = { Text("INTC") },
                    singleLine = true,
                    isError = symbolError,
                    modifier = Modifier.weight(1.4f),
                )
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.uppercase().filter(Char::isLetter).take(3) },
                    label = { Text("币种") },
                    singleLine = true,
                    isError = currency.trim().length != 3,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filterDecimal() },
                    label = { Text("数量") },
                    singleLine = true,
                    isError = qtyError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filterDecimal() },
                    label = { Text("成交价") },
                    singleLine = true,
                    isError = priceError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))

            Text("成交日期", style = MaterialTheme.typography.labelMedium, color = Viz.colors.secondaryInk)
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberBox(year, { year = it.filterDigits(4) }, "年", dateError, Modifier.weight(1.4f))
                NumberBox(month, { month = it.filterDigits(2) }, "月", dateError, Modifier.weight(1f))
                NumberBox(day, { day = it.filterDigits(2) }, "日", dateError, Modifier.weight(1f))
            }

            if (dateError) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "日期无效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(10.dp))

            TextButton(onClick = { showOptional = !showOptional }) {
                Text(if (showOptional) "收起选填项" else "名称、费用（选填）")
            }

            if (showOptional) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("股票名称") },
                    placeholder = { Text("INTEL CORPORATION") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = fees,
                    onValueChange = { fees = it.filterDecimal() },
                    label = { Text("费用合计") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    isError = feeError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Live preview of the figure that will actually enter the books, so a
            // mistyped quantity or price is visible before it is committed.
            if (qtyValue != null && priceValue != null && feeValue != null) {
                val gross = qtyValue * priceValue
                val net = if (side == Side.BUY) -(gross + feeValue) else gross - feeValue
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                ) {
                    Column {
                        Text(
                            "净现金流",
                            style = MaterialTheme.typography.labelMedium,
                            color = Viz.colors.secondaryInk,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            Format.signedMoney(net, currency.trim().uppercase()),
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (side == Side.BUY) Viz.colors.buy else Viz.colors.sell,
                        )
                        Text(
                            "成交额 " + Format.money(gross),
                            style = MaterialTheme.typography.labelSmall,
                            color = Viz.colors.mutedInk,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onSubmit(
                        dateMillis!!,
                        symbol,
                        name.ifBlank { null },
                        side,
                        qtyValue!!,
                        priceValue!!,
                        currency,
                        feeValue!!,
                    )
                    onDismiss()
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (editing) "保存修改" else "保存") }
        }
    }
}

@Composable
private fun NumberBox(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/**
 * A number as the form would have it typed: no thousands separators, no trailing zeros, and
 * never scientific notation — all three would fail the field's own decimal filter and come back
 * as a different value than was stored.
 */
private fun plain(value: Double): String =
    // valueOf, not the Double constructor: the latter expands to the exact binary value, so
    // 87.69 would seed the field as 87.68999999999999772626324556767940521240234375.
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

/** Digits only, capped at [max] characters. */
private fun String.filterDigits(max: Int): String = filter(Char::isDigit).take(max)

/**
 * Keeps digits and at most one decimal point, so the field cannot hold something
 * `toDoubleOrNull` would reject.
 */
private fun String.filterDecimal(): String {
    val cleaned = filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
    val firstDot = cleaned.indexOf('.')
    if (firstDot == -1) return cleaned.take(15)
    val head = cleaned.substring(0, firstDot + 1)
    val tail = cleaned.substring(firstDot + 1).filter(Char::isDigit)
    return (head + tail).take(15)
}

/** True when both instants land on the same calendar day in the device's zone. */
private fun sameCalendarDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

/**
 * Builds an epoch-millis timestamp for midday on the given date, in the device's zone, or null
 * when the parts do not form a real date.
 *
 * Midday rather than midnight: a trade stored at 00:00 shifts to the previous calendar day for
 * anyone whose zone moves behind the one it was entered in, which would move the trade into the
 * wrong month at a month boundary. Midday leaves ~12 hours of slack either way.
 *
 * Lenient mode is off so 31 February is rejected rather than silently rolled into March.
 */
private fun toMillis(year: String, month: String, day: String): Long? {
    val y = year.toIntOrNull() ?: return null
    val mo = month.toIntOrNull() ?: return null
    val d = day.toIntOrNull() ?: return null
    if (y < 1970 || y > 2200 || mo !in 1..12 || d !in 1..31) return null
    return try {
        Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(y, mo - 1, d, 12, 0, 0)
        }.timeInMillis
    } catch (_: IllegalArgumentException) {
        null
    }
}
