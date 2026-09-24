package com.tradelog.data.perf

import com.tradelog.data.db.Side
import com.tradelog.data.db.Trade
import com.tradelog.data.fx.FxSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MonthlyTurnoverCalculatorTest {

    private val zone = TimeZone.getTimeZone("Asia/Hong_Kong")

    /** 7.8 HKD and 7.1 CNY per USD — round numbers so expectations stay readable. */
    private val fx = FxSnapshot(
        rates = mapOf("USD" to 1.0, "HKD" to 7.8, "CNY" to 7.1),
        fetchedAt = 0L,
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        val c = Calendar.getInstance(zone)
        c.clear()
        c.set(year, month - 1, day, hour, 0, 0)
        return c.timeInMillis
    }

    private val now = millis(2026, 9, 15)

    private fun trade(
        date: Long,
        side: Side,
        gross: Double,
        currency: String = "HKD",
    ) = Trade(
        messageKey = "test:${date}:${side}:${gross}:${currency}",
        tradeDate = date,
        symbol = "0005",
        name = null,
        market = null,
        side = side,
        quantity = 100.0,
        price = gross / 100.0,
        currency = currency,
        gross = gross,
        fees = 30.0,
        netAmount = if (side == Side.BUY) -(gross + 30.0) else gross - 30.0,
        orderRef = null,
        accountRef = null,
    )

    @Test
    fun `only trades inside the current calendar month count`() {
        val trades = listOf(
            trade(millis(2026, 9, 1, 0), Side.BUY, 1_000.0),
            trade(millis(2026, 9, 30, 23), Side.SELL, 2_000.0),
            trade(millis(2026, 8, 31, 23), Side.BUY, 5_000.0), // previous month
            trade(millis(2026, 10, 1, 0), Side.SELL, 5_000.0), // next month
            trade(millis(2025, 9, 15), Side.BUY, 5_000.0), // same month, wrong year
        )

        val result = MonthlyTurnoverCalculator.compute(trades, fx, zone, now)

        assertEquals(2, result.tradeCount)
        assertEquals(1_000.0, result.buyHkd, 1e-9)
        assertEquals(2_000.0, result.sellHkd, 1e-9)
        assertEquals(3_000.0, result.totalHkd, 1e-9)
        assertFalse(result.hasUnconverted)
    }

    @Test
    fun `non-HKD trades convert at the snapshot rate, gross of fees`() {
        val trades = listOf(
            trade(millis(2026, 9, 3), Side.BUY, 1_000.0, currency = "USD"),
            trade(millis(2026, 9, 4), Side.SELL, 710.0, currency = "CNY"),
            trade(millis(2026, 9, 5), Side.SELL, 500.0, currency = "HKD"),
        )

        val result = MonthlyTurnoverCalculator.compute(trades, fx, zone, now)

        // 1000 USD * 7.8 HKD/USD
        assertEquals(7_800.0, result.buyHkd, 1e-6)
        // 710 CNY -> 100 USD -> 780 HKD, plus 500 HKD as-is
        assertEquals(1_280.0, result.sellHkd, 1e-6)
        assertEquals(3, result.tradeCount)
        assertFalse(result.hasUnconverted)
    }

    @Test
    fun `missing rate skips the trade and flags it instead of guessing`() {
        val trades = listOf(
            trade(millis(2026, 9, 3), Side.BUY, 1_000.0, currency = "JPY"),
            trade(millis(2026, 9, 4), Side.BUY, 400.0, currency = "HKD"),
        )

        val result = MonthlyTurnoverCalculator.compute(trades, FxSnapshot(mapOf("USD" to 1.0, "HKD" to 7.8), 0L), zone, now)

        assertTrue(result.hasUnconverted)
        assertEquals(400.0, result.totalHkd, 1e-9)
        // The skipped trade is still counted as this month's activity.
        assertEquals(2, result.tradeCount)
    }

    @Test
    fun `no snapshot at all - HKD still sums, everything else is flagged`() {
        val trades = listOf(
            trade(millis(2026, 9, 3), Side.BUY, 1_000.0, currency = "USD"),
            trade(millis(2026, 9, 4), Side.SELL, 400.0, currency = "HKD"),
        )

        val result = MonthlyTurnoverCalculator.compute(trades, fx = null, zone = zone, now = now)

        assertTrue(result.hasUnconverted)
        assertEquals(0.0, result.buyHkd, 1e-9)
        assertEquals(400.0, result.sellHkd, 1e-9)
    }

    @Test
    fun `empty month is all zeros`() {
        val result = MonthlyTurnoverCalculator.compute(emptyList(), fx, zone, now)
        assertEquals(0, result.tradeCount)
        assertEquals(0.0, result.totalHkd, 1e-9)
        assertFalse(result.hasUnconverted)
    }

    @Test
    fun `snapshot cross rates`() {
        assertEquals(7.8, fx.rate("USD", "HKD")!!, 1e-9)
        assertEquals(7.1 / 7.8, fx.rate("HKD", "CNY")!!, 1e-9)
        assertEquals(7.1, fx.rate("USD", "CNY")!!, 1e-9)
        assertEquals(1.0 / 7.1, fx.rate("CNY", "USD")!!, 1e-9)
        assertNull(fx.rate("JPY", "HKD"))
        assertNull(fx.rate("HKD", "JPY"))
    }
}
