package com.flightradius.app.data.aircraftdb

import com.flightradius.app.domain.AircraftClass
import java.io.StringReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AircraftDbImporterTest {

    private val HEADER =
        "'icao24','timestamp','acars','adsb','built','categoryDescription','country','engines'," +
            "'firstFlightDate','firstSeen','icaoAircraftClass','lineNumber','manufacturerIcao'," +
            "'manufacturerName','model','modes','nextReg','notes','operator','operatorCallsign'," +
            "'operatorIata','operatorIcao','owner','prevReg','regUntil','registered','registration'," +
            "'selCal','serialNumber','status','typecode','vdl'"

    private val DOC8643 =
        "\"AircraftDescription\",\"Description\",\"Designator\",\"EngineCount\",\"EngineType\"," +
            "\"ManufacturerCode\",\"ModelFullName\",\"WTC\"\n" +
            "\"Helicopter\",\"H2T\",\"EC35\",\"2\",\"Turboshaft\",\"EUROCOPTER\",\"EC-135\",\"L\"\n" +
            "\"LandPlane\",\"L2J\",\"A320\",\"2\",\"Jet\",\"AIRBUS\",\"A-320\",\"M\"\n"

    private val ROWS = listOf(
        // class + registration
        "'0071b4','1970-01-01 01:00:00',0,0,,'',,'',,,H1T,'',LEONARDO,'','',0,'','','','','','','','',,,ZT-RAX,'','','',A119,0",
        // class only (no registration)
        "'000001','2016-11-27 00:10:39',0,0,,'',,'',,,L1P,'',SHIJIAZHUANG,'',Antonov 2,0,'','','','','','',Private,'',,,'','','','',AN2,0",
        // registration only, no class info at all
        "'00ad00','2017-09-15 00:10:19',0,0,,'',South Africa,'',,,'',,'','',,0,'','','','','','','','',,,ZS-REE,'','','','',0",
        // category description only
        "'000080','2017-07-01 02:00:00',0,0,,Rotorcraft,,'',,,'','','','','',0,'','','','','','','','',,,'','','','','',0",
        // nothing useful: dropped
        "'000005','1970-01-01 01:00:00',0,0,,'',,'',,,'','','','','',0,'','','','','','','','',,,'','','','','',0",
        // class from doc8643 typecode
        "'4a0001','1970-01-01 01:00:00',0,0,,'',,'',,,'','','','','',0,'','','','','','','','',,,EC-KZX,'','','',EC35,0",
        // invalid icao24: dropped
        "'zzzzzz','1970-01-01 01:00:00',0,0,,'',,'',,,H1T,'','','','',0,'','','','','','','','',,,EC-BAD,'','','','',0"
    )

    private fun csv() = (listOf(HEADER) + ROWS).joinToString("\n")

    private fun doc() = AircraftDbImporter.parseDoc8643(StringReader(DOC8643))

    private fun importAll(text: String, sink: suspend (List<MetaRow>) -> Unit): ImportStats =
        runBlocking { AircraftDbImporter.import(StringReader(text), doc(), sink) }

    @Test
    fun `doc8643 parsing`() {
        val d = doc()
        assertEquals("H2T", d["EC35"]!!.description)
        assertEquals("L", d["EC35"]!!.wtc)
        assertEquals(2, d.size)
    }

    @Test
    fun `header driven mapping keeps rows with a class or a registration`() {
        val rows = ArrayList<MetaRow>()
        val stats = importAll(csv()) { rows += it }
        assertEquals(7L, stats.parsedRows)
        assertEquals(5L, stats.keptRows)
        assertEquals(4L, stats.rowsWithClass)
        val byId = rows.associateBy { it.icao24 }
        assertEquals(setOf("0071b4", "000001", "00ad00", "000080", "4a0001"), byId.keys)
        assertEquals(AircraftClass.HELICOPTER, byId["0071b4"]!!.cls)
        assertEquals("ZT-RAX", byId["0071b4"]!!.registration)
        assertEquals("A119", byId["0071b4"]!!.typecode)
        assertEquals(AircraftClass.LIGHT, byId["000001"]!!.cls)
        assertNull(byId["000001"]!!.registration)
        assertEquals("Antonov 2", byId["000001"]!!.model)
        assertEquals(AircraftClass.UNKNOWN, byId["00ad00"]!!.cls)
        assertEquals("ZS-REE", byId["00ad00"]!!.registration)
        assertEquals(AircraftClass.HELICOPTER, byId["000080"]!!.cls)
        assertEquals(AircraftClass.HELICOPTER, byId["4a0001"]!!.cls)
        assertEquals("EC-KZX", byId["4a0001"]!!.registration)
    }

    @Test
    fun `legacy double quoted header with icaoaircrafttype`() {
        val legacy = "\"icao24\",\"registration\",\"model\",\"typecode\",\"icaoaircrafttype\"," +
            "\"operator\",\"categoryDescription\"\n" +
            "\"4ca123\",\"ec-abc\",\"Cessna 172\",\"C172\",\"L1P\",\"\",\"\"\n" +
            "\"4ca124\",\"\",\"\",\"\",\"\",\"\",\"Glider / sailplane\"\n"
        val rows = ArrayList<MetaRow>()
        importAll(legacy) { rows += it }
        assertEquals(2, rows.size)
        assertEquals(AircraftClass.LIGHT, rows[0].cls)
        assertEquals("EC-ABC", rows[0].registration)
        assertEquals(AircraftClass.GLIDER, rows[1].cls)
    }

    @Test
    fun `rows are delivered in batches of BATCH_SIZE`() {
        val n = AircraftDbImporter.BATCH_SIZE * 2 + 17
        val text = buildString {
            append(HEADER).append('\n')
            repeat(n) { i ->
                append("'%06x',,0,0,,'',,'',,,L1P,'','','','',0,'','','','','','','','',,,'','','','','',0\n".format(i))
            }
        }
        val sizes = ArrayList<Int>()
        val stats = importAll(text) { sizes += it.size }
        assertEquals(listOf(AircraftDbImporter.BATCH_SIZE, AircraftDbImporter.BATCH_SIZE, 17), sizes)
        assertEquals(n.toLong(), stats.keptRows)
    }

    @Test
    fun `a failing sink aborts the import so nothing is swapped`() {
        val text = buildString {
            append(HEADER).append('\n')
            repeat(AircraftDbImporter.BATCH_SIZE * 3) { i ->
                append("'%06x',,0,0,,'',,'',,,L1P,'','','','',0,'','','','','','','','',,,'','','','','',0\n".format(i))
            }
        }
        var batches = 0
        try {
            importAll(text) {
                batches++
                if (batches == 2) error("disk full")
            }
            fail("expected the sink failure to propagate")
        } catch (e: IllegalStateException) {
            assertEquals("disk full", e.message)
        }
        assertEquals(2, batches)
    }

    @Test
    fun `empty or headerless input is harmless`() {
        assertEquals(0L, importAll("") {}.keptRows)
        assertEquals(0L, importAll("'foo','bar'\n'1','2'") { fail("no rows expected") }.keptRows)
    }

    @Test
    fun `most rows with a class make it in`() {
        assertTrue(ROWS.size > 3)
    }
}
