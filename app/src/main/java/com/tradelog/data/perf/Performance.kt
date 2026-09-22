package com.tradelog.data.perf

import com.tradelog.data.db.Side
import com.tradelog.data.db.Trade
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/** One buy matched against one sell: a closed round trip. */
data class RealizedLot(
    val symbol: String,
    val name: String?,
    val currency: String,
    val quantity: Double,
    val buyDate: Long,
    val sellDate: Long,
    /** What the matched shares cost, including the buy's share of its fees. */
    val cost: Double,
    /** What the matched shares brought in, after the sell's share of its fees. */
    val proceeds: Double,
) {
    val profit: Double get() = proceeds - cost

    /** Return on this round trip. Null when cost is zero, which would make it meaningless. */
    val roi: Double? get() = if (cost > 0) profit / cost else null

    val holdingDays: Double
        get() = ((sellDate - buyDate).coerceAtLeast(0)).toDouble() / MILLIS_PER_DAY

    private companion object {
        const val MILLIS_PER_DAY = 24.0 * 60 * 60 * 1000
    }
}

/** Shares still held: bought and not yet sold. */
data class OpenLot(
    val symbol: String,
    val name: String?,
    val currency: String,
    val quantity: Double,
    val buyDate: Long,
    val cost: Double,
) {
    val averagePrice: Double get() = if (quantity > 0) cost / quantity else 0.0
}

/**
 * A sale the ledger cannot fully account for: more shares sold than were ever recorded
 * as bought. Normally means the position predates what the app has imported.
 */
data class UnmatchedSale(
    val symbol: String,
    val currency: String,
    val quantity: Double,
    val sellDate: Long,
    val proceeds: Double,
)

/** Realized figures for one calendar period, in one currency. */
data class PeriodPerformance(
    /** `2026-03` for a month, `2026` for a year. */
    val key: String,
    val year: Int,
    /** 1-12, or null for a yearly bucket. */
    val month: Int?,
    val currency: String,
    val cost: Double,
    val proceeds: Double,
    val closedTrades: Int,
) {
    val profit: Double get() = proceeds - cost
    val roi: Double? get() = if (cost > 0) profit / cost else null
}

/**
 * One stock's standing in one currency: what has been closed, and what is still held.
 *
 * Both halves live together because that is the question being asked of a symbol — "how did
 * INTC do, and do I still hold any" — and keeping them in one row is what stops a sold-out
 * position from being displayed as if it were still losing money.
 */
data class SymbolPerformance(
    val symbol: String,
    val name: String?,
    val currency: String,
    /** Cost of the closed round trips only. */
    val realizedCost: Double,
    val realizedProceeds: Double,
    val closedTrades: Int,
    /** Shares still held. Zero means the position is fully closed. */
    val openQuantity: Double,
    /** Cost of the shares still held. Not a valuation — there are no market prices here. */
    val openCost: Double,
    val lastTradeDate: Long,
) {
    /** Realized profit: what selling actually earned. */
    val realizedProfit: Double get() = realizedProceeds - realizedCost

    /** Return on the capital that was closed out. Null when nothing has been sold. */
    val realizedRoi: Double? get() = if (realizedCost > 0) realizedProfit / realizedCost else null

    /** True when every share bought has been sold. */
    val isClosed: Boolean get() = openQuantity <= 0

    val averageOpenPrice: Double? get() = if (openQuantity > 0) openCost / openQuantity else null
}

/**
 * Everything the returns screen needs, for a single currency.
 *
 * Currencies are never summed: adding HKD to USD produces a number that means nothing, and
 * this app has no exchange rates. Each currency gets its own [CurrencyPerformance], and every
 * figure on it is denominated in [currency] alone.
 */
