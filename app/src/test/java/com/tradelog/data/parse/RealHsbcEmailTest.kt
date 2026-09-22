package com.tradelog.data.parse

import com.tradelog.data.db.Side
import com.tradelog.data.mail.EmailMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests against real HSBC HK / Easy Invest execution notices, kept verbatim
 * in `src/test/resources/emails/`.
 *
 * These are the authority on the parser's behaviour: if HSBC changes a template, add the
 * new sample here rather than adjusting the parser to a guess.
 */
class RealHsbcEmailTest {

    private fun load(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("emails/$name")) {
            "missing test resource emails/$name"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun message(subject: String, file: String) = EmailMessage(
        messageId = "<$file@hsbc.com.hk>",
        from = "dfv.enquiry@hsbc.com.hk",
        subject = subject,
        body = load(file),
        sentAt = 1_757_400_000_000L,
    )

    @Test
    fun `parses a real buy execution notice`() {
        val result = HsbcTradeParser.parse(
            message(
                subject = "全部執行: 買入INTC: INTEL CORPORATION的股/單位(交易編號: P396741)",
                file = "easyinvest_buy_zh.txt",
            ),
        )

        assertTrue("expected Success but was $result", result is ParseResult.Success)
        val trade = (result as ParseResult.Success).trade

        assertEquals("INTC", trade.symbol)
        assertEquals("INTEL CORPORATION", trade.name)
        assertEquals(Side.BUY, trade.side)
        assertEquals(1.0, trade.quantity, 0.0001)
        assertEquals(87.69, trade.price, 0.0001)
        // The currency is glued to the price as "USD87.69".
        assertEquals("USD", trade.currency)
        assertEquals(87.69, trade.gross, 0.01)
        // This template itemises no charges, so fees are zero rather than guessed.
        assertEquals(0.0, trade.fees, 0.0001)
        // A buy is cash out.
        assertEquals(-87.69, trade.netAmount, 0.01)
        assertEquals("P396741", trade.orderRef)
        assertFalse(trade.needsReview)
    }

    @Test
    fun `parses a real sell execution notice`() {
        val result = HsbcTradeParser.parse(
            message(
                subject = "全部執行: 沽出 INTC: INTEL CORPORATION 的股/單位(交易編號: S406644)",
                file = "easyinvest_sell_zh.txt",
            ),
        )

        assertTrue("expected Success but was $result", result is ParseResult.Success)
        val trade = (result as ParseResult.Success).trade

        assertEquals("INTC", trade.symbol)
        assertEquals("INTEL CORPORATION", trade.name)
        // 沽出 is HSBC HK's word for a sale.
        assertEquals(Side.SELL, trade.side)
        assertEquals(4.0, trade.quantity, 0.0001)
        assertEquals(101.38, trade.price, 0.0001)
        assertEquals("USD", trade.currency)
        assertEquals(405.52, trade.gross, 0.01)
        // A sell is cash in.
        assertEquals(405.52, trade.netAmount, 0.01)
        assertEquals("S406644", trade.orderRef)
        assertFalse(trade.needsReview)
    }

    @Test
    fun `takes the per-fill quantity, not the cumulative or remaining one`() {
        // 已成交數量 is 4, 共成交數量 is 4 and 餘下數量 is 0 in the sell sample. On a
        // partially-filled order these diverge, and only 已成交數量 belongs in the books.
        val body = load("easyinvest_sell_zh.txt")
            .replace("• 共成交數量(股/單位): 4", "• 共成交數量(股/單位): 10")
            .replace("• 餘下數量(股/單位): 0", "• 餘下數量(股/單位): 6")

        val result = HsbcTradeParser.parse(
            EmailMessage(
                messageId = "<partial@hsbc.com.hk>",
                from = "dfv.enquiry@hsbc.com.hk",
                subject = "部分執行: 沽出 INTC",
                body = body,
                sentAt = 1_757_400_000_000L,
            ),
        )

        val trade = (result as ParseResult.Success).trade
        assertEquals(4.0, trade.quantity, 0.0001)
    }

    @Test
    fun `rejects a notice whose status is not an execution`() {
        val body = load("easyinvest_buy_zh.txt")
            .replace("• 交易狀況: 全部執行", "• 交易狀況: 已取消")

        val result = HsbcTradeParser.parse(
            EmailMessage(
                messageId = "<cancelled@hsbc.com.hk>",
                from = "dfv.enquiry@hsbc.com.hk",
                // The subject still reads like an execution; the status field must win.
                subject = "全部執行: 買入INTC",
                body = body,
                sentAt = 1_757_400_000_000L,
            ),
        )

        assertTrue("expected NotATrade but was $result", result is ParseResult.NotATrade)
    }

    @Test
    fun `splits a combined name and code field`() {
        val (name, symbol) = HsbcTradeParser.splitNameAndSymbol("INTEL CORPORATION (INTC)")
            .let { it.name to it.symbol }
        assertEquals("INTEL CORPORATION", name)
        assertEquals("INTC", symbol)

        val hk = HsbcTradeParser.splitNameAndSymbol("匯豐控股 (0005)")
        assertEquals("匯豐控股", hk.name)
        assertEquals("0005", hk.symbol)

        // No code present: keep the whole thing as a name rather than inventing a code.
        val nameOnly = HsbcTradeParser.splitNameAndSymbol("INTEL CORPORATION")
        assertEquals("INTEL CORPORATION", nameOnly.name)
        assertEquals(null, nameOnly.symbol)
    }

    @Test
    fun `recognises a currency glued to the price`() {
        assertEquals("USD", HsbcTradeParser.normaliseCurrency("USD87.69"))
        assertEquals("HKD", HsbcTradeParser.normaliseCurrency("HKD62.35"))
        assertEquals("USD", HsbcTradeParser.normaliseCurrency("USD 87.69"))
    }

    @Test
    fun `sell verb 沽出 parses as a sale`() {
        assertEquals(Side.SELL, HsbcTradeParser.parseSide("沽出"))
        assertEquals(Side.SELL, HsbcTradeParser.parseSide("沽"))
        assertEquals(Side.BUY, HsbcTradeParser.parseSide("買入"))
    }

    @Test
    fun `the same notice imported twice yields one stable key`() {
        val a = message("全部執行: 買入INTC", "easyinvest_buy_zh.txt")
        val b = message("全部執行: 買入INTC", "easyinvest_buy_zh.txt")
        assertEquals(a.stableKey(), b.stableKey())
    }
}
