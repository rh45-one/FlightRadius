package com.flightradius.app.ui.aircraft

import com.flightradius.app.domain.IdentifierType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AircraftFormsTest {

    // Normalized forms as stored: callsigns uppercase, icao24 lowercase.
    private val existing = setOf("IBE3174", "4ca123")

    @Test
    fun `blank input is EMPTY`() {
        assertEquals(AddValidation.EMPTY,
            AircraftForms.validate("   ", IdentifierType.CALLSIGN, existing))
    }

    @Test
    fun `bad format is INVALID_FORMAT`() {
        assertEquals(AddValidation.INVALID_FORMAT,
            AircraftForms.validate("!!!", IdentifierType.CALLSIGN, existing))
        assertEquals(AddValidation.INVALID_FORMAT,
            AircraftForms.validate("ZZZZZZZZZ", IdentifierType.CALLSIGN, existing))
        assertEquals(AddValidation.INVALID_FORMAT,
            AircraftForms.validate("xyz123", IdentifierType.ICAO24, existing))
    }

    @Test
    fun `already tracked is DUPLICATE`() {
        assertEquals(AddValidation.DUPLICATE,
            AircraftForms.validate("ibe3174", IdentifierType.CALLSIGN, existing))
        assertEquals(AddValidation.DUPLICATE,
            AircraftForms.validate("4CA123", IdentifierType.ICAO24, existing))
    }

    @Test
    fun `new valid identifier is VALID`() {
        assertEquals(AddValidation.VALID,
            AircraftForms.validate("DLH400", IdentifierType.CALLSIGN, existing))
        assertEquals(AddValidation.VALID,
            AircraftForms.validate("a0b1c2", IdentifierType.ICAO24, existing))
    }

    @Test
    fun `suggestType spots icao24`() {
        assertEquals(IdentifierType.ICAO24, AircraftForms.suggestType("4ca123"))
        assertEquals(IdentifierType.CALLSIGN, AircraftForms.suggestType("DLH400"))
        assertEquals(null, AircraftForms.suggestType(""))
    }

    @Test
    fun `parseBulk splits dedupes and flags tracked`() {
        val out = AircraftForms.parseBulk(
            "ibe3174, dlh400\n4CA123 abc-def TOOLONGCALLSIGN99 a0b1c2 DLH400 DLH400",
            existing
        )
        val byIdent = out.associateBy { it.identifier }

        // Already tracked (normalizes to the stored callsign).
        assertEquals(BulkAddStatus.ALREADY_TRACKED, byIdent["IBE3174"]!!.status)
        // Ambiguous 6-hex defaults to CALLSIGN; not tracked as a callsign.
        assertEquals(BulkAddStatus.NEW, byIdent["4CA123"]!!.status)
        assertEquals(IdentifierType.CALLSIGN, byIdent["4CA123"]!!.type)
        // New, normalized.
        assertEquals(BulkAddStatus.NEW, byIdent["DLH400"]!!.status)
        assertEquals(IdentifierType.CALLSIGN, byIdent["DLH400"]!!.type)
        assertEquals(BulkAddStatus.NEW, byIdent["A0B1C2"]!!.status)
        // Invalid tokens kept as INVALID, unselected.
        assertEquals(BulkAddStatus.INVALID, byIdent["abc-def"]!!.status)
        assertEquals(BulkAddStatus.INVALID, byIdent["TOOLONGCALLSIGN99"]!!.status)
        // In-input duplicate collapsed (DLH400 appears once).
        assertEquals(1, out.count { it.identifier == "DLH400" })
        // Selection defaults.
        assertTrue(out.filter { it.status == BulkAddStatus.NEW }.all { it.selected })
        assertTrue(out.filter { it.status != BulkAddStatus.NEW }.none { it.selected })
        // 6-hex tokens valid as both types are flagged ambiguous.
        assertTrue(byIdent["4CA123"]!!.ambiguous)
        assertTrue(byIdent["A0B1C2"]!!.ambiguous)
        assertTrue(!byIdent["DLH400"]!!.ambiguous)
    }

    @Test
    fun `parseBulk honours icao prefix`() {
        val out = AircraftForms.parseBulk("icao:4ca12f IBE200", existing)
        val byIdent = out.associateBy { it.identifier }
        assertEquals(IdentifierType.ICAO24, byIdent["4ca12f"]!!.type)
        assertTrue(!byIdent["4ca12f"]!!.ambiguous)
        assertEquals(IdentifierType.CALLSIGN, byIdent["IBE200"]!!.type)
    }

    @Test
    fun `retype switches an ambiguous entry and re-checks tracking`() {
        val e = AircraftForms.parseBulk("4CA123", existing).single()
        assertEquals(IdentifierType.CALLSIGN, e.type)
        val asIcao = AircraftForms.retype(e, IdentifierType.ICAO24, existing)
        assertEquals("4ca123", asIcao.identifier)
        assertEquals(IdentifierType.ICAO24, asIcao.type)
        // The stored "4ca123" is already tracked.
        assertEquals(BulkAddStatus.ALREADY_TRACKED, asIcao.status)
        assertTrue(!asIcao.selected)
        val back = AircraftForms.retype(asIcao, IdentifierType.CALLSIGN, existing)
        assertEquals("4CA123", back.identifier)
        assertEquals(BulkAddStatus.NEW, back.status)
    }

    @Test
    fun `parseBulk empty input yields nothing`() {
        assertTrue(AircraftForms.parseBulk("  \n ", emptySet()).isEmpty())
    }
}
