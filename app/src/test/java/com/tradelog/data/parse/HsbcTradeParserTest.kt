package com.tradelog.data.parse

import com.tradelog.data.db.Side
import com.tradelog.data.mail.EmailMessage
import com.tradelog.data.mail.HtmlText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HsbcTradeParserTest {

    private fun message(subject: String, body: String, from: String = "no-reply@hsbc.com.hk") =
        EmailMessage(
            messageId = "<test-${subject.hashCode()}@hsbc.com.hk>",
            from = from,
            subject = subject,
            body = body,
            sentAt = 1_700_000_000_000L,
        )

    // ---- Unit-level rules ---------------------------------------------------------------

    @Test
    fun `sign convention is buy negative sell positive`() {
        assertEquals(-100.0, HsbcTradeParser.signedNet(Side.BUY, 100.0), 0.0)
        assertEquals(-100.0, HsbcTradeParser.signedNet(Side.BUY, -100.0), 0.0)
        assertEquals(100.0, HsbcTradeParser.signedNet(Side.SELL, 100.0), 0.0)
        assertEquals(100.0, HsbcTradeParser.signedNet(Side.SELL, -100.0), 0.0)
    }

    @Test
    fun `side parsing does not confuse sold with the single-letter s`() {
        assertEquals(Side.SELL, HsbcTradeParser.parseSide("Sold"))
        assertEquals(Side.BUY, HsbcTradeParser.parseSide("Bought"))
        assertEquals(Side.BUY, HsbcTradeParser.parseSide("B"))
        assertEquals(Side.SELL, HsbcTradeParser.parseSide("S"))
        assertNull(HsbcTradeParser.parseSide("Executed"))
        assertNull(HsbcTradeParser.parseSide(null))
    }

    @Test
    fun `hong kong codes are zero padded to four digits`() {
        assertEquals("0005", HsbcTradeParser.normaliseSymbol("5"))
        assertEquals("0700", HsbcTradeParser.normaliseSymbol("00700"))
        assertEquals("0005", HsbcTradeParser.normaliseSymbol("0005 HSBC HOLDINGS"))
        assertEquals("AAPL", HsbcTradeParser.normaliseSymbol("AAPL"))
        assertNull(HsbcTradeParser.normaliseSymbol("   "))
    }

    @Test
    fun `currency aliases normalise to iso codes`() {
        assertEquals("CNY", HsbcTradeParser.normaliseCurrency("RMB"))
        assertEquals("CNY", HsbcTradeParser.normaliseCurrency("CNH"))
        assertEquals("HKD", HsbcTradeParser.normaliseCurrency("HK$"))
        assertEquals("USD", HsbcTradeParser.normaliseCurrency("US$ 1,000"))
        assertNull(HsbcTradeParser.normaliseCurrency("no currency here"))
    }

    @Test
    fun `sender recognition matches hsbc domains only`() {
        assertTrue(HsbcTradeParser.looksLikeHsbc(message("x", "y", from = "alerts@hsbc.com.hk")))
        assertTrue(!HsbcTradeParser.looksLikeHsbc(message("x", "y", from = "news@example.com")))
    }

    @Test
    fun `message key falls back to a content hash without a message id`() {
        val a = EmailMessage(null, "a@hsbc.com", "Trade Confirmation", "body", 1L)
        val b = EmailMessage(null, "a@hsbc.com", "Trade Confirmation", "body", 1L)
        val c = EmailMessage(null, "a@hsbc.com", "Trade Confirmation", "other body", 1L)
        assertEquals(a.stableKey(), b.stableKey())
        assertTrue(a.stableKey() != c.stableKey())
        assertNotNull(a.stableKey())
    }
}

class NumbersTest {

    @Test
    fun `parses grouped decimals`() {
        assertEquals(24_940.0, Numbers.parse("24,940.00")!!, 0.001)
        assertEquals(62.35, Numbers.parse("HKD 62.35")!!, 0.001)
        assertEquals(1000.0, Numbers.parse("1,000")!!, 0.001)
    }

    @Test
    fun `treats parentheses and leading minus as negative`() {
        assertEquals(-95.33, Numbers.parse("(95.33)")!!, 0.001)
        assertEquals(-95.33, Numbers.parse("-95.33")!!, 0.001)
        assertEquals(95.33, Numbers.parseAbs("(95.33)")!!, 0.001)
    }

    @Test
    fun `returns null when there is no number`() {
        assertNull(Numbers.parse("N/A"))
        assertNull(Numbers.parse(""))
        assertNull(Numbers.parse(null))
    }
}

class DatesTest {

    @Test
    fun `parses the broker date formats`() {
        assertNotNull(Dates.parse("05/03/2024 10:32:11"))
        assertNotNull(Dates.parse("05-Mar-2024"))
        assertNotNull(Dates.parse("2024-03-05 10:32"))
        assertNotNull(Dates.parse("2024年3月5日"))
    }

    @Test
    fun `returns null for junk`() {
        assertNull(Dates.parse("not a date"))
        assertNull(Dates.parse(""))
        assertNull(Dates.parse(null))
    }

    @Test
    fun `day month order is not swapped`() {
        // 05/03/2024 is 5 March, not 3 May.
        val fiveMarch = Dates.parse("05/03/2024")!!
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Hong_Kong"))
        cal.timeInMillis = fiveMarch
        assertEquals(5, cal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(java.util.Calendar.MARCH, cal.get(java.util.Calendar.MONTH))
    }
}

class HtmlTextTest {

    @Test
    fun `strips tags and decodes entities`() {
        val out = HtmlText.toPlainText("<p>Fee &amp; levy&nbsp;: 1.25</p>")
        assertTrue(out.contains("Fee & levy"))
        assertTrue(!out.contains("<p>"))
    }

    @Test
    fun `drops script and style content`() {
        val out = HtmlText.toPlainText("<style>.a{color:red}</style><script>var x=1</script><p>Price : 62.35</p>")
        assertTrue(!out.contains("color:red"))
        assertTrue(!out.contains("var x"))
        assertTrue(out.contains("Price"))
    }

    @Test
    fun `keeps table cells separated`() {
        val out = HtmlText.toPlainText("<tr><td>Stock Code</td><td>0005</td></tr>")
        assertTrue("unexpected: $out", out.contains("Stock Code") && out.contains("0005"))
    }
}
