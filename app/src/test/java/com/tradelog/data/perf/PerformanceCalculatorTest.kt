package com.tradelog.data.perf

import com.tradelog.data.db.Side
import com.tradelog.data.db.Trade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.Calendar
import java.util.TimeZone
import org.junit.Test

class PerformanceCalculatorTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Hong_Kong")

    private var nextId = 1L

    private fun at(year: Int, month: Int, day: Int, hour: Int = 10, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun trade(
        symbol: String,
        side: Side,
        quantity: Double,
        price: Double,
        date: Long,
        fees: Double = 0.0,
        currency: String = "USD",
    ): Trade {
        val gross = quantity * price
        return Trade(
            id = nextId++,
            messageKey = "test-" + nextId,
            tradeDate = date,
            symbol = symbol,
            name = symbol,
            market = null,
            side = side,
            quantity = quantity,
            price = price,
            currency = currency,
            gross = gross,
            fees = fees,
            netAmount = if (side == Side.BUY) -(gross + fees) else gross - fees,
            orderRef = null,
            accountRef = null,
        )
    }

    private fun single(trades: List<Trade>): CurrencyPerformance =
        PerformanceCalculator.compute(trades, zone).single()

    // ---- Basic round trips --------------------------------------------------------------

    @Test
    fun `a simple round trip realizes the price difference`() {
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 10)),
                trade("INTC", Side.SELL, 10.0, 120.0, at(2026, 3, 10)),
            ),
        )

        assertEquals(1, perf.realized.size)
        assertEquals(1000.0, perf.totalCost, 0.001)
        assertEquals(1200.0, perf.totalProceeds, 0.001)
        assertEquals(200.0, perf.totalProfit, 0.001)
        assertEquals(0.20, perf.totalRoi!!, 0.0001)
        assertTrue(perf.openLots.isEmpty())
        assertTrue(perf.unmatchedSales.isEmpty())
    }

    @Test
    fun `fees raise the cost and reduce the proceeds`() {
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 10), fees = 8.0),
                trade("INTC", Side.SELL, 10.0, 120.0, at(2026, 3, 10), fees = 9.0),
            ),
        )

        assertEquals(1008.0, perf.totalCost, 0.001)
        assertEquals(1191.0, perf.totalProceeds, 0.001)
        // 200 gross profit, less 17 of total charges.
        assertEquals(183.0, perf.totalProfit, 0.001)
    }

    @Test
    fun `an unsold buy stays an open lot at cost and earns nothing`() {
        val perf = single(listOf(trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 10), fees = 5.0)))

        assertTrue(perf.realized.isEmpty())
        assertNull(perf.totalRoi)
        assertEquals(1, perf.openLots.size)
        assertEquals(1005.0, perf.openCost, 0.001)
        assertEquals(10.0, perf.bySymbol.single { it.symbol == "INTC" }.openQuantity, 0.001)
        // Cost per share includes the buy fee.
        assertEquals(100.5, perf.openLots.single().averagePrice, 0.001)
    }

    // ---- FIFO ---------------------------------------------------------------------------

    @Test
    fun `a sale consumes the oldest shares first`() {
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 10)),
                trade("INTC", Side.BUY, 10.0, 200.0, at(2026, 2, 10)),
                // Sell 10: FIFO takes the 100-cost shares, not the 200-cost ones.
                trade("INTC", Side.SELL, 10.0, 150.0, at(2026, 3, 10)),
            ),
        )

        assertEquals(1, perf.realized.size)
        assertEquals(1000.0, perf.realized.single().cost, 0.001)
        assertEquals(500.0, perf.totalProfit, 0.001)
        // The newer, pricier lot is still held.
        assertEquals(2000.0, perf.openCost, 0.001)
    }

    @Test
    fun `one sale spanning two buy lots splits into two closed lots`() {
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 5.0, 100.0, at(2026, 1, 10)),
                trade("INTC", Side.BUY, 5.0, 200.0, at(2026, 2, 10)),
                trade("INTC", Side.SELL, 10.0, 300.0, at(2026, 3, 10)),
            ),
        )

        assertEquals(2, perf.realized.size)
        assertEquals(1500.0, perf.totalCost, 0.001)
        assertEquals(3000.0, perf.totalProceeds, 0.001)
        assertEquals(1500.0, perf.totalProfit, 0.001)
        assertTrue(perf.openLots.isEmpty())
    }

    @Test
    fun `a partial sale leaves the remainder open`() {
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 10)),
                trade("INTC", Side.SELL, 4.0, 150.0, at(2026, 3, 10)),
            ),
        )

        assertEquals(400.0, perf.totalCost, 0.001)
        assertEquals(200.0, perf.totalProfit, 0.001)
        assertEquals(600.0, perf.openCost, 0.001)
        assertEquals(6.0, perf.bySymbol.single { it.symbol == "INTC" }.openQuantity, 0.001)
    }

    @Test
    fun `each symbol is matched independently`() {
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 10)),
                trade("AAPL", Side.BUY, 10.0, 200.0, at(2026, 1, 11)),
                // Selling AAPL must not consume INTC shares.
                trade("AAPL", Side.SELL, 10.0, 250.0, at(2026, 3, 10)),
            ),
        )

        assertEquals(1, perf.realized.size)
        assertEquals("AAPL", perf.realized.single().symbol)
        assertEquals(500.0, perf.totalProfit, 0.001)
        assertEquals(1000.0, perf.openCost, 0.001)
    }

    @Test
    fun `a same-day buy then sell matches rather than looking unbacked`() {
        val sameInstant = at(2026, 5, 20, 14, 30)
        val perf = single(
            listOf(
                trade("INTC", Side.SELL, 10.0, 110.0, sameInstant),
                trade("INTC", Side.BUY, 10.0, 100.0, sameInstant),
            ),
        )

        assertTrue("day trade should match: ${perf.unmatchedSales}", perf.unmatchedSales.isEmpty())
        assertEquals(100.0, perf.totalProfit, 0.001)
    }

    @Test
    fun `a sale with no recorded purchase is reported, not counted as pure profit`() {
        val perf = single(listOf(trade("INTC", Side.SELL, 10.0, 150.0, at(2026, 3, 10))))

        assertTrue(perf.realized.isEmpty())
        assertEquals(1, perf.unmatchedSales.size)
        assertEquals(10.0, perf.unmatchedSales.single().quantity, 0.001)
        // Crucially: not treated as a 1500 gain on zero cost.
        assertEquals(0.0, perf.totalProfit, 0.001)
        assertNull(perf.totalRoi)
    }

    @Test
    fun `fractional shares match correctly`() {
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 0.5, 100.0, at(2026, 1, 10)),
                trade("INTC", Side.SELL, 0.5, 120.0, at(2026, 2, 10)),
            ),
        )

        assertEquals(10.0, perf.totalProfit, 0.001)
        assertTrue("no residual lot expected: ${perf.openLots}", perf.openLots.isEmpty())
    }

    // ---- Currencies ---------------------------------------------------------------------

    @Test
    fun `currencies are kept separate and never summed`() {
        val all = PerformanceCalculator.compute(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 10), currency = "USD"),
                trade("INTC", Side.SELL, 10.0, 120.0, at(2026, 2, 10), currency = "USD"),
                trade("0005", Side.BUY, 100.0, 60.0, at(2026, 1, 10), currency = "HKD"),
                trade("0005", Side.SELL, 100.0, 55.0, at(2026, 2, 10), currency = "HKD"),
            ),
            zone,
        )

        assertEquals(2, all.size)
        val usd = all.single { it.currency == "USD" }
        val hkd = all.single { it.currency == "HKD" }
        assertEquals(200.0, usd.totalProfit, 0.001)
        assertEquals(-500.0, hkd.totalProfit, 0.001)
    }

    // ---- Per-symbol rollup: the sold-out case -------------------------------------------

    @Test
    fun `a stock sold out entirely keeps its profit and carries no remaining cost`() {
        // The reported bug: after selling everything, the position must not still read as a
        // loss. It closed at a profit, holds nothing, and has no leftover cost.
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 4.0, 87.69, at(2026, 1, 10)),
                trade("INTC", Side.SELL, 4.0, 101.38, at(2026, 2, 10)),
            ),
        )

        val intc = perf.bySymbol.single()
        assertTrue("selling everything must close the position", intc.isClosed)
        assertEquals(0.0, intc.openQuantity, 1e-9)
        assertEquals(0.0, intc.openCost, 1e-9)
        assertEquals(54.76, intc.realizedProfit, 0.001)
        assertTrue(intc.realizedProfit > 0)
        // And nothing lingers in the currency-level open figures either.
        assertEquals(0.0, perf.openCost, 1e-9)
        assertTrue(perf.openBySymbol.isEmpty())
        assertEquals(1, perf.closedBySymbol.size)
    }

    @Test
    fun `a partially sold stock reports realized profit and the rest as still held`() {
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 10)),
                trade("INTC", Side.SELL, 4.0, 150.0, at(2026, 2, 10)),
            ),
        )

        val intc = perf.bySymbol.single()
        assertFalse("6 shares are still held", intc.isClosed)
        assertEquals(6.0, intc.openQuantity, 0.001)
        assertEquals(600.0, intc.openCost, 0.001)
        // Realized cost covers only the 4 shares that were sold.
        assertEquals(400.0, intc.realizedCost, 0.001)
        assertEquals(200.0, intc.realizedProfit, 0.001)
        assertEquals(0.50, intc.realizedRoi!!, 0.0001)
        assertEquals(100.0, intc.averageOpenPrice!!, 0.001)
    }

    @Test
    fun `a stock only ever bought has no realized figures at all`() {
        val perf = single(listOf(trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 10))))

        val intc = perf.bySymbol.single()
        assertEquals(0.0, intc.realizedProfit, 1e-9)
        // Null, not zero: nothing was sold, so there is no return to report.
        assertNull(intc.realizedRoi)
        assertEquals(0, intc.closedTrades)
        assertFalse(intc.isClosed)
    }

    @Test
    fun `each stock is rolled up separately within one currency`() {
        val perf = single(
            listOf(
                trade("WIN", Side.BUY, 10.0, 100.0, at(2026, 1, 10)),
                trade("WIN", Side.SELL, 10.0, 130.0, at(2026, 2, 10)),
                trade("LOSE", Side.BUY, 10.0, 100.0, at(2026, 1, 10)),
                trade("LOSE", Side.SELL, 10.0, 90.0, at(2026, 2, 10)),
                trade("HELD", Side.BUY, 5.0, 50.0, at(2026, 1, 10)),
            ),
        )

        assertEquals(3, perf.bySymbol.size)
        val win = perf.bySymbol.single { it.symbol == "WIN" }
        val lose = perf.bySymbol.single { it.symbol == "LOSE" }
        val held = perf.bySymbol.single { it.symbol == "HELD" }

        assertEquals(300.0, win.realizedProfit, 0.001)
        assertEquals(-100.0, lose.realizedProfit, 0.001)
        assertEquals(0.0, held.realizedProfit, 1e-9)

        // The currency total is the sum of the closed results only.
        assertEquals(200.0, perf.totalProfit, 0.001)
        // HELD is the only open position, so it alone contributes open cost.
        assertEquals(250.0, perf.openCost, 0.001)
        assertEquals(listOf("HELD"), perf.openBySymbol.map { it.symbol })
    }

    @Test
    fun `a symbol traded in two currencies is rolled up under each separately`() {
        val all = PerformanceCalculator.compute(
            listOf(
                trade("X", Side.BUY, 10.0, 100.0, at(2026, 1, 10), currency = "USD"),
                trade("X", Side.SELL, 10.0, 120.0, at(2026, 2, 10), currency = "USD"),
                trade("X", Side.BUY, 10.0, 100.0, at(2026, 1, 10), currency = "HKD"),
            ),
            zone,
        )

        val usd = all.single { it.currency == "USD" }
        val hkd = all.single { it.currency == "HKD" }
        // Closed in USD, still held in HKD: neither leaks into the other.
        assertTrue(usd.bySymbol.single().isClosed)
        assertEquals(200.0, usd.totalProfit, 0.001)
        assertFalse(hkd.bySymbol.single().isClosed)
        assertEquals(0.0, hkd.totalProfit, 1e-9)
        assertEquals(1000.0, hkd.openCost, 0.001)
    }

    // ---- Currency-level totals ----------------------------------------------------------

    @Test
    fun `no closed positions means no return figures at all`() {
        val perf = single(listOf(trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 10))))

        assertNull(perf.totalRoi)
        assertNull(perf.winRate)
    }

    @Test
    fun `an empty ledger produces no currency rows`() {
        assertTrue(PerformanceCalculator.compute(emptyList(), zone).isEmpty())
    }

    @Test
    fun `a loss reports a negative return`() {
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, at(2025, 1, 1)),
                trade("INTC", Side.SELL, 10.0, 80.0, at(2026, 1, 1)),
            ),
        )

        assertEquals(-200.0, perf.totalProfit, 0.001)
        assertEquals(-0.20, perf.totalRoi!!, 0.0001)
        assertEquals(0, perf.winCount)
        assertEquals(0.0, perf.winRate!!, 0.0001)
    }

    @Test
    fun `the return rate divides by closed cost only, not by cost still held`() {
        // 10 bought at 100; 5 sold at 150. The 500 of cost still held must stay out of the
        // denominator, or a real 50% gain would read as 25%.
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 10)),
                trade("INTC", Side.SELL, 5.0, 150.0, at(2026, 2, 10)),
            ),
        )

        assertEquals(250.0, perf.totalProfit, 0.001)
        assertEquals(0.50, perf.totalRoi!!, 0.0001)
    }

    @Test
    fun `win and loss counts follow the closed round trips`() {
        val perf = single(
            listOf(
                trade("A", Side.BUY, 10.0, 10.0, at(2026, 1, 5)),
                trade("A", Side.SELL, 10.0, 12.0, at(2026, 2, 5)),
                trade("B", Side.BUY, 10.0, 10.0, at(2026, 1, 5)),
                trade("B", Side.SELL, 10.0, 9.0, at(2026, 2, 5)),
            ),
        )

        assertEquals(1, perf.winCount)
        assertEquals(1, perf.lossCount)
        assertEquals(0.5, perf.winRate!!, 0.0001)
        assertEquals(10.0, perf.totalProfit, 0.001)
    }

    // ---- Period buckets -----------------------------------------------------------------

    @Test
    fun `profit lands in the month the position closed`() {
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 15)),
                trade("INTC", Side.SELL, 10.0, 120.0, at(2026, 3, 20)),
            ),
        )

        assertEquals(1, perf.byMonth.size)
        // Bought in January, sold in March: the gain belongs to March.
        assertEquals("2026-03", perf.byMonth.single().key)
        assertEquals(200.0, perf.byMonth.single().profit, 0.001)
    }

    @Test
    fun `months and years aggregate separately and in order`() {
        val perf = single(
            listOf(
                trade("A", Side.BUY, 10.0, 10.0, at(2025, 1, 5)),
                trade("A", Side.SELL, 10.0, 12.0, at(2025, 6, 5)),
                trade("B", Side.BUY, 10.0, 10.0, at(2026, 1, 5)),
                trade("B", Side.SELL, 10.0, 9.0, at(2026, 2, 5)),
                trade("C", Side.BUY, 10.0, 10.0, at(2026, 1, 5)),
                trade("C", Side.SELL, 10.0, 15.0, at(2026, 2, 20)),
            ),
        )

        assertEquals(listOf("2025-06", "2026-02"), perf.byMonth.map { it.key })
        // Both February closes land in one bucket: -10 and +50.
        assertEquals(40.0, perf.byMonth.last().profit, 0.001)
        assertEquals(2, perf.byMonth.last().closedTrades)

        assertEquals(listOf("2025", "2026"), perf.byYear.map { it.key })
        assertEquals(20.0, perf.byYear.first().profit, 0.001)
        assertEquals(40.0, perf.byYear.last().profit, 0.001)
    }

    // ---- Annualization ------------------------------------------------------------------

    @Test
    fun `a 10 percent gain over exactly one year annualizes to 10 percent`() {
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, at(2025, 1, 1)),
                trade("INTC", Side.SELL, 10.0, 110.0, at(2026, 1, 1)),
            ),
        )

        assertEquals(0.10, perf.totalRoi!!, 0.0001)
        // 365 days held, so the annualized rate equals the raw return.
        assertEquals(0.10, perf.annualizedReturn!!, 0.005)
        assertEquals(365.0, perf.averageHoldingDays!!, 1.0)
    }

    @Test
    fun `the same gain earned in half the time annualizes to roughly double`() {
        val fast = single(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, at(2025, 1, 1)),
                trade("INTC", Side.SELL, 10.0, 110.0, at(2025, 7, 2)),
            ),
        )

        // Same 10% profit, held ~half a year, so ~20% a year.
        assertEquals(0.10, fast.totalRoi!!, 0.0001)
        assertEquals(0.20, fast.annualizedReturn!!, 0.01)
    }

    @Test
    fun `annualization weights a bigger position more heavily`() {
        // A large winner held a year, and a tiny loser held a year.
        val perf = single(
            listOf(
                trade("BIG", Side.BUY, 100.0, 100.0, at(2025, 1, 1)),
                trade("BIG", Side.SELL, 100.0, 120.0, at(2026, 1, 1)),
                trade("SMALL", Side.BUY, 1.0, 100.0, at(2025, 1, 1)),
                trade("SMALL", Side.SELL, 1.0, 50.0, at(2026, 1, 1)),
            ),
        )

        // Profit 2000 - 50 = 1950 on 10100 of capital, all held one year.
        assertEquals(1950.0, perf.totalProfit, 0.001)
        assertEquals(1950.0 / 10100.0, perf.annualizedReturn!!, 0.005)
    }

    @Test
    fun `a day trade has no holding period to annualize`() {
        val sameInstant = at(2026, 5, 20, 14, 30)
        val perf = single(
            listOf(
                trade("INTC", Side.BUY, 10.0, 100.0, sameInstant),
                trade("INTC", Side.SELL, 10.0, 110.0, sameInstant),
            ),
        )

        assertEquals(100.0, perf.totalProfit, 0.001)
        // A real return over zero elapsed time would annualize to nonsense.
        assertNull(perf.annualizedReturn)
    }

    @Test
    fun `no closed position means no annualized figure`() {
        val perf = single(listOf(trade("INTC", Side.BUY, 10.0, 100.0, at(2026, 1, 10))))

        assertNull(perf.annualizedReturn)
        assertNull(perf.averageHoldingDays)
        assertTrue(perf.byMonth.isEmpty())
        assertTrue(perf.byYear.isEmpty())
    }

    // ---- Best and worst -----------------------------------------------------------------

    @Test
    fun `best and worst name the extreme round trips, not the average`() {
        val perf = single(
            listOf(
                trade("WIN", Side.BUY, 10.0, 100.0, at(2026, 1, 5)),
                trade("WIN", Side.SELL, 10.0, 150.0, at(2026, 2, 5)),
                trade("MEH", Side.BUY, 10.0, 100.0, at(2026, 1, 5)),
                trade("MEH", Side.SELL, 10.0, 101.0, at(2026, 2, 5)),
                trade("BAD", Side.BUY, 10.0, 100.0, at(2026, 1, 5)),
                trade("BAD", Side.SELL, 10.0, 70.0, at(2026, 2, 5)),
            ),
        )

        assertEquals("WIN", perf.bestTrade!!.symbol)
        assertEquals(500.0, perf.bestTrade!!.profit, 0.001)
        assertEquals("BAD", perf.worstTrade!!.symbol)
        assertEquals(-300.0, perf.worstTrade!!.profit, 0.001)
    }
}
