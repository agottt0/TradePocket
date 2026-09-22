package com.tradelog.data.parse

import com.tradelog.data.db.Side
import com.tradelog.data.db.Trade
import com.tradelog.data.mail.EmailMessage
import kotlin.math.abs

sealed interface ParseResult {
    data class Success(val trade: Trade) : ParseResult
    /** Recognised as a trade confirmation, but a required field was missing. */
    data class Partial(val trade: Trade, val missing: List<String>) : ParseResult
    /** Not a trade confirmation at all — skip it quietly. */
    data class NotATrade(val reason: String) : ParseResult
}

/**
 * Parses HSBC securities trade-confirmation emails into [Trade] rows.
 *
 * Design notes:
 *  - Label-driven, not position-driven. HSBC's HTML layout changes between markets and
 *    template revisions, but the labels ("Stock Code", "Executed Price", "股票代號") are stable.
 *    Each field has a list of accepted labels in both English and Chinese.
 *  - Never guesses a number. A field it cannot find is reported in [ParseResult.Partial.missing]
 *    and the row is flagged `needsReview` so it shows up in the UI rather than silently
 *    entering the books wrong.
 *  - The sign convention lives in one place: [signedNet].
 */
object HsbcTradeParser {

    /** From-address fragments that mark a message as plausibly from HSBC. */
    private val SENDER_HINTS = listOf(
        "hsbc.com",
        "hsbc.com.hk",
        "hsbc.co.uk",
        "hsbc.com.cn",
    )

    /**
     * Wording that marks a message as an execution notice.
     *
     * HSBC HK / Easy Invest leads with the fill status — 全部執行 (fully executed) or
     * 部分執行 (partially filled) — rather than the words "confirmation" or 成交通知,
     * so those statuses are the primary signal.
     */
    private val TRADE_SUBJECT_HINTS = listOf(
        "全部執行",
        "全部执行",
        "部分執行",
        "部分执行",
        "fully executed",
        "partially executed",
        "partially filled",
        "trade confirmation",
        "contract note",
        "order executed",
        "execution confirmation",
        "deal confirmation",
        "securities transaction",
        "買賣單據",
        "成交通知",
        "交易確認",
        "買賣確認",
        "股票交易",
    )

    private val NON_TRADE_HINTS = listOf(
        "未執行",
        "未执行",
        "已取消",
        "已拒絕",
        "訂單已接收",
        "order placed",
        "order received",
        "order cancelled",
        "order rejected",
        "order expired",
        "statement is ready",
        "monthly statement",
        "e-statement",
        "dividend",
        "corporate action",
        "password",
        "security alert",
        "訂單已接收",
        "落單確認",
        "已取消",
        "月結單",
        "派息",
    )

    // ---- Field label dictionaries -------------------------------------------------------

    /**
     * Labels for a field holding the code alone. HSBC's execution notice instead uses a
     * combined 股票名稱/ 股票編號 field, handled separately by [LABELS_NAME_AND_SYMBOL].
     */
    private val LABELS_SYMBOL = listOf(
        "stock code", "stock symbol", "security code", "instrument code", "product code",
        "股票編號", "股票编号", "股票代號", "股票代码", "證券代號", "证券代码", "股份代號",
        "symbol", "ticker", "code", "代號", "代码",
    )

