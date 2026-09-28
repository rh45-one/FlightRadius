package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentifiersTest {

    @Test
    fun `validates callsigns`() {
        assertTrue(Identifiers.isValidCallsign("DLH123"))
        assertTrue(Identifiers.isValidCallsign("ab"))           // normalizes
        assertFalse(Identifiers.isValidCallsign("A"))           // too short
        assertFalse(Identifiers.isValidCallsign("ABCDEFGHI"))   // too long
        assertFalse(Identifiers.isValidCallsign("DLH-123"))     // bad char
    }

    @Test
    fun `validates icao24`() {
        assertTrue(Identifiers.isValidIcao24("abc123"))
        assertTrue(Identifiers.isValidIcao24("ABCDEF"))         // case-insens
        assertFalse(Identifiers.isValidIcao24("abc12"))         // 5 chars
        assertFalse(Identifiers.isValidIcao24("abcdefg"))       // 'g' not hex
        assertFalse(Identifiers.isValidIcao24("abcd1234"))      // 8 chars
    }

    @Test
    fun `normalize uppercases callsign and lowercases icao`() {
        assertEquals("DLH123", Identifiers.normalizeCallsign(" dlh123 "))
        assertEquals("abcdef", Identifiers.normalizeIcao24(" AbCdEf "))
    }

    @Test
    fun `ambiguous 6-hex defaults to callsign`() {
        assertEquals(IdentifierType.CALLSIGN, Identifiers.classify("ABCDEF"))
        assertEquals(
            IdentifierType.ICAO24,
            Identifiers.classify("ABCDEF", IdentifierType.ICAO24)
        )
    }

    @Test
    fun `explicit type still requires validity`() {
        assertNull(Identifiers.classify("ZZZZZZZZ", IdentifierType.ICAO24))
        assertNull(Identifiers.classify("not-valid!", IdentifierType.CALLSIGN))
    }

    @Test
    fun `classify returns null for garbage`() {
        assertNull(Identifiers.classify("hello world!"))
        assertNull(Identifiers.classify(""))
    }

    @Test
    fun `bulk parse splits dedupes and normalizes`() {
        val parsed = Identifiers.parseBulk("dlh123, AAA100\nbbb200  DLH123")
        assertEquals(
            listOf(
                ParsedIdentifier("DLH123", IdentifierType.CALLSIGN),
                ParsedIdentifier("AAA100", IdentifierType.CALLSIGN),
                ParsedIdentifier("BBB200", IdentifierType.CALLSIGN)
            ),
            parsed
        )
    }

    @Test
    fun `bulk parse drops invalid tokens`() {
        val parsed = Identifiers.parseBulk("OK123, !!!, ALSO-INVALID-LONG-123, AB")
        assertEquals(
            listOf(
                ParsedIdentifier("OK123", IdentifierType.CALLSIGN),
                ParsedIdentifier("AB", IdentifierType.CALLSIGN)
            ),
            parsed
        )
    }

    @Test
    fun `bulk parse icao prefix forces icao24`() {
        val parsed = Identifiers.parseBulk("icao:4CA12F, IBE100")
        assertEquals(
            listOf(
                ParsedIdentifier("4ca12f", IdentifierType.ICAO24),
                ParsedIdentifier("IBE100", IdentifierType.CALLSIGN)
            ),
            parsed
        )
    }

    @Test
    fun `bulk parse callsign prefix forces callsign`() {
        val parsed = Identifiers.parseBulk("callsign:4ca12f")
        assertEquals(
            listOf(ParsedIdentifier("4CA12F", IdentifierType.CALLSIGN)),
            parsed
        )
    }

    @Test
    fun `bulk parse icao prefix wins over explicit type`() {
        val parsed = Identifiers.parseBulk(
            "icao:4CA12F, abcdef", IdentifierType.CALLSIGN)
        assertEquals(
            listOf(
                ParsedIdentifier("4ca12f", IdentifierType.ICAO24),
                ParsedIdentifier("ABCDEF", IdentifierType.CALLSIGN)
            ),
            parsed
        )
    }

    @Test
    fun `bulk parse with explicit icao type`() {
        val parsed = Identifiers.parseBulk("ABCDEF,0123ab", IdentifierType.ICAO24)
        assertEquals(
            listOf(
                ParsedIdentifier("abcdef", IdentifierType.ICAO24),
                ParsedIdentifier("0123ab", IdentifierType.ICAO24)
            ),
            parsed
        )
    }
}
