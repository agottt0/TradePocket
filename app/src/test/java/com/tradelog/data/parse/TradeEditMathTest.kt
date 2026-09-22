package com.tradelog.data.parse

import com.tradelog.data.db.Side
import com.tradelog.data.db.Trade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the arithmetic a hand-edit applies to an existing trade.
 *
 * `TradeRepository.updateFields` recomputes gross and net from the edited fields rather than
 * keeping the old row's values, so these mirror that formula. The risk being guarded against is
 * an edited row whose stored cash effect no longer agrees with its own quantity and price — the
 * totals downstream would then be built on a figure nothing on screen explains.
 */
class TradeEditMathTest {

    private fun original(
        side: Side = Side.BUY,
        quantity: Double = 1.0,
        price: Double = 87.69,
        fees: Double = 0.0,
        needsReview: Boolean = false,
    ): Trade {
        val gross = quantity * price
        return Trade(
            id = 7,
            messageKey = "<abc@hsbc.com.hk>",
            tradeDate = 1_757_400_000_000L,
            symbol = "INTC",
            name = "INTEL CORPORATION",
            market = null,
            side = side,
            quantity = quantity,
            price = price,
            currency = "USD",
            gross = gross,
            fees = fees,
            netAmount = HsbcTradeParser.signedNet(side, gross + if (side == Side.BUY) fees else -fees),
            orderRef = "P396741",
            accountRef = null,
            needsReview = needsReview,
            importedAt = 1_757_000_000_000L,
        )
    }

    /** Exactly what the repository does, kept in one place so the test states the rule. */
    private fun edit(
        original: Trade,
        side: Side = original.side,
        quantity: Double = original.quantity,
        price: Double = original.price,
        fees: Double = original.fees,
        symbol: String = original.symbol,
        currency: String = original.currency,
        tradeDate: Long = original.tradeDate,
    ): Trade {
        val gross = quantity * price
        return original.copy(
            tradeDate = tradeDate,
            symbol = symbol.trim().uppercase(),
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
            needsReview = false,
        )
    }

    @Test
    fun `correcting the quantity rewrites gross and net together`() {
        // A partial fill imported as 1 share that was really 4.
        val edited = edit(original(quantity = 1.0, price = 87.69), quantity = 4.0)

        assertEquals(350.76, edited.gross, 0.001)
        assertEquals(-350.76, edited.netAmount, 0.001)
        // The invariant: net always equals the signed gross plus/minus fees.
        assertEquals(-(edited.quantity * edited.price + edited.fees), edited.netAmount, 0.001)
    }

    @Test
    fun `flipping the side flips the cash direction`() {
        val edited = edit(original(side = Side.BUY, quantity = 4.0, price = 101.38), side = Side.SELL)

        // A sale brings cash in, where the buy took it out.
        assertEquals(405.52, edited.netAmount, 0.001)
        assertTrue(edited.netAmount > 0)
    }

    @Test
    fun `adding a fee after the fact reduces what a sale brought in`() {
        val edited = edit(original(side = Side.SELL, quantity = 100.0, price = 12.50), fees = 8.0)

        assertEquals(1250.0, edited.gross, 0.001)
        assertEquals(1242.0, edited.netAmount, 0.001)
    }

    @Test
    fun `adding a fee after the fact increases what a purchase cost`() {
        val edited = edit(original(side = Side.BUY, quantity = 100.0, price = 12.50), fees = 8.0)

        assertEquals(-1258.0, edited.netAmount, 0.001)
    }

    @Test
    fun `editing clears the review flag`() {
        val edited = edit(original(needsReview = true), price = 87.70)

        // Editing is the human confirmation the flag was asking for.
        assertFalse(edited.needsReview)
    }

    @Test
    fun `the dedupe key and import time survive an edit`() {
        val before = original()
        val edited = edit(before, quantity = 9.0)

        // Keeping the key is what stops a later full rescan from re-importing the uncorrected
        // original alongside the correction.
        assertEquals(before.messageKey, edited.messageKey)
        assertEquals(before.id, edited.id)
        // The trade was imported when it was imported; editing it later does not change that.
        assertEquals(before.importedAt, edited.importedAt)
        assertEquals(before.orderRef, edited.orderRef)
    }

    @Test
    fun `symbol and currency are normalised the way import normalises them`() {
        val edited = edit(original(), symbol = " intc ", currency = "usd")

        assertEquals("INTC", edited.symbol)
        assertEquals("USD", edited.currency)
    }

    @Test
    fun `an edit that changes nothing leaves the figures identical`() {
        val before = original(quantity = 4.0, price = 101.38, fees = 1.5)
        val edited = edit(before)

        assertEquals(before.gross, edited.gross, 1e-9)
        assertEquals(before.netAmount, edited.netAmount, 1e-9)
        assertEquals(before.tradeDate, edited.tradeDate)
    }
}