    /**
     * A single field carrying both name and code, e.g.
     * `股票名稱/ 股票編號: INTEL CORPORATION (INTC)`.
     * Note the space inside the label — HSBC writes `股票名稱/ 股票編號`, so matching is
     * done on a whitespace-insensitive form of the line.
     */
    private val LABELS_NAME_AND_SYMBOL = listOf(
        "股票名稱/股票編號", "股票名称/股票编号",
        "股票名稱/ 股票編號", "股票名称/ 股票编号",
        "證券名稱/證券代號", "证券名称/证券代码",
        "stock name/stock code", "stock name / stock code",
    )
    private val LABELS_NAME = listOf(
        "stock name", "security name", "instrument name", "product name", "stock",
        "股票名稱", "股票名称", "證券名稱", "证券名称", "股份名稱", "名稱",
    )
    private val LABELS_SIDE = listOf(
        "指示類別", "指示类别", "指示種類", "指示种类",
        "buy/sell", "buy / sell", "transaction type", "trade type", "order type",
        "instruction type", "side", "direction", "instruction",
        "買入/賣出", "买入/卖出", "交易類型", "交易类型", "買賣", "买卖", "指示",
    )
    /**
     * Quantity labels, most specific first.
     *
     * HSBC emits three quantity lines per notice: 已成交數量 (this fill), 共成交數量
     * (total filled) and 餘下數量 (remaining, normally 0). 已成交數量 is the one that
     * belongs in the books — 共成交 would double-count a partially-filled order that
     * sends one notice per fill, and 餘下 is not a trade at all.
     */
    private val LABELS_QTY = listOf(
        "已成交數量", "已成交数量",
        "executed quantity", "filled quantity", "quantity executed",
        "trade quantity", "no. of shares", "number of shares",
        "成交數量", "成交数量", "股數", "股数", "shares", "quantity", "units",
        "數量", "数量",
    )
    private val LABELS_PRICE = listOf(
        "executed price", "average price", "avg price", "trade price", "deal price",
        "price per share", "unit price", "price",
        "成交價", "成交价", "平均價", "平均价", "每股價格", "價格", "价格",
    )
    private val LABELS_GROSS = listOf(
        "gross amount", "gross consideration", "consideration", "trade amount",
        "principal amount", "principal", "transaction amount", "gross value",
        "成交金額", "成交金额", "交易金額", "交易金额", "總金額", "总金额",
    )
    private val LABELS_NET = listOf(
        "net amount", "net consideration", "settlement amount", "amount payable",
        "amount receivable", "total amount", "net proceeds", "net settlement",
        "淨額", "净额", "應付金額", "应付金额", "應收金額", "结算金额", "結算金額", "總計", "总计",
    )
    private val LABELS_CURRENCY = listOf(
        "currency", "trade currency", "settlement currency", "ccy",
        "貨幣", "货币", "交易貨幣", "交易货币", "結算貨幣",
    )
    private val LABELS_MARKET = listOf(
        "market", "exchange", "trading market",
        "市場", "市场", "交易所",
    )
    private val LABELS_DATE = listOf(
        "trade date and time", "trade date/time", "execution time", "executed at",
        "trade date", "transaction date", "deal date", "date",
        "成交日期", "交易日期", "成交時間", "成交时间", "日期",
    )
    private val LABELS_ORDER_REF = listOf(
        "交易編號", "交易编号",
        "order reference", "order ref", "order no", "order number", "reference no",
        "contract note no", "transaction reference", "reference",
        "訂單編號", "订单编号", "参考编号", "參考編號", "單據編號",
    )
    private val LABELS_ACCOUNT = listOf(
        "account number", "account no", "investment account", "account",
        "帳戶號碼", "账户号码", "投資帳戶", "投资账户", "帳戶", "账户",
    )

    /** Every fee-ish line HSBC itemises; all of them are summed into one `fees` figure. */
    private val LABELS_FEES = listOf(
        "commission", "brokerage", "handling fee", "handling charge", "service charge",
        "stamp duty", "transaction levy", "trading fee", "trading tariff", "clearing fee",
        "ccass fee", "settlement fee", "sfc levy", "frc transaction levy", "afrc levy",
        "exchange fee", "platform fee", "custody fee", "sec fee", "taf fee",
        "stock exchange trading fee", "government charge", "regulatory fee", "tax",
        "佣金", "經紀佣金", "手續費", "手续费", "服務費", "服务费", "印花稅", "印花税",
        "交易徵費", "交易征费", "交易費", "交易费", "結算費", "结算费", "中央結算費",
        "證監會徵費", "证监会征费", "財務匯報局", "交易所費用", "政府費用", "稅項",
    )

