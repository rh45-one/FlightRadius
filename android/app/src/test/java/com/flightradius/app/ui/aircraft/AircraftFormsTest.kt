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

    @Test
    fun `toggleType round trip keeps the row id and ends as uppercase callsign`() {
        val start = AircraftForms.parseBulk("IBE3174, ABC123", emptySet())
        val abc = start.first { it.identifier == "ABC123" }
        assertEquals(IdentifierType.CALLSIGN, abc.type)

        val asIcao = AircraftForms.toggleType(start, abc.id, emptySet())
        val row1 = asIcao.single { it.id == abc.id }
        assertEquals("abc123", row1.identifier)
        assertEquals(IdentifierType.ICAO24, row1.type)

        val back = AircraftForms.toggleType(asIcao, abc.id, emptySet())
        val row2 = back.single { it.id == abc.id }
        assertEquals("ABC123", row2.identifier)
        assertEquals(IdentifierType.CALLSIGN, row2.type)
        assertEquals(start.map { it.id }, back.map { it.id })
    }

    @Test
    fun `toggleType keeps checkbox choices and ignores unknown or unambiguous rows`() {
        val start = AircraftForms.parseBulk("IBE3174, ABC123, DEF456", emptySet())
            .map { if (it.identifier == "DEF456") it.copy(selected = false) else it }
        val abc = start.first { it.identifier == "ABC123" }
        val toggled = AircraftForms.toggleType(start, abc.id, emptySet())
        assertEquals(false, toggled.first { it.id == start[2].id }.selected)
        assertEquals(start, AircraftForms.toggleType(start, 999, emptySet()))
        val ibe = start.first { it.identifier == "IBE3174" }
        assertEquals(start, AircraftForms.toggleType(start, ibe.id, emptySet()))
        // An unchecked NEW row stays unchecked across a retype.
        val unchecked = start.map { if (it.id == abc.id) it.copy(selected = false) else it }
        assertEquals(false, AircraftForms.toggleType(unchecked, abc.id, emptySet())
            .first { it.id == abc.id }.selected)
    }

    @Test
    fun `AUTO mode matches the previous classification`() {
        val out = AircraftForms.parseBulk("IBE3174 4ca123 !!", emptySet(), BulkMode.AUTO)
        assertEquals(listOf("IBE3174", "4CA123"), out.filter { it.type != null }.map { it.identifier })
        assertEquals(BulkAddStatus.INVALID, out.last().status)
        assertEquals(null, out.last().invalidFor)
        assertTrue(out[1].ambiguous)
    }

    @Test
    fun `CALLSIGNS mode treats every token as a callsign`() {
        val out = AircraftForms.parseBulk("ABC123 IBE3174 !!", emptySet(), BulkMode.CALLSIGNS)
        assertEquals(IdentifierType.CALLSIGN, out[0].type)
        assertEquals("ABC123", out[0].identifier)
        assertEquals(IdentifierType.CALLSIGN, out[1].type)
        assertEquals(BulkAddStatus.INVALID, out[2].status)
        assertEquals(IdentifierType.CALLSIGN, out[2].invalidFor)
    }

    @Test
    fun `ICAO24 mode rejects non-hex tokens and lowercases hex`() {
        val out = AircraftForms.parseBulk("ABC123 IBE3174 LONGCALL1", emptySet(), BulkMode.ICAO24)
        assertEquals("abc123", out[0].identifier)
        assertEquals(IdentifierType.ICAO24, out[0].type)
        assertEquals(BulkAddStatus.INVALID, out[1].status)
        assertEquals(IdentifierType.ICAO24, out[1].invalidFor)
        assertEquals(BulkAddStatus.INVALID, out[2].status) // 8 chars, not 6 hex
        assertEquals("IBE3174", out[1].identifier) // raw token kept for display
    }

    @Test
    fun `prefixes win over the mode`() {
        val icao = AircraftForms.parseBulk("icao:4ca12f callsign:ABC123", emptySet(), BulkMode.CALLSIGNS)
        assertEquals(IdentifierType.ICAO24, icao[0].type)
        assertEquals(IdentifierType.CALLSIGN, icao[1].type)
        val cs = AircraftForms.parseBulk("icao:4ca12f callsign:ABC123", emptySet(), BulkMode.ICAO24)
        assertEquals(IdentifierType.ICAO24, cs[0].type)
        assertEquals(IdentifierType.CALLSIGN, cs[1].type)
        assertTrue(!cs[0].ambiguous && !cs[1].ambiguous)
    }

    @Test
    fun `duplicates and tracked rows keep working in every mode`() {
        val tracked = setOf("4ca123", "ABC123")
        // AUTO / CALLSIGNS read 4ca123 as callsign 4CA123 (not tracked), ABC123 is tracked.
        for (mode in listOf(BulkMode.AUTO, BulkMode.CALLSIGNS)) {
            val out = AircraftForms.parseBulk("4ca123 4CA123 abc123", tracked, mode)
            assertEquals("$mode", listOf("4CA123", "ABC123"), out.map { it.identifier })
            assertEquals(listOf(BulkAddStatus.NEW, BulkAddStatus.ALREADY_TRACKED), out.map { it.status })
        }
        // ICAO24 mode: 4ca123 is tracked, the duplicate is dropped, abc123 is new.
        val icao = AircraftForms.parseBulk("4ca123 4CA123 abc123", tracked, BulkMode.ICAO24)
        assertEquals(listOf("4ca123", "abc123"), icao.map { it.identifier })
        assertEquals(listOf(BulkAddStatus.ALREADY_TRACKED, BulkAddStatus.NEW), icao.map { it.status })
    }

    @Test
    fun `ids are token indices and stay stable across modes`() {
        val auto = AircraftForms.parseBulk("IBE3174, ABC123, !!, DEF456", emptySet())
        val icao = AircraftForms.parseBulk("IBE3174, ABC123, !!, DEF456", emptySet(), BulkMode.ICAO24)
        assertEquals(listOf(0, 1, 2, 3), auto.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), icao.map { it.id })
    }

    @Test
    fun `reparse keeps checkbox choices for rows that remain new`() {
        val text = "IBE3174, ABC123, DEF456"
        val first = AircraftForms.parseBulk(text, emptySet())
            .map { if (it.id == 1) it.copy(selected = false) else it }
        val re = AircraftForms.reparse(text, emptySet(), BulkMode.ICAO24, first)
        assertEquals(false, re.first { it.id == 1 }.selected)
        assertEquals(true, re.first { it.id == 2 }.selected)
        assertEquals(BulkAddStatus.INVALID, re.first { it.id == 0 }.status)
    }
}
