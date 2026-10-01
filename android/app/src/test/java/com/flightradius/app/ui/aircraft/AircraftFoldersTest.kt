package com.flightradius.app.ui.aircraft

import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.TrackedAircraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AircraftFoldersTest {

    private fun ac(id: Long, name: String, notes: String? = null) =
        TrackedAircraft(id, name, IdentifierType.CALLSIGN, notes = notes, createdAt = id)

    private fun group(id: Long, name: String, vararg members: Long) =
        Fleet(id, name, 0xFF22D3EE.toInt(), memberIds = members.toSet())

    private val a1 = ac(1, "IBE3174")
    private val a2 = ac(2, "PEGASO1")
    private val a3 = ac(3, "RYR4412")
    private val a4 = ac(4, "DLH100")

    private fun describe(items: List<FolderItem>) = items.map {
        when (it) {
            is FolderItem.Header -> "H:${it.group.name}:${it.count}:${if (it.expanded) "open" else "closed"}"
            is FolderItem.Member -> "M:${it.aircraft.identifier}:${it.groupId}"
            is FolderItem.EmptyGroup -> "E:${it.groupId}"
            is FolderItem.UngroupedHeader -> "U"
        }
    }

    @Test fun `no groups gives the plain sorted flat list`() {
        val items = AircraftFolders.build(emptyList(), listOf(a1, a2, a4), "", emptySet())
        assertEquals(listOf("M:DLH100:null", "M:IBE3174:null", "M:PEGASO1:null"), describe(items))
        assertTrue((items.first() as FolderItem.Member).first)
        assertTrue(items.last().last)
        assertFalse(items.first().last)
    }

    @Test fun `groups sorted by name with members then the no group section`() {
        val groups = listOf(group(2, "Pegasus", 2), group(1, "airlines", 1, 3))
        val items = AircraftFolders.build(groups, listOf(a1, a2, a3, a4), "", emptySet())
        assertEquals(
            listOf(
                "H:airlines:2:open", "M:IBE3174:1", "M:RYR4412:1",
                "H:Pegasus:1:open", "M:PEGASO1:2",
                "U", "M:DLH100:null"
            ),
            describe(items)
        )
    }

    @Test fun `no ungrouped aircraft means no no group header`() {
        val items = AircraftFolders.build(listOf(group(1, "All", 1, 2)), listOf(a1, a2), "", emptySet())
        assertFalse(describe(items).contains("U"))
    }

    @Test fun `collapsed group shows only its header`() {
        val groups = listOf(group(1, "Alpha", 1, 2))
        val items = AircraftFolders.build(groups, listOf(a1, a2, a4), "", setOf(1L))
        assertEquals(listOf("H:Alpha:2:closed", "U", "M:DLH100:null"), describe(items))
        assertTrue(items.first().last)
    }

    @Test fun `empty group shows the hint row, collapsed or not`() {
        val groups = listOf(group(1, "Empty"))
        assertEquals(listOf("H:Empty:0:open", "E:1"),
            describe(AircraftFolders.build(groups, emptyList(), "", emptySet())))
        assertEquals(listOf("H:Empty:0:closed"),
            describe(AircraftFolders.build(groups, emptyList(), "", setOf(1L))))
    }

    @Test fun `search hides groups without matches and expands the ones with`() {
        val groups = listOf(group(1, "Alpha", 1), group(2, "Beta", 2, 3))
        val items = AircraftFolders.build(groups, listOf(a1, a2, a3, a4), "peg", setOf(2L))
        // Beta is collapsed but has a match: shown expanded; Alpha hidden; ungrouped has none.
        assertEquals(listOf("H:Beta:2:open", "M:PEGASO1:2"), describe(items))
    }

    @Test fun `search matches notes and keeps matching ungrouped aircraft`() {
        val noted = ac(5, "ZZZ9", notes = "Pegasus helicopter")
        val items = AircraftFolders.build(listOf(group(1, "Alpha", 1)), listOf(a1, noted), "pegasus", emptySet())
        assertEquals(listOf("U", "M:ZZZ9:null"), describe(items))
    }

    @Test fun `search with no match gives an empty list`() {
        assertTrue(AircraftFolders.build(listOf(group(1, "Alpha", 1)), listOf(a1), "zzz", emptySet()).isEmpty())
    }

    @Test fun `last flags close each card`() {
        val groups = listOf(group(1, "Alpha", 1, 2))
        val items = AircraftFolders.build(groups, listOf(a1, a2, a4), "", emptySet())
        // Header, IBE3174, PEGASO1 (last of group), U, DLH100 (last)
        assertEquals(listOf(false, false, true, false, true), items.map { it.last })
    }

    @Test fun `previous assignments record the old group or null`() {
        val groups = listOf(group(1, "Alpha", 1), group(2, "Beta", 2))
        val previous = AircraftFolders.previousAssignments(listOf(1, 2, 4), groups)
        assertEquals(mapOf(1L to 1L, 2L to 2L, 4L to null), previous)
        assertNull(previous[4L])
        assertTrue(previous.containsKey(4L))
    }
}

class RadiusInfoTest {
    private fun ac(radius: Double? = null) =
        TrackedAircraft(1, "IBE3174", IdentifierType.CALLSIGN, alertRadiusKm = radius, createdAt = 1)
    private fun grp(radius: Double?) = Fleet(1, "G", 0, alertRadiusKm = radius, memberIds = setOf(1))

    @Test fun `own radius wins over group and global`() =
        assertEquals(RadiusInfo(12.5, RadiusSource.OWN), radiusInfo(ac(12.5), grp(30.0), 25.0))

    @Test fun `group radius wins over global`() =
        assertEquals(RadiusInfo(30.0, RadiusSource.GROUP), radiusInfo(ac(), grp(30.0), 25.0))

    @Test fun `global when neither is set, with or without a group`() {
        assertEquals(RadiusInfo(25.0, RadiusSource.GLOBAL), radiusInfo(ac(), grp(null), 25.0))
        assertEquals(RadiusInfo(25.0, RadiusSource.GLOBAL), radiusInfo(ac(), null, 25.0))
    }
}
