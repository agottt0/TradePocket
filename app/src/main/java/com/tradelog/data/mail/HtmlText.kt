package com.tradelog.data.mail

/**
 * Minimal HTML -> plain text conversion, tuned for broker confirmation emails.
 *
 * HSBC lays its confirmations out as tables, so the goal is to keep each
 * label/value pair on its own line: a cell end becomes a " : " separator and a
 * row end becomes a newline. A full HTML parser would be a heavy dependency for
 * what amounts to tag stripping.
 */
object HtmlText {

    /** Non-breaking space: HSBC's templates are full of them. */
    private const val NBSP = '\u00a0'

    /** Private-use marker for a cell boundary, so it survives tag stripping. */
    private const val CELL_MARK = "\uE000"

    private val SCRIPT_STYLE = Regex(
        """<(script|style)\b[^>]*>.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val CELL_END = Regex("""</t[dh]>""", RegexOption.IGNORE_CASE)
    private val ROW_END = Regex("""</(tr|table|p|div|li|h[1-6])>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]+>""")
    private val BLANK_LINES = Regex("""\n{3,}""")
    // Deliberately not a raw string: a raw literal cannot express the non-breaking
    // space as an escape, and a literal one here would be invisible in a diff.
    private val TRAILING_SPACE = Regex("[ \t\u00a0]+\n")
    private val RUN_OF_SPACES = Regex("""[ \t]{2,}""")

    fun toPlainText(html: String): String {
        var s = html
        s = SCRIPT_STYLE.replace(s, " ")
        s = s.replace("<!--", "\n").replace("-->", "\n")
        s = BR.replace(s, "\n")
        s = CELL_END.replace(s, CELL_MARK)
        s = ROW_END.replace(s, "\n")
        s = TAG.replace(s, " ")
        s = decodeEntities(s)
        s = s.replace(NBSP, ' ')
        s = s.replace(CELL_MARK, " : ")
        s = s.lines().joinToString("\n") { line -> RUN_OF_SPACES.replace(line, " ").trim() }
        s = TRAILING_SPACE.replace(s, "\n")
        s = BLANK_LINES.replace(s, "\n\n")
        return s.trim()
    }

    private val NAMED = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ", // decoded to a plain space on purpose
        "ndash" to "-",
        "mdash" to "-",
        "hellip" to "...",
        "middot" to "·",
        "bull" to "·",
    )

    private val ENTITY = Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z]+);""")

    fun decodeEntities(text: String): String = ENTITY.replace(text) { m ->
        val body = m.groupValues[1]
        when {
            body.startsWith("#x", ignoreCase = true) ->
                body.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value

            body.startsWith("#") ->
                body.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value

            else -> NAMED[body.lowercase()] ?: m.value
        }
    }
}
