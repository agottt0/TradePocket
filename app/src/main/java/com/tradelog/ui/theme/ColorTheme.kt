package com.tradelog.ui.theme

/**
 * How gains and losses are coloured.
 *
 * This is a display preference only: it never changes a stored figure or which sign a number
 * carries. Every place a theme colour appears, the sign (`+` / `-`) or the bar's direction
 * carries the same information, so the meaning survives in [MONO] and for a reader who cannot
 * separate the hues.
 */
enum class ColorTheme(val label: String, val description: String) {
    /** Simple: figures in plain ink, no colour on numbers. */
    MONO("简洁", "数字用纯黑字体，不着色"),

    /** The Greater China convention: red is up, green is down. */
    RED_GAIN("红涨绿跌", "盈利红色，亏损绿色"),

    /** The Western convention, for completeness. */
    GREEN_GAIN("绿涨红跌", "盈利绿色，亏损红色"),
    ;

    companion object {
        val DEFAULT = RED_GAIN

        fun fromName(name: String?): ColorTheme =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
