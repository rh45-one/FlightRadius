package com.flightradius.app.data.aircraftdb

import java.io.BufferedReader
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvParserTest {

    // Real lines from aircraft-database-complete-2025-08.csv.
    private val HELI =
        "'004000','2016-09-03 17:19:23',0,0,,'',Zimbabwe,'',,,H2T,'',TAI,Aerospatiale," +
            "Aerospatiale Cougar AS.532 UL,0,'','','Aviation Legere De L''armee De Terre'," +
            "FRENCH ARMY,'',FMY,French Army,'',,,'2324','','2324','',AS32,0"
    private val HTML =
        "'40064a','1970-01-01 01:00:00',0,0,,'',United Kingdom," +
            "'2 x PRATT &amp; WHITNEY CANADA PW126&nbsp;&nbsp;( HAMILTON STANDARD 6/5500/F-1 )<br>'," +
            ",,L2T,'',JETSTREAM,British Aerospace (commercial Aircraft) Ltd,BAE.ATP,0,'','','','','','','','',," +
            "'2013-09-12',G-BTPJ,'','2016','',ATP,0"
    private val UNQUOTED_ID =
        "a1a298,'2017-09-15 00:10:16',0,0,,'',United States,'',,,T2T,'',BELL-BOEING,Bell/boeing," +
            "MV-22B Osprey,0,'','','','','','',United States Marine Corps,'',,,N204TR,'',D0043,'',V22,0"

    @Test
    fun `single quoted record with escaped quote and unquoted fields`() {
        val f = CsvParser.parseRecord(HELI, '\'')!!
        assertEquals(32, f.size)
        assertEquals("004000", f[0])
        assertEquals("Zimbabwe", f[6])
        assertEquals("", f[7])
        assertEquals("H2T", f[10])
        assertEquals("Aerospatiale Cougar AS.532 UL", f[14])
        assertEquals("Aviation Legere De L'armee De Terre", f[18])
        assertEquals("AS32", f[30])
        assertEquals("0", f[31])
    }

    @Test
    fun `html entities and commas stay inside quoted fields`() {
        val f = CsvParser.parseRecord(HTML, '\'')!!
        assertEquals(32, f.size)
        assertEquals(
            "2 x PRATT &amp; WHITNEY CANADA PW126&nbsp;&nbsp;( HAMILTON STANDARD 6/5500/F-1 )<br>",
            f[7])
        assertEquals("G-BTPJ", f[26])
    }

    @Test
    fun `unquoted first field and trailing values`() {
        val f = CsvParser.parseRecord(UNQUOTED_ID, '\'')!!
        assertEquals("a1a298", f[0])
        assertEquals("N204TR", f[26])
    }

    @Test
    fun `trailing empty field and embedded commas`() {
        assertEquals(listOf("a", "b", ""), CsvParser.parseRecord("a,b,", '\''))
        assertEquals(listOf("a,b", "c"), CsvParser.parseRecord("'a,b',c", '\''))
        assertEquals(listOf("", "", ""), CsvParser.parseRecord("'','',''", '\''))
        assertEquals(listOf("it's"), CsvParser.parseRecord("it's", '\''))
    }

    @Test
    fun `double quoted legacy format`() {
        val f = CsvParser.parseRecord(
            "\"4ca123\",\"EC-KZX\",\"\",\"Say \"\"hi\"\", ok\",\"EC35\"", '"')!!
        assertEquals(listOf("4ca123", "EC-KZX", "", "Say \"hi\", ok", "EC35"), f)
    }

    @Test
    fun `open quote is reported incomplete`() {
        assertNull(CsvParser.parseRecord("'abc,def", '\''))
    }

    @Test
    fun `quote detection from header`() {
        assertEquals('\'', CsvParser.detectQuote("'icao24','timestamp'"))
        assertEquals('"', CsvParser.detectQuote("\"icao24\",\"registration\""))
        assertEquals('\'', CsvParser.detectQuote("icao24,registration"))
    }

    @Test
    fun `record reader handles crlf blank lines and multi line fields`() {
        val text = "'a','b'\r\n'multi\nline','x'\r\n'c','d'"
        val rr = CsvParser.RecordReader(BufferedReader(StringReader(text)), '\'')
        assertEquals(listOf("a", "b"), rr.next())
        assertEquals(listOf("multi\nline", "x"), rr.next())
        assertEquals(listOf("c", "d"), rr.next())
        assertNull(rr.next())
    }

    @Test
    fun `a stray quote cannot swallow the rest of the file`() {
        val lines = (1..40).joinToString("\n") { "'r$it','v'" }
        // An unterminated quote at the very start of the data.
        val text = "'broken,start\n$lines"
        val rr = CsvParser.RecordReader(BufferedReader(StringReader(text)), '\'')
        var count = 0
        while (rr.next() != null) count++
        assertTrue("records: $count", count >= 35)
    }
}
