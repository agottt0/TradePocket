package com.tradelog.data.mail

import java.util.Properties
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeUtility
import javax.mail.search.AndTerm
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm
import javax.mail.search.SearchTerm

data class ImapConfig(
    val host: String,
    val port: Int = 993,
    val useSsl: Boolean = true,
    val username: String,
    /** App-specific password, never the account password. */
    val password: String,
    val folder: String = "INBOX",
)

/**
 * Read-only IMAP fetch over SSL.
 *
 * Deliberately never sets \Deleted, never expunges, and never moves messages — the app
 * only reads. It does not mark messages as seen either (`setPeek`-equivalent is achieved
 * by opening the folder READ_ONLY), so using the app does not disturb your inbox.
 */
class ImapMailClient {

    /**
     * Fetches messages received at or after [sinceMillis], newest first, capped at [limit].
     * Bodies are flattened to plain text.
     */
    fun fetch(config: ImapConfig, sinceMillis: Long, limit: Int = 200): List<EmailMessage> {
        require(config.useSsl) { "Plaintext IMAP is not supported: credentials would cross the network unencrypted." }

        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", config.host)
            put("mail.imaps.port", config.port.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.ssl.protocols", "TLSv1.2 TLSv1.3")
            put("mail.imaps.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
            put("mail.imaps.timeout", READ_TIMEOUT_MS.toString())
            put("mail.imaps.writetimeout", READ_TIMEOUT_MS.toString())
            put("mail.imaps.partialfetch", "false")
            // Some providers advertise mechanisms the Android port cannot do; keep it simple.
            put("mail.imaps.auth.plain.disable", "false")
        }

        val session = Session.getInstance(props)
        val store = session.getStore("imaps")
        store.connect(config.host, config.port, config.username, config.password)
        try {
            val folder = store.getFolder(config.folder)
            if (!folder.exists()) error("邮箱文件夹不存在: ${config.folder}")
            folder.open(Folder.READ_ONLY)
            try {
                val since = java.util.Date(sinceMillis)
                val term: SearchTerm = AndTerm(
                    ReceivedDateTerm(ComparisonTerm.GE, since),
                    // Exclude nothing else server-side; sender filtering happens locally
                    // because IMAP FROM matching is inconsistent across providers.
                    ReceivedDateTerm(ComparisonTerm.LE, java.util.Date(Long.MAX_VALUE / 2)),
                )
                val found = try {
                    folder.search(term)
                } catch (_: javax.mail.MessagingException) {
                    // Server refused the search; fall back to a tail scan of the folder.
                    val count = folder.messageCount
                    val from = (count - limit + 1).coerceAtLeast(1)
                    if (count == 0) emptyArray() else folder.getMessages(from, count)
                }

                return found
                    .sortedByDescending { runCatching { it.receivedDate?.time ?: 0L }.getOrDefault(0L) }
                    .take(limit)
                    .mapNotNull { message -> runCatching { toEmailMessage(message) }.getOrNull() }
            } finally {
                // false = do not expunge.
                runCatching { folder.close(false) }
            }
        } finally {
            runCatching { store.close() }
        }
    }

    /** Verifies host/credentials without importing anything. Returns null on success, else a message. */
    fun testConnection(config: ImapConfig): String? = try {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
            put("mail.imaps.timeout", READ_TIMEOUT_MS.toString())
        }
        val store = Session.getInstance(props).getStore("imaps")
        store.connect(config.host, config.port, config.username, config.password)
        val folder = store.getFolder(config.folder)
        val exists = folder.exists()
        runCatching { store.close() }
        if (exists) null else "登录成功，但找不到文件夹 ${config.folder}"
    } catch (e: javax.mail.AuthenticationFailedException) {
        "认证失败：请确认使用的是应用专用密码，而不是登录密码"
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }

    private fun toEmailMessage(message: Message): EmailMessage {
        val from = (message.from?.firstOrNull() as? InternetAddress)?.address
            ?: message.from?.firstOrNull()?.toString()
            ?: ""
        val subject = message.subject?.let { decodeHeader(it) } ?: ""
        val messageId = (message as? javax.mail.internet.MimeMessage)?.messageID
        val sentAt = message.receivedDate?.time ?: message.sentDate?.time ?: System.currentTimeMillis()
        return EmailMessage(
            messageId = messageId,
            from = from,
            subject = subject,
            body = extractText(message),
            sentAt = sentAt,
        )
    }

    private fun decodeHeader(value: String): String =
        runCatching { MimeUtility.decodeText(value) }.getOrDefault(value)

    /**
     * Walks the MIME tree and returns the best plain-text rendering.
     * Prefers a real text/plain part; falls back to the HTML part stripped of tags.
     */
    private fun extractText(part: Part): String {
        val plain = StringBuilder()
        val html = StringBuilder()
        collect(part, plain, html, depth = 0)
        val plainText = plain.toString().trim()
        // HSBC's plain-text alternative is often a "please view in HTML" stub, so prefer
        // whichever part actually carries content.
        val htmlText = if (html.isNotEmpty()) HtmlText.toPlainText(html.toString()) else ""
        return when {
            plainText.length >= MIN_USEFUL_BODY -> plainText
            htmlText.isNotEmpty() -> htmlText
            else -> plainText
        }
    }

    private fun collect(part: Part, plain: StringBuilder, html: StringBuilder, depth: Int) {
        if (depth > MAX_MIME_DEPTH) return
        val content = runCatching { part.content }.getOrNull()
        when {
            content is Multipart -> {
                for (i in 0 until content.count) {
                    val child = runCatching { content.getBodyPart(i) }.getOrNull() ?: continue
                    collect(child, plain, html, depth + 1)
                }
            }

            part.isMimeType("text/plain") -> {
                (content as? String)?.let { plain.append(it).append('\n') }
            }

            part.isMimeType("text/html") -> {
                (content as? String)?.let { html.append(it).append('\n') }
            }
            // Attachments are ignored on purpose: PDF contract notes would need a PDF
            // parser, and the email body already carries the same figures.
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_MIME_DEPTH = 8
        const val MIN_USEFUL_BODY = 200
    }
}

