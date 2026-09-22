package com.tradelog.data.repo

import android.content.Context
import com.tradelog.data.db.Side
import com.tradelog.data.db.Trade
import com.tradelog.data.db.TradeDao
import com.tradelog.data.db.TradeDatabase
import com.tradelog.data.mail.EmailMessage
import com.tradelog.data.mail.ImapMailClient
import com.tradelog.data.parse.HsbcTradeParser
import com.tradelog.data.parse.ParseResult
import com.tradelog.data.prefs.MailSettings
import com.tradelog.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

data class SyncOutcome(
    val scanned: Int = 0,
    val imported: Int = 0,
    val needsReview: Int = 0,
    val duplicates: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null

    fun summary(): String = when {
        error != null -> "同步失败：$error"
        imported == 0 -> "扫描 $scanned 封，无新交易"
        needsReview > 0 -> "新增 $imported 笔（$needsReview 笔待核对）"
        else -> "新增 $imported 笔交易"
    }
}

class TradeRepository(
    private val dao: TradeDao,
    private val settings: SettingsStore,
    private val mail: ImapMailClient = ImapMailClient(),
) {

    fun observeTrades(): Flow<List<Trade>> = dao.observeAll()
    fun observeRecent(limit: Int = 8): Flow<List<Trade>> = dao.observeRecent(limit)
    fun observeCount(): Flow<Int> = dao.observeCount()
    fun observeReviewCount(): Flow<Int> = dao.observeReviewCount()
    fun observeSettings(): Flow<MailSettings> = settings.observe()

    /**
     * Inserts a hand-entered trade.
     *
     * The [Trade.messageKey] is minted locally with a `manual:` prefix and a random suffix, so a
     * manual row can never collide with an imported email's key and two identical hand entries
     * (a genuine case: same stock, price and minute, split across two fills) both survive the
     * unique index.
     *
     * Fees default to 0 because Trade25 execution notices carry none; when the caller does pass a
     * fee it reduces the cash effect in the same direction the parser uses.
     */
    suspend fun addManual(
        tradeDate: Long,
        symbol: String,
        name: String?,
        side: Side,
        quantity: Double,
        price: Double,
        currency: String,
        fees: Double = 0.0,
        note: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        val gross = quantity * price
        val trade = Trade(
            messageKey = "manual:" + UUID.randomUUID(),
            tradeDate = tradeDate,
            symbol = symbol.trim().uppercase(),
            name = name?.trim()?.ifEmpty { null },
            market = null,
            side = side,
            quantity = quantity,
            price = price,
            currency = currency.trim().uppercase(),
            gross = gross,
            fees = fees,
            netAmount = HsbcTradeParser.signedNet(side, gross + if (side == Side.BUY) fees else -fees),
            orderRef = null,
            accountRef = null,
            // Hand-entered rows are authoritative by definition: the human just typed them.
            needsReview = false,
            rawSubject = note?.trim()?.ifEmpty { null } ?: "手动录入",
        )
        dao.insert(trade)
    }

    suspend fun update(trade: Trade) = withContext(Dispatchers.IO) { dao.update(trade) }

    /**
     * Rewrites the economic fields of an existing trade and re-derives everything computed from
     * them.
     *
     * Gross and net are recomputed here rather than accepted from the caller, so a correction
     * goes through exactly the same arithmetic as an import or a hand entry ([HsbcTradeParser.signedNet]) —
     * one formula, three entry points, no chance of an edited row carrying a cash effect that
     * disagrees with its own quantity and price.
     *
     * The row's identity is preserved deliberately: [Trade.messageKey] keeps a rescan from
     * re-importing the uncorrected original as a second row, and [Trade.importedAt] records when
     * the trade first arrived, which an edit does not change.
     */
    suspend fun updateFields(
        original: Trade,
        tradeDate: Long,
        symbol: String,
        name: String?,
        side: Side,
        quantity: Double,
        price: Double,
        currency: String,
        fees: Double,
    ) = withContext(Dispatchers.IO) {
        val gross = quantity * price
        dao.update(
            original.copy(
                tradeDate = tradeDate,
                symbol = symbol.trim().uppercase(),
                name = name?.trim()?.ifEmpty { null },
                side = side,
                quantity = quantity,
                price = price,
                currency = currency.trim().uppercase(),
                gross = gross,
                fees = fees,
                netAmount = HsbcTradeParser.signedNet(
                    side,
                    gross + if (side == Side.BUY) fees else -fees,
                ),
                // A human has now reviewed this row by editing it.
                needsReview = false,
            ),
        )
    }
    suspend fun delete(trade: Trade) = withContext(Dispatchers.IO) { dao.delete(trade) }
    suspend fun byId(id: Long): Trade? = withContext(Dispatchers.IO) { dao.byId(id) }

    suspend fun testConnection(
        host: String,
        port: Int,
        username: String,
        password: String?,
        folder: String,
    ): String? = withContext(Dispatchers.IO) {
        val config = settings.draftConfig(host, port, username, password, folder)
            ?: return@withContext "请填写服务器、账号和应用专用密码"
        mail.testConnection(config)
    }

    /**
     * Fetches mail, parses trade confirmations and inserts the new ones.
     *
     * Incremental by default: scans back to the last successful sync (minus a one-day
     * overlap, since a confirmation can arrive after the sync that should have caught it).
     * [fullRescan] instead reaches back `lookbackDays`; de-duplication by message key makes
     * a rescan harmless.
     */
    suspend fun sync(fullRescan: Boolean = false): SyncOutcome = withContext(Dispatchers.IO) {
        val current = settings.read()
        val config = settings.imapConfig()
            ?: return@withContext SyncOutcome(error = "邮箱未配置").also {
                settings.recordSync(System.currentTimeMillis(), it.summary(), advanceWatermark = false)
            }

        val since = if (fullRescan || current.lastSyncAt == 0L) {
            System.currentTimeMillis() - current.lookbackDays * DAY_MS
        } else {
            current.lastSyncAt - OVERLAP_MS
        }

        val messages = try {
            mail.fetch(config, since)
        } catch (e: Exception) {
            val outcome = SyncOutcome(error = friendlyError(e))
            settings.recordSync(System.currentTimeMillis(), outcome.summary(), advanceWatermark = false)
            return@withContext outcome
        }

        val senderFilters = current.senderFilter
            .split(',', ';', ' ')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        var imported = 0
        var review = 0
        var duplicates = 0
        var skipped = 0

        for (message in messages) {
            if (!matchesSender(message, senderFilters)) {
                skipped++
                continue
            }
            when (val result = HsbcTradeParser.parse(message)) {
                is ParseResult.NotATrade -> skipped++

                is ParseResult.Success -> {
                    if (dao.insert(result.trade) == -1L) duplicates++ else imported++
                }

                is ParseResult.Partial -> {
                    if (dao.insert(result.trade) == -1L) {
                        duplicates++
                    } else {
                        imported++
                        review++
                    }
                }
            }
        }

        val outcome = SyncOutcome(
            scanned = messages.size,
            imported = imported,
            needsReview = review,
            duplicates = duplicates,
            skipped = skipped,
        )
        // Only a clean run advances the watermark, so a failure re-scans the same window.
        settings.recordSync(System.currentTimeMillis(), outcome.summary(), advanceWatermark = true)
        outcome
    }

    private fun matchesSender(message: EmailMessage, filters: List<String>): Boolean =
        if (filters.isEmpty()) HsbcTradeParser.looksLikeHsbc(message)
        else filters.any { message.from.lowercase().contains(it) }

    private fun friendlyError(e: Exception): String = when (e) {
        is javax.mail.AuthenticationFailedException -> "认证失败，请检查应用专用密码"
        is javax.mail.MessagingException -> "无法连接邮箱：${e.message ?: "网络错误"}"
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val OVERLAP_MS = DAY_MS

        @Volatile
        private var instance: TradeRepository? = null

        fun get(context: Context): TradeRepository =
            instance ?: synchronized(this) {
                instance ?: TradeRepository(
                    dao = TradeDatabase.get(context).tradeDao(),
                    settings = SettingsStore.get(context),
                ).also { instance = it }
            }
    }
}