    private val BUY_TOKENS = listOf(
        "buy", "bought", "purchase", "b",
        "買入", "买入", "買", "买",
    )

    /** 沽出 is HSBC HK's word for a sale; without it every sell parses as unknown. */
    private val SELL_TOKENS = listOf(
        "sell", "sold", "sale", "s",
        "沽出", "沽", "賣出", "卖出", "賣", "卖",
    )

    private val KNOWN_CURRENCIES = listOf(
        "HKD", "USD", "CNY", "CNH", "RMB", "GBP", "EUR", "JPY", "SGD", "AUD", "CAD", "CHF", "TWD", "KRW",
    )

    private val CURRENCY_SYMBOLS = mapOf(
        "HK$" to "HKD",
        "US$" to "USD",
        "RMB" to "CNY",
        "£" to "GBP",
        "€" to "EUR",
        "¥" to "JPY",
        "S$" to "SGD",
        "A$" to "AUD",
    )

    private val LABELS_STATUS = listOf(
        "交易狀況", "交易状况", "訂單狀況", "订单状况", "order status", "status",
    )

    /** Statuses that mean the order actually filled. */
    private val EXECUTED_STATUSES = listOf(
        "全部執行", "全部执行", "部分執行", "部分执行",
        "fully executed", "partially executed", "partially filled", "executed",
    )

    // ---- Entry point ---------------------------------------------------------------------

    fun looksLikeHsbc(message: EmailMessage): Boolean {
        val from = message.from.lowercase()
        return SENDER_HINTS.any { from.contains(it) }
    }

    fun parse(message: EmailMessage): ParseResult {
        val subject = message.subject.lowercase()
        val body = message.body
        val haystack = (subject + "\n" + body).lowercase()

        val subjectSaysTrade = TRADE_SUBJECT_HINTS.any { subject.contains(it) }
        val bodySaysTrade = TRADE_SUBJECT_HINTS.any { haystack.contains(it) }
        if (!subjectSaysTrade && !bodySaysTrade) {
            return ParseResult.NotATrade("no trade-confirmation wording")
        }
        // A rejection notice can quote the original order, so only the subject can veto.
        NON_TRADE_HINTS.firstOrNull { subject.contains(it) && !subjectSaysTrade }?.let {
            return ParseResult.NotATrade("subject indicates '$it', not an execution")
        }

        val fields = LabelledFields.from(body)

        // When the notice states a status, trust it over the subject: an amended or
        // cancelled order can still carry executed-looking wording in the subject line.
        fields.text(LABELS_STATUS)?.let { status ->
            val lower = status.lowercase()
            val executed = EXECUTED_STATUSES.any { lower.contains(it.lowercase()) }
            if (!executed) return ParseResult.NotATrade("status is '$status', not an execution")
        }
        val missing = mutableListOf<String>()

        // HSBC's execution notice packs both into one field; a contract note splits them.
        val combined = fields.text(LABELS_NAME_AND_SYMBOL)?.let { splitNameAndSymbol(it) }

        val symbol = normaliseSymbol(
            fields.text(LABELS_SYMBOL)
                ?: combined?.symbol
                ?: symbolFromSubject(message.subject),
        )
        if (symbol == null) missing += "股票代码"

        val name = fields.text(LABELS_NAME) ?: combined?.name

        val side = parseSide(fields.text(LABELS_SIDE) ?: message.subject)
        if (side == null) missing += "买卖方向"

        val quantity = fields.number(LABELS_QTY)?.let { abs(it) }
        if (quantity == null || quantity == 0.0) missing += "成交数量"

        // The price field carries its own currency: "USD87.69".
        val priceText = fields.text(LABELS_PRICE)
        val price = Numbers.parse(priceText)?.let { abs(it) }
        if (price == null || price == 0.0) missing += "成交价"

        val currency = priceText?.let { normaliseCurrency(it) }
            ?: fields.text(LABELS_CURRENCY)?.let { normaliseCurrency(it) }
            ?: currencyFromBody(body)
            ?: "HKD"

        // The execution notice has no trade-date field, so the email's own timestamp is
        // the best available answer. It is the send time, not the exchange fill time.
        val tradeDate = Dates.parse(fields.text(LABELS_DATE)) ?: message.sentAt

        // Execution notices itemise no charges at all. Absent fees are 0, not unknown:
        // the real commission shows up later on the contract note or statement.
        val fees = fields.sumOf(LABELS_FEES)

        val statedGross = fields.number(LABELS_GROSS)?.let { abs(it) }
        val computedGross = if (quantity != null && price != null) quantity * price else null
        val gross = statedGross ?: computedGross ?: 0.0

        val statedNet = fields.number(LABELS_NET)?.let { abs(it) }
        val resolvedSide = side ?: Side.BUY
        val net = signedNet(resolvedSide, statedNet ?: (gross + if (resolvedSide == Side.BUY) fees else -fees))

        val trade = Trade(
            messageKey = message.stableKey(),
            tradeDate = tradeDate,
            symbol = symbol ?: "UNKNOWN",
            name = name,
            market = fields.text(LABELS_MARKET),
            side = resolvedSide,
            quantity = quantity ?: 0.0,
            price = price ?: 0.0,
            currency = currency,
            gross = gross,
            fees = fees,
            netAmount = net,
            orderRef = fields.text(LABELS_ORDER_REF),
            accountRef = fields.text(LABELS_ACCOUNT)?.let { maskAccount(it) },
            needsReview = missing.isNotEmpty(),
            rawSubject = message.subject,
        )

        return if (missing.isEmpty()) ParseResult.Success(trade) else ParseResult.Partial(trade, missing)
    }

