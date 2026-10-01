package com.flightradius.app

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flightradius.app.data.db.FlightRadiusDatabase
import com.flightradius.app.data.db.FlightRadiusMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FlightRadiusDatabase::class.java
    )

    private fun SupportSQLiteDatabase.seedV1() {
        execSQL("INSERT INTO tracked_aircraft (id, identifier, type, notes, alertRadiusKm, createdAt) VALUES (1, 'IBE3174', 'CALLSIGN', 'both', 12.5, 10)")
        execSQL("INSERT INTO tracked_aircraft (id, identifier, type, notes, alertRadiusKm, createdAt) VALUES (2, 'abc123', 'ICAO24', NULL, NULL, 11)")
        execSQL("INSERT INTO tracked_aircraft (id, identifier, type, notes, alertRadiusKm, createdAt) VALUES (3, 'DLH100', 'CALLSIGN', NULL, NULL, 12)")
        execSQL("INSERT INTO tracked_aircraft (id, identifier, type, notes, alertRadiusKm, createdAt) VALUES (4, 'RYR200', 'CALLSIGN', NULL, NULL, 13)")
        // Fleet 1 is older than fleet 2; fleets 3 and 4 tie on createdAt.
        execSQL("INSERT INTO fleets (id, name, colorArgb, alertRadiusKm, createdAt) VALUES (1, 'Pegasus', -16711681, 30.0, 100)")
        execSQL("INSERT INTO fleets (id, name, colorArgb, alertRadiusKm, createdAt) VALUES (2, 'Airlines', -65536, NULL, 200)")
        execSQL("INSERT INTO fleets (id, name, colorArgb, alertRadiusKm, createdAt) VALUES (3, 'TieA', -1, NULL, 300)")
        execSQL("INSERT INTO fleets (id, name, colorArgb, alertRadiusKm, createdAt) VALUES (4, 'TieB', -1, NULL, 300)")
        // Aircraft 1 in two fleets (older = Pegasus), 2 only in Airlines, 3 in none,
        // 4 in both tied fleets (smallest id = 3).
        execSQL("INSERT INTO fleet_members (fleetId, aircraftId) VALUES (2, 1)")
        execSQL("INSERT INTO fleet_members (fleetId, aircraftId) VALUES (1, 1)")
        execSQL("INSERT INTO fleet_members (fleetId, aircraftId) VALUES (2, 2)")
        execSQL("INSERT INTO fleet_members (fleetId, aircraftId) VALUES (4, 4)")
        execSQL("INSERT INTO fleet_members (fleetId, aircraftId) VALUES (3, 4)")
    }

    @Test
    fun migrate1To2KeepsOneMembershipPerAircraftAndAddsIcons() {
        helper.createDatabase(DB, 1).apply { seedV1(); close() }

        val db = helper.runMigrationsAndValidate(DB, 2, true, FlightRadiusMigrations.MIGRATION_1_2)

        val memberships = mutableMapOf<Long, Long>()
        db.query("SELECT aircraftId, fleetId FROM fleet_members").use {
            while (it.moveToNext()) {
                assertTrue("duplicate membership", memberships.put(it.getLong(0), it.getLong(1)) == null)
            }
        }
        assertEquals(mapOf(1L to 1L, 2L to 2L, 4L to 3L), memberships)

        db.query("SELECT id, name, colorArgb, alertRadiusKm, iconKey FROM fleets ORDER BY id").use {
            val rows = generateSequence { if (it.moveToNext()) it else null }
                .map { c -> listOf(c.getLong(0), c.getString(1), c.getInt(2), c.getString(4)) to
                    (if (c.isNull(3)) null else c.getDouble(3)) }
                .toList()
            assertEquals(4, rows.size)
            assertTrue(rows.all { it.first[3] == "PLANE" })
            assertEquals(listOf(1L, "Pegasus", -16711681, "PLANE") to 30.0, rows[0])
            assertEquals("Airlines", rows[1].first[1])
        }

        db.query("SELECT id, identifier, type, notes, alertRadiusKm, createdAt FROM tracked_aircraft ORDER BY id").use {
            assertEquals(4, it.count)
            it.moveToFirst()
            assertEquals("IBE3174", it.getString(1))
            assertEquals("both", it.getString(3))
            assertEquals(12.5, it.getDouble(4), 0.0)
            assertEquals(10L, it.getLong(5))
        }
    }

    @Test
    fun migratedDatabaseRejectsASecondMembership() {
        helper.createDatabase(DB, 1).apply { seedV1(); close() }
        val db = helper.runMigrationsAndValidate(DB, 2, true, FlightRadiusMigrations.MIGRATION_1_2)
        var rejected = false
        try {
            db.execSQL("INSERT INTO fleet_members (fleetId, aircraftId) VALUES (2, 3)")
            db.execSQL("INSERT INTO fleet_members (fleetId, aircraftId) VALUES (1, 3)")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    private companion object {
        const val DB = "migration-test"
    }
}
