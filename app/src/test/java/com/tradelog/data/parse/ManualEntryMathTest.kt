package com.tradelog.data.parse

import com.tradelog.data.db.Side
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the cash-effect arithmetic shared by mail import and hand entry.
 *
 * Both paths must agree, and the manual sheet shows a live preview of the same figure, so a
 * change here that is not mirrored in `ManualTradeSheet`'s preview would show the user one
 * number and store another.
 */
class ManualEntryMathTest {

    /** The formula both the repository and the sheet's preview apply. */
    private fun net(side: Side, quantity: Double, price: Double, fees: Double): Double {
        val gross = quantity * price
        return HsbcTradeParser.signedNet(side, gross + if (side == Side.BUY) fees else -fees)
    }

    @Test
    fun `a buy costs the gross plus fees`() {
        // 100 @ 12.50 = 1250, plus 8.00 of charges, all cash out.
        assertEquals(-1258.0, net(Side.BUY, 100.0, 12.50, 8.0), 0.0001)
    }

    @Test
    fun `a sell yields the gross minus fees`() {
        assertEquals(1242.0, net(Side.SELL, 100.0, 12.50, 8.0), 0.0001)
    }

    @Test
    fun `fees always reduce the proceeds, whichever side`() {
        val buyNoFee = net(Side.BUY, 10.0, 100.0, 0.0)
        val buyWithFee = net(Side.BUY, 10.0, 100.0, 5.0)
        // A buy with fees costs more, so it is further below zero.
        assertEquals(-1000.0, buyNoFee, 0.0001)
        assertEquals(-1005.0, buyWithFee, 0.0001)

        val sellNoFee = net(Side.SELL, 10.0, 100.0, 0.0)
        val sellWithFee = net(Side.SELL, 10.0, 100.0, 5.0)
        // A sell with fees brings in less.
        assertEquals(1000.0, sellNoFee, 0.0001)
        assertEquals(995.0, sellWithFee, 0.0001)
    }

    @Test
    fun `zero fees match the fee-free execution notice case`() {
        // Trade25 notices carry no charges, so this is the common path.
        assertEquals(-87.69, net(Side.BUY, 1.0, 87.69, 0.0), 0.0001)
        assertEquals(405.52, net(Side.SELL, 4.0, 101.38, 0.0), 0.0001)
    }

    @Test
    fun `fractional quantities are supported`() {
        // Fractional-share purchases are real on US brokers.
        assertEquals(-52.5, net(Side.BUY, 0.5, 105.0, 0.0), 0.0001)
    }
}
