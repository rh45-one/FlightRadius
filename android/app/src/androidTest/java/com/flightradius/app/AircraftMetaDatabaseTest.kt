package com.flightradius.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flightradius.app.data.aircraftdb.AircraftDbImporter
import com.flightradius.app.data.aircraftdb.AircraftMetaDatabase
import com.flightradius.app.data.aircraftdb.AircraftMetaStagingEntity
import java.io.StringReader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AircraftMetaDatabaseTest {

    private lateinit var db: AircraftMetaDatabase

    private val header =
        "'icao24','categoryDescription','icaoAircraftClass','registration','model','typecode'"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AircraftMetaDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun stage(text: String) {
        AircraftDbImporter.import(StringReader(text), emptyMap()) { rows ->
            db.dao().insertStaging(rows.map {
                AircraftMetaStagingEntity(it.icao24, it.cls.code, it.typecode, it.registration, it.model, it.operator)
            })
        }
    }

    @Test
    fun swapReplacesTheLiveTableAndRegistrationLookupWorks() = runBlocking {
        stage("$header\n'a00001','','H2T','EC-KZX','EC135','EC35'\n'a00002','','L1P','EC-ABC','',''")
        db.dao().swap()
        assertEquals(2, db.dao().count())
        assertEquals("a00001", db.dao().findByRegistration("EC-KZX")!!.icao24)
        assertNull(db.dao().findByRegistration("ec-kzx"))
        assertEquals(2, db.dao().findByIds(listOf("a00001", "a00002", "zzz")).size)
    }

    @Test
    fun failedImportKeepsTheOldData() = runBlocking {
        stage("$header\n'a00001','','H2T','EC-KZX','',''")
        db.dao().swap()

        db.dao().clearStaging()
        try {
            AircraftDbImporter.import(
                StringReader("$header\n'b00001','','L1P','EC-NEW','',''"), emptyMap()
            ) { rows ->
                db.dao().insertStaging(rows.map {
                    AircraftMetaStagingEntity(it.icao24, it.cls.code, null, it.registration, null, null)
                })
                error("connection lost")
            }
            fail("expected failure")
        } catch (e: IllegalStateException) {
            db.dao().clearStaging()
        }
        assertEquals(1, db.dao().count())
        assertNotNull(db.dao().findByRegistration("EC-KZX"))
        assertNull(db.dao().findByRegistration("EC-NEW"))
    }

    @Test
    fun secondSwapReplacesEverything() = runBlocking {
        stage("$header\n'a00001','','H2T','EC-KZX','',''")
        db.dao().swap()
        stage("$header\n'a00009','','L1P','EC-NEW','',''")
        db.dao().swap()
        assertEquals(1, db.dao().count())
        assertNull(db.dao().findByRegistration("EC-KZX"))
        assertNotNull(db.dao().findByRegistration("EC-NEW"))
    }
}
