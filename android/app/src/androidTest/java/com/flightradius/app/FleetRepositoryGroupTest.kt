package com.flightradius.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flightradius.app.data.db.FlightRadiusDatabase
import com.flightradius.app.data.db.TrackedAircraftEntity
import com.flightradius.app.data.repo.FleetRepository
import com.flightradius.app.domain.GroupIcon
import com.flightradius.app.domain.IdentifierType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FleetRepositoryGroupTest {

    private lateinit var db: FlightRadiusDatabase
    private lateinit var repo: FleetRepository
    private val ids = mutableListOf<Long>()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FlightRadiusDatabase::class.java
        ).build()
        repo = FleetRepository(db.fleetDao())
        for (n in listOf("AAA111", "BBB222", "CCC333")) {
            ids += db.aircraftDao().insertIgnore(
                TrackedAircraftEntity(identifier = n, type = IdentifierType.CALLSIGN, createdAt = 1))
        }
    }

    @After fun tearDown() = db.close()

    private suspend fun groupOf(id: Long) =
        repo.getAll().filter { id in it.memberIds }.map { it.name }

    @Test
    fun moveUngroupAndMoveBetweenGroupsNeverDuplicates() = runBlocking {
        val a = repo.getOrCreate("Alpha", icon = GroupIcon.STAR)!!
        val b = repo.getOrCreate("Beta")!!

        repo.setGroup(ids.take(2), a)
        assertEquals(listOf("Alpha"), groupOf(ids[0]))
        assertEquals(listOf("Alpha"), groupOf(ids[1]))
        assertEquals(emptyList<String>(), groupOf(ids[2]))

        repo.setGroup(listOf(ids[0]), b)
        assertEquals(listOf("Beta"), groupOf(ids[0]))
        assertEquals(listOf("Alpha"), groupOf(ids[1]))

        repo.setGroup(ids.take(2), null)
        assertTrue(ids.all { groupOf(it).isEmpty() })

        // Same group twice is a no-op, not a constraint violation.
        repo.setGroup(ids, a)
        repo.setGroup(ids, a)
        assertEquals(3, repo.getAll().first { it.id == a }.memberIds.size)
        assertEquals(GroupIcon.STAR, repo.getAll().first { it.id == a }.icon)
    }

    @Test
    fun applyAssignmentsRestoresPreviousMemberships() = runBlocking {
        val a = repo.getOrCreate("Alpha")!!
        val b = repo.getOrCreate("Beta")!!
        repo.setGroup(listOf(ids[0]), a)
        val before = mapOf(ids[0] to a, ids[1] to null as Long?)
        repo.setGroup(ids.take(2), b)
        repo.applyAssignments(before)
        assertEquals(listOf("Alpha"), groupOf(ids[0]))
        assertEquals(emptyList<String>(), groupOf(ids[1]))
    }

    @Test
    fun importStyleAddKeepsTheFirstGroup() = runBlocking {
        val a = repo.getOrCreate("Alpha")!!
        val b = repo.getOrCreate("Beta")!!
        assertTrue(repo.addMemberIfUngrouped(a, ids[0]))
        assertFalse(repo.addMemberIfUngrouped(b, ids[0]))
        assertEquals(listOf("Alpha"), groupOf(ids[0]))
    }

    @Test
    fun deletingAGroupKeepsItsAircraft() = runBlocking {
        val a = repo.getOrCreate("Alpha")!!
        repo.setGroup(ids, a)
        repo.remove(a)
        assertEquals(3, db.aircraftDao().getAll().size)
        assertTrue(ids.all { groupOf(it).isEmpty() })
    }
}