    /**
     * The one place the cash-direction convention lives: a buy is cash out (negative),
     * a sell is cash in (positive). [magnitude] is always treated as unsigned.
     */
    fun signedNet(side: Side, magnitude: Double): Double =
        if (side == Side.BUY) -abs(magnitude) else abs(magnitude)

    // ---- Field helpers ------------------------------------------------------------------

    internal data class NameAndSymbol(val name: String?, val symbol: String?)

    private val TRAILING_PAREN = Regex("""^(.*?)[\s]*[(\uFF08]([^)\uFF09]{1,12})[)\uFF09]\s*$""")

    /**
     * Splits `INTEL CORPORATION (INTC)` into its name and code.
     *
     * Falls back to a slash-separated form (`INTEL CORPORATION / INTC`) and, failing that,
     * treats the whole string as a name so the row still carries something readable
     * rather than being silently dropped.
     */
    internal fun splitNameAndSymbol(raw: String): NameAndSymbol {
        val text = raw.trim()
        if (text.isEmpty()) return NameAndSymbol(null, null)

        TRAILING_PAREN.matchEntire(text)?.let { m ->
            val name = m.groupValues[1].trim().ifEmpty { null }
            val code = m.groupValues[2].trim().ifEmpty { null }
            return NameAndSymbol(name, code)
        }

        val parts = text.split('/', '\uFF0F').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            // The shorter half is the code: "INTEL CORPORATION / INTC".
            val code = parts.minByOrNull { it.length }
            val name = parts.firstOrNull { it != code }
            return NameAndSymbol(name, code)
        }

