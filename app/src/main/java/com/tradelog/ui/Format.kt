package com.tradelog.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Format {

    private val dayFmt = SimpleDateFormat("MM-dd", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val stampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun day(millis: Long): String = dayFmt.format(Date(millis))
    fun date(millis: Long): String = dateFmt.format(Date(millis))
    fun stamp(millis: Long): String = stampFmt.format(Date(millis))

    fun relative(millis: Long): String {
        if (millis <= 0L) return "从未"
        val delta = System.currentTimeMillis() - millis
        return when {
            delta < 60_000 -> "刚刚"
            delta < 3_600_000 -> "${delta / 60_000} 分钟前"
            delta < 86_400_000 -> "${delta / 3_600_000} 小时前"
            delta < 7 * 86_400_000L -> "${delta / 86_400_000} 天前"
            else -> date(millis)
        }
    }

    /** Full precision, grouped — for detail rows where the exact figure matters. */
    fun money(amount: Double, currency: String? = null): String {
        val sign = if (amount < 0) "-" else ""
        val body = String.format(Locale.US, "%,.2f", abs(amount))
        return if (currency == null) sign + body else "$sign$body $currency"
    }

    fun signedMoney(amount: Double, currency: String? = null): String {
        val sign = if (amount < 0) "-" else "+"
        val body = String.format(Locale.US, "%,.2f", abs(amount))
        return if (currency == null) sign + body else "$sign$body $currency"
    }

    /** Compact — for stat tiles and axis labels, where width is scarce. */
    fun compact(amount: Double): String {
        val a = abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            a >= 1_000_000_000 -> sign + String.format(Locale.US, "%.2fB", a / 1_000_000_000)
            a >= 1_000_000 -> sign + String.format(Locale.US, "%.2fM", a / 1_000_000)
            a >= 10_000 -> sign + String.format(Locale.US, "%.1fK", a / 1_000)
            else -> sign + String.format(Locale.US, "%,.2f", a)
        }
    }

    fun quantity(qty: Double): String =
        if (qty == qty.toLong().toDouble()) {
            String.format(Locale.US, "%,d", qty.toLong())
        } else {
            String.format(Locale.US, "%,.4f", qty)
        }

    /** Days as a human span: "45 天" up to a year, then "1.4 年". */
    fun holdingPeriod(days: Double): String = if (days < 365) {
        String.format(Locale.US, "%.0f 天", days)
    } else {
        String.format(Locale.US, "%.1f 年", days / 365.0)
    }

    fun price(p: Double): String = String.format(Locale.US, "%,.3f", p)

    /** An exchange rate: four decimals, enough for the big figure and the pips (7.0932, 0.1410). */
    fun rate(value: Double): String = String.format(Locale.US, "%,.4f", value)

    /**
     * A rate as a signed percentage. [rate] is a fraction, so 0.1234 renders as "+12.3%".
     * Large rates drop the decimal, since "+1234.5%" reads as noise at that magnitude.
     */
    fun percent(rate: Double): String {
        val pct = rate * 100
        val sign = if (pct < 0) "-" else "+"
        val body = if (abs(pct) >= 100) {
            String.format(Locale.US, "%,.0f", abs(pct))
        } else {
            String.format(Locale.US, "%.1f", abs(pct))
        }
        return "$sign$body%"
    }
}
