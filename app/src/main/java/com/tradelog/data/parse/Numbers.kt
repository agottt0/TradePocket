package com.tradelog.data.parse

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal object Numbers {

    private val NUMBER = Regex("""\(?-?\s*[\d,]+(?:\.\d+)?\)?""")

    /**
     * Parses the first number in [text], tolerating thousands separators, a currency
     * prefix, a trailing minus, and accounting-style parentheses for negatives.
     * Returns null when there is no number at all.
     */
    fun parse(text: String?): Double? {
        if (text == null) return null
        val cleaned = text.replace('\u00a0', ' ').replace("\uFF0C", ",")
        val match = NUMBER.find(cleaned) ?: return null
        val token = match.value
        val negative = token.contains('(') || token.trimStart().startsWith('-')
        val digits = token.filter { it.isDigit() || it == '.' }
        if (digits.isEmpty() || digits == ".") return null
        val value = digits.toDoubleOrNull() ?: return null
        return if (negative) -value else value
    }

    /** Same as [parse] but always non-negative — for fee lines where the sign is noise. */
    fun parseAbs(text: String?): Double? = parse(text)?.let { kotlin.math.abs(it) }
}

internal object Dates {

    private val PATTERNS = listOf(
        "dd/MM/yyyy HH:mm:ss",
        "dd/MM/yyyy HH:mm",
        "dd/MM/yyyy",
        "dd-MMM-yyyy HH:mm:ss",
        "dd-MMM-yyyy HH:mm",
        "dd-MMM-yyyy",
        "dd MMM yyyy HH:mm:ss",
        "dd MMM yyyy",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd",
        "yyyyMMdd",
    )

    /**
     * Parses a broker date/time in [zone]. HSBC statements carry no offset, so the
     * caller supplies the market's zone (Hong Kong by default) rather than the device's.
     */
    fun parse(text: String?, zone: TimeZone = TimeZone.getTimeZone("Asia/Hong_Kong")): Long? {
        val raw = text?.trim()?.replace('\u00a0', ' ')?.replace(Regex("""\s+"""), " ") ?: return null
        if (raw.isEmpty()) return null
        // Chinese-style "2026年3月5日" → "2026-03-05"
        val normalised = raw
            .replace(Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日""")) { m ->
                "%04d-%02d-%02d".format(
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt(),
                    m.groupValues[3].toInt(),
                )
            }
        for (pattern in PATTERNS) {
            val fmt = SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                timeZone = zone
                isLenient = false
            }
            try {
                return fmt.parse(normalised)?.time ?: continue
            } catch (_: java.text.ParseException) {
                // try the next pattern
            }
        }
        return null
    }
}