        return NameAndSymbol(text, null)
    }

    private fun symbolFromSubject(subject: String): String? {
        // Only trust the subject for an explicitly-formatted code, e.g. "0005.HK" or "(00700)".
        Regex("""\((\d{4,5})\)""").find(subject)?.let { return it.groupValues[1] }
        Regex("""\b(\d{4,5}\.HK)\b""", RegexOption.IGNORE_CASE).find(subject)?.let { return it.groupValues[1] }
        return null
    }

    internal fun normaliseSymbol(raw: String?): String? {
        val token = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Strip a trailing name that shared the cell: "0005 HSBC HOLDINGS" -> "0005"
        val first = token.split(Regex("""[\s,/|]+""")).firstOrNull()?.trim() ?: return null
        val cleaned = first.removeSurrounding("(", ")").uppercase()
            .trimEnd(':', '\uFF1A')
        if (cleaned.isEmpty()) return null
        if (!cleaned.all { it.isDigit() }) return cleaned
        // Hong Kong codes are written zero-padded to 4 digits, but the same instrument
        // arrives as "5", "0005" or "00005" across templates. Normalise all of them to
        // "0005" so they group as a single position.
        val bare = cleaned.trimStart('0').ifEmpty { "0" }
        return if (bare.length <= 4) bare.padStart(4, '0') else bare
    }

    internal fun parseSide(raw: String?): Side? {
        val text = raw?.lowercase()?.trim() ?: return null
        // Longest tokens first so "sold" is not matched by the bare "s" rule.
        val buyHit = BUY_TOKENS.sortedByDescending { it.length }
            .firstOrNull { containsToken(text, it) }
        val sellHit = SELL_TOKENS.sortedByDescending { it.length }
            .firstOrNull { containsToken(text, it) }
        return when {
            buyHit != null && sellHit == null -> Side.BUY
            sellHit != null && buyHit == null -> Side.SELL
            buyHit != null && sellHit != null ->
                // Both present (e.g. a "Buy/Sell" label leaked in): trust the earlier one.
                if (text.indexOf(buyHit) < text.indexOf(sellHit)) Side.BUY else Side.SELL
            else -> null
        }
    }

    private fun containsToken(text: String, token: String): Boolean {
        if (token.any { it.code > 127 }) return text.contains(token)
        // Single-letter tokens must stand alone, or "s" would match every word.
        val pattern = if (token.length == 1) """(?<![a-z])$token(?![a-z])""" else """\b$token\b"""
        return Regex(pattern).containsMatchIn(text)
    }

    internal fun normaliseCurrency(raw: String): String? {
        val upper = raw.uppercase()
        // No \b after the code: HSBC writes the price as "USD87.69", with no separator,
        // and a word boundary does not exist between "D" and "8".
        KNOWN_CURRENCIES.firstOrNull { Regex("""\b$it""").containsMatchIn(upper) }?.let {
            return if (it == "RMB" || it == "CNH") "CNY" else it
        }
        CURRENCY_SYMBOLS.entries.firstOrNull { upper.contains(it.key.uppercase()) }?.let { return it.value }
        return null
    }

    private fun currencyFromBody(body: String): String? = normaliseCurrency(body)

    private fun maskAccount(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 4) "****" + digits.takeLast(4) else raw.trim()
    }
}

/**
 * A `label -> value` view of a plain-text email body.
 *
 * Handles the three shapes brokers actually emit:
 *   1. `Executed Price : 62.35`      (same line, separator)
 *   2. `Executed Price` / `62.35`    (label line, value on the next line — table columns)
 *   3. `Executed Price 62.35 HKD`    (same line, no separator)
 *
 * Matching is longest-label-first so "Executed Price" wins over "Price", and a label only
 * matches at a line start (after optional bullet punctuation) so prose cannot trigger it.
 */