data class CurrencyPerformance(
    val currency: String,
    val realized: List<RealizedLot>,
    val openLots: List<OpenLot>,
    val unmatchedSales: List<UnmatchedSale>,
    /** Per-stock rollup, closed and open together. Sorted by weight in this currency. */
    val bySymbol: List<SymbolPerformance>,
    val byMonth: List<PeriodPerformance>,
    val byYear: List<PeriodPerformance>,
) {
    val totalCost: Double get() = realized.sumOf { it.cost }
    val totalProceeds: Double get() = realized.sumOf { it.proceeds }

    /** The headline: realized profit across every closed round trip in this currency. */
    val totalProfit: Double get() = totalProceeds - totalCost

    /**
     * Return on closed positions: profit over what those positions cost.
     *
     * The denominator is the cost of the *closed* positions only. Cost still tied up in open
     * positions is excluded, because those have not returned anything yet — including them
     * would drag a real gain toward zero.
     */
    val totalRoi: Double? get() = if (totalCost > 0) totalProfit / totalCost else null

    /** Cost of everything still held. Not a valuation — there are no market prices here. */
    val openCost: Double get() = openLots.sumOf { it.cost }

    /** Stocks still held, with the quantity outstanding. Fully-closed symbols are absent. */
    val openBySymbol: List<SymbolPerformance> get() = bySymbol.filter { !it.isClosed }

    /** Stocks that have been sold out entirely. */
    val closedBySymbol: List<SymbolPerformance> get() = bySymbol.filter { it.isClosed }

    /**
     * Annualized return on capital actually at risk, over the time it was at risk.
     *
     * `sum(profit) / sum(cost x years-held)` — a money-weighted rate answering "what did each
     * unit of capital earn per year while it was invested". Two shortcuts a plain
     * `profit / cost` figure gets wrong are handled: a gain made in one week is not treated the
     * same as the same gain over three years, and a large position counts for more than a small
     * one held equally long.
     *
     * Null when there is no closed position, or when the time-weighted capital rounds to zero
     * (every round trip closed the same day) — annualizing an instant is meaningless.
     */
    val annualizedReturn: Double?
        get() {
            if (realized.isEmpty()) return null
            val capitalYears = realized.sumOf { it.cost * (it.holdingDays / DAYS_PER_YEAR) }
            if (capitalYears < MIN_CAPITAL_YEARS) return null
            return totalProfit / capitalYears
        }

    /** Average holding period of closed positions, weighted by cost. */
    val averageHoldingDays: Double?
        get() {
            val weight = realized.sumOf { it.cost }
            if (weight <= 0) return null
            return realized.sumOf { it.cost * it.holdingDays } / weight
        }

    val winCount: Int get() = realized.count { it.profit > 0 }
    val lossCount: Int get() = realized.count { it.profit < 0 }

    /** Share of closed round trips that made money. */
    val winRate: Double? get() = if (realized.isEmpty()) null else winCount.toDouble() / realized.size

    /** The single best and worst closed round trips, for the detail view. */
    val bestTrade: RealizedLot? get() = realized.maxByOrNull { it.profit }
    val worstTrade: RealizedLot? get() = realized.minByOrNull { it.profit }

    private companion object {
        const val DAYS_PER_YEAR = 365.0

        /**
         * Below roughly one day of one unit of capital, the divisor is small enough that the
         * annualized figure explodes into a meaningless number, so it is reported as unavailable.
         */
        const val MIN_CAPITAL_YEARS = 1.0 / 365.0
    }
}

/**
 * Matches sells against buys to work out what was actually earned.
 *
 * FIFO: the oldest shares are sold first. This is the default basis for tax reporting in most
 * jurisdictions including Hong Kong practice, and unlike average-cost it keeps each round trip's
 * own holding period, which is what the annualized figure needs.
 *
 * Only *realized* profit is computed. Unrealized gains on open positions would need current
 * market prices, and this app has none — it reads confirmation emails, not quotes. Open
 * positions are reported at cost so they are visible, never as a valuation. A position that has
 * been sold out therefore carries its realized result and nothing else: no residual cost that
 * could read as an ongoing loss.
 */
object PerformanceCalculator {

    fun compute(
        trades: List<Trade>,
        zone: TimeZone = TimeZone.getDefault(),
    ): List<CurrencyPerformance> =
        trades.groupBy { it.currency }
            .map { (currency, sameCurrency) -> computeOne(currency, sameCurrency, zone) }
            // Lead with the currency carrying the most at stake, closed or open.
            .sortedByDescending { abs(it.totalProfit) + it.openCost }

