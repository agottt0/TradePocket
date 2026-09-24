package com.tradelog.data.perf

import com.tradelog.data.db.Side
import com.tradelog.data.db.Trade
import com.tradelog.data.fx.FxSnapshot
import java.util.Calendar
import java.util.TimeZone

/**
 * This calendar month's traded volume, converted to HKD at the current exchange rate.
 *
 * Turnover is activity, not result: buys and sells both count, at their gross consideration
 * (quantity x price, fees excluded). Trades whose currency has no known rate are left out of
 * the totals and flagged via [hasUnconverted] — an honest smaller number over a silently
 * wrong one.
 */
data class MonthlyTurnover(
    val buyHkd: Double,
    val sellHkd: Double,
    val tradeCount: Int,
    val hasUnconverted: Boolean,
) {
    val totalHkd: Double get() = buyHkd + sellHkd
}

object MonthlyTurnoverCalculator {

    private const val TARGET = "HKD"

    fun compute(
        trades: List<Trade>,
        fx: FxSnapshot?,
        zone: TimeZone = TimeZone.getDefault(),
        now: Long = System.currentTimeMillis(),
    ): MonthlyTurnover {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = now
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)

        var buy = 0.0
        var sell = 0.0
        var count = 0
        var unconverted = false

        for (trade in trades) {
            calendar.timeInMillis = trade.tradeDate
            if (calendar.get(Calendar.YEAR) != year || calendar.get(Calendar.MONTH) != month) {
                continue
            }
            count++

            val inHkd = if (trade.currency == TARGET) {
                trade.gross
            } else {
                val rate = fx?.rate(trade.currency, TARGET)
                if (rate == null) {
                    unconverted = true
                    continue
                }
                trade.gross * rate
            }

            when (trade.side) {
                Side.BUY -> buy += inHkd
                Side.SELL -> sell += inHkd
            }
        }

        return MonthlyTurnover(
            buyHkd = buy,
            sellHkd = sell,
            tradeCount = count,
            hasUnconverted = unconverted,
        )
    }
}
