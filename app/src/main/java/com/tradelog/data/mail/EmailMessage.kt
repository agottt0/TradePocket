package com.tradelog.data.mail

/**
 * A mail message flattened to just what the parser needs, so parsing is testable
 * without an IMAP server or any Android dependency.
 */
data class EmailMessage(
    val messageId: String?,
    val from: String,
    val subject: String,
    /** Body already converted to plain text (HTML stripped, entities decoded). */
    val body: String,
    val sentAt: Long,
) {
    /** Stable de-dup key: prefer the server's Message-ID, else hash the message's own content. */
    fun stableKey(): String {
        val id = messageId?.trim()?.takeIf { it.isNotEmpty() }
        if (id != null) return id
        val basis = "$from|$subject|$sentAt|${body.length}|${body.take(512)}"
        return "sha:" + basis.hashCode().toString() + ":" + basis.length
    }
}