internal class LabelledFields private constructor(
    private val lines: List<String>,
) {

    private val separators = charArrayOf(':', '\uFF1A', '=')

    /**
     * Returns how many characters of [line] the [label] consumed when both are compared
     * ignoring whitespace, or null when the line does not start with the label.
     */
    private fun matchLabelPrefix(line: String, label: String): Int? {
        var li = 0
        var ki = 0
        while (ki < label.length) {
            val lc = label[ki]
            if (lc.isWhitespace() || lc == ' ') {
                ki++
                continue
            }
            while (li < line.length && (line[li].isWhitespace() || line[li] == ' ')) li++
            if (li >= line.length) return null
            if (!line[li].equals(lc, ignoreCase = true)) return null
            li++
            ki++
        }
        return li
    }

    /** A leading `(股/單位)`-style unit annotation, before the label's separator. */
    private val UNIT_SUFFIX = Regex("""^[(\uFF08][^)\uFF09]{0,16}[)\uFF09]""")

    private fun startsWithNumber(text: String): Boolean =
        text.firstOrNull()?.let { it.isDigit() || it == '-' || it == '(' || it == '$' } == true

    fun text(labels: List<String>): String? {
        for (label in labels.sortedByDescending { it.length }) {
            val hit = find(label)
            if (hit != null && hit.isNotBlank()) return hit
        }
        return null
    }

    fun number(labels: List<String>): Double? {
        for (label in labels.sortedByDescending { it.length }) {
            val hit = find(label) ?: continue
            Numbers.parse(hit)?.let { return it }
        }
        return null
    }

    /** Sums every occurrence of every label — HSBC itemises half a dozen separate fees. */
    fun sumOf(labels: List<String>): Double {
        var total = 0.0
        val consumed = mutableSetOf<Int>()
        for (label in labels.sortedByDescending { it.length }) {
            for ((index, line) in lines.withIndex()) {
                if (index in consumed) continue
                val value = valueOnLine(line, label, index) ?: continue
                val amount = Numbers.parseAbs(value) ?: continue
                consumed += index
                total += amount
            }
        }
        return total
    }

    private fun find(label: String): String? {
        for ((index, line) in lines.withIndex()) {
            valueOnLine(line, label, index)?.let { return it }
        }
        return null
    }

    /**
     * Returns the value that belongs to [label] on [line], or null when the line
     * does not start with that label.
     */
    private fun valueOnLine(line: String, label: String, index: Int): String? {
        val trimmed = line.trimStart(' ', '\t', '-', '*', '·', '•', '|')

        // Match on a whitespace-free comparison so `股票名稱/ 股票編號` also matches
        // `股票名稱/股票編號`, then map the match length back onto the original line.
        val consumed = matchLabelPrefix(trimmed, label) ?: return null
        var rest = trimmed.substring(consumed)
        // The label must end here — otherwise "Price" would match "Priceless".
        if (rest.isNotEmpty() && rest[0].isLetterOrDigit() && rest[0].code < 128) return null

        // HSBC appends the unit to the label: `已成交數量(股/單位): 1`. Drop that suffix so
        // what remains is the value, not the unit.
        rest = UNIT_SUFFIX.replaceFirst(rest.trimStart(' ', '\t'), "")

        rest = rest.trimStart(' ', '\t', '|')
        val hadSeparator = rest.isNotEmpty() && rest[0] in separators
        if (hadSeparator) rest = rest.drop(1)
        rest = rest.trim().trim('|').trimEnd(*separators).trim()

        if (rest.isNotEmpty()) {
            // With no separator, the match is only trustworthy when a number follows
            // ("Executed Price 62.35"). Otherwise the label was merely a prefix of a
            // longer label — "Stock" sitting in front of "Stock Code : 0005" — and
            // matching it would capture the wrong value.
            if (!hadSeparator && !startsWithNumber(rest)) return null
            return rest
        }
        // Shape 2: the value sits on the following non-empty line (table column layout).
        return lines.drop(index + 1).firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.takeIf { next -> separators.none { next.endsWith(it) } }
    }

    companion object {
        fun from(body: String): LabelledFields {
            val expanded = body
                .lines()
                // A single physical line can hold several pairs: "Qty : 500 | Price : 62.35"
                .flatMap { line -> line.split(" | ", "|", "  ") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            return LabelledFields(expanded)
        }
    }
}
