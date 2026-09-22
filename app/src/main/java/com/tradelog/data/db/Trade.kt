package com.tradelog.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Side { BUY, SELL }

/**
 * One executed securities trade, parsed out of an HSBC trade-confirmation email.
 *
 * [messageKey] is the de-duplication key: the IMAP Message-ID when the server gives us one,
 * otherwise a hash of the message's own stable fields. A unique index on it means re-scanning
 * the same mailbox is idempotent.
 */
@Entity(
    tableName = "trades",
    indices = [
        Index(value = ["messageKey"], unique = true),
        Index(value = ["tradeDate"]),
        Index(value = ["symbol"]),
    ],
)
data class Trade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageKey: String,
    /** Epoch millis of the execution (falls back to the email date when the body has no time). */
    val tradeDate: Long,
    val symbol: String,
    val name: String?,
    val market: String?,
    val side: Side,
    val quantity: Double,
    val price: Double,
    /** Trade currency, ISO-4217, e.g. HKD / USD. */
    val currency: String,
    /** Gross consideration = quantity * price, as stated by the broker when available. */
    val gross: Double,
    /** All commissions, levies, stamp duty and platform fees, summed and non-negative. */
    val fees: Double,
    /**
     * Signed cash effect in [currency]: negative for a BUY (cash out), positive for a SELL.
     * Fees always reduce it.
     */
    val netAmount: Double,
    val orderRef: String?,
    val accountRef: String?,
    /** True when the parser could not fill every field and the row needs a human look. */
    val needsReview: Boolean = false,
    val rawSubject: String? = null,
    val importedAt: Long = System.currentTimeMillis(),
)