    private fun computeOne(
        currency: String,
        trades: List<Trade>,
        zone: TimeZone,
    ): CurrencyPerformance {
        val realized = mutableListOf<RealizedLot>()
        val unmatched = mutableListOf<UnmatchedSale>()
        val remaining = mutableListOf<OpenLot>()
        val symbols = mutableListOf<SymbolPerformance>()

        // Per symbol, so one stock's sells never consume another's shares.
        for ((symbol, symbolTrades) in trades.groupBy { it.symbol }) {
            val queue = ArrayDeque<MutableLot>()
            val symbolRealized = mutableListOf<RealizedLot>()

            // Stable ordering: by time, and a buy before a sell within the same instant, so a
            // same-minute buy-then-sell matches rather than looking like an unbacked sale.
            val ordered = symbolTrades.sortedWith(
                compareBy({ it.tradeDate }, { if (it.side == Side.BUY) 0 else 1 }, { it.id }),
            )

            for (trade in ordered) {
                if (trade.quantity <= 0) continue
                when (trade.side) {
                    Side.BUY -> queue.addLast(
                        MutableLot(
                            name = trade.name,
                            quantity = trade.quantity,
                            // Fees are part of what the shares cost.
                            unitCost = (trade.quantity * trade.price + trade.fees) / trade.quantity,
                            buyDate = trade.tradeDate,
                        ),
                    )

                    Side.SELL -> {
                        var toMatch = trade.quantity
                        // Sale fees come off the proceeds, spread across the shares sold.
                        val unitProceeds =
                            (trade.quantity * trade.price - trade.fees) / trade.quantity

                        while (toMatch > EPSILON && queue.isNotEmpty()) {
                            val lot = queue.first()
                            val take = minOf(toMatch, lot.quantity)
                            symbolRealized += RealizedLot(
                                symbol = symbol,
                                name = trade.name ?: lot.name,
                                currency = currency,
                                quantity = take,
                                buyDate = lot.buyDate,
                                sellDate = trade.tradeDate,
                                cost = take * lot.unitCost,
                                proceeds = take * unitProceeds,
                            )
                            lot.quantity -= take
                            toMatch -= take
                            if (lot.quantity <= EPSILON) queue.removeFirst()
                        }

                        if (toMatch > EPSILON) {
                            // Sold shares with no recorded purchase: the position predates the
                            // ledger, or a buy confirmation was never imported. Reported, never
                            // silently folded into profit as if it had cost nothing.
                            unmatched += UnmatchedSale(
                                symbol = symbol,
                                currency = currency,
                                quantity = toMatch,
                                sellDate = trade.tradeDate,
                                proceeds = toMatch * unitProceeds,
                            )
                        }
                    }
                }
            }

            val symbolOpen = queue.map { lot ->
                OpenLot(
                    symbol = symbol,
                    name = lot.name,
                    currency = currency,
                    quantity = lot.quantity,
                    buyDate = lot.buyDate,
                    cost = lot.quantity * lot.unitCost,
                )
            }

            realized += symbolRealized
            remaining += symbolOpen

            symbols += SymbolPerformance(
                symbol = symbol,
                name = symbolTrades.firstNotNullOfOrNull { it.name },
                currency = currency,
                realizedCost = symbolRealized.sumOf { it.cost },
                realizedProceeds = symbolRealized.sumOf { it.proceeds },
                closedTrades = symbolRealized.size,
                openQuantity = symbolOpen.sumOf { it.quantity },
                openCost = symbolOpen.sumOf { it.cost },
                lastTradeDate = ordered.maxOf { it.tradeDate },
            )
        }

        return CurrencyPerformance(
            currency = currency,
            realized = realized.sortedByDescending { it.sellDate },
            openLots = remaining.sortedByDescending { it.buyDate },
            unmatchedSales = unmatched.sortedByDescending { it.sellDate },
            bySymbol = symbols.sortedByDescending { abs(it.realizedProfit) + it.openCost },
            byMonth = bucket(realized, currency, zone, monthly = true),
            byYear = bucket(realized, currency, zone, monthly = false),
        )
    }

    /**
     * Groups closed lots into calendar buckets by **sell** date: profit is realized when the
     * position closes, so that is the period it belongs to.
     */
    private fun bucket(
        realized: List<RealizedLot>,
        currency: String,
        zone: TimeZone,
        monthly: Boolean,
    ): List<PeriodPerformance> {
        val calendar = Calendar.getInstance(zone)
        val groups = realized.groupBy { lot ->
            calendar.timeInMillis = lot.sellDate
            val y = calendar.get(Calendar.YEAR)
            val m = calendar.get(Calendar.MONTH) + 1
            if (monthly) y to m else y to null
        }

        return groups.map { (key, lots) ->
            val (year, month) = key
            PeriodPerformance(
                key = if (month != null) "%04d-%02d".format(year, month) else "%04d".format(year),
                year = year,
                month = month,
                currency = currency,
                cost = lots.sumOf { it.cost },
                proceeds = lots.sumOf { it.proceeds },
                closedTrades = lots.size,
            )
        }.sortedBy { it.key }
    }

    /** Working state for one open buy lot. */
    private class MutableLot(
        val name: String?,
        var quantity: Double,
        val unitCost: Double,
        val buyDate: Long,
    )

    /** Fractional shares are real, so quantities are compared with a tolerance. */
    private const val EPSILON = 1e-9
}
