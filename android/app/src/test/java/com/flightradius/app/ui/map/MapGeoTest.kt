package com.flightradius.app.ui.map

import com.flightradius.app.domain.AircraftClass
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.Geo
import com.flightradius.app.domain.LocationSource
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.NearbyAircraft
import com.flightradius.app.domain.UserFix
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapGeoTest {

    @Test
    fun `circle points are equidistant from the centre and the ring is closed`() {
        val pts = MapGeo.circle(40.4168, -3.7038, 25.0)
        assertEquals(73, pts.size)
        assertEquals(pts.first(), pts.last())
        for ((lat, lon) in pts) {
            assertEquals(25.0, Geo.distanceKm(40.4168, -3.7038, lat, lon), 0.1)
        }
    }

    @Test
    fun `bounds contain the whole circle`() {
        val b = MapGeo.bounds(40.0, -3.0, 25.0)
        for ((lat, lon) in MapGeo.circle(40.0, -3.0, 25.0)) {
            assertTrue(lat in b.south..b.north)
            assertTrue(lon in b.west..b.east)
        }
    }

    private val fix = UserFix(40.0, -3.0, null, 0L, LocationSource.MANUAL)

    @Test
    fun `aircraft features merge tracked and nearby with kinds and labels`() {
        val snap = MonitoringSnapshot(
            timeMs = 0L, fix = fix,
            ranked = listOf(
                AircraftObservation(7, "IBE\"1", null, 5.0, 40.1, -3.1, headingDeg = 90.0,
                    bearingDeg = 0.0, effectiveRadiusKm = 25.0)
            ),
            noData = emptyList(), fleets = emptyList(), closest = null,
            nearby = listOf(
                NearbyAircraft("abc123", null, AircraftClass.HELICOPTER, 40.0, -3.0, 1.0, 0.0,
                    trackDeg = 45.0, registration = "EC-KCM", matchesRule = true),
                NearbyAircraft("def456", "RYR1", AircraftClass.AIRLINER, 40.2, -3.2, 2.0, 0.0)
            )
        )
        val list = MapGeo.aircraft(snap)
        assertEquals(listOf("t:7", "n:abc123", "n:def456"), list.map { it.id })
        assertEquals(listOf(MapKind.TRACKED, MapKind.MATCH, MapKind.NEARBY), list.map { it.kind })
        assertEquals(listOf(90.0, 45.0, 0.0), list.map { it.rotationDeg })
        assertEquals("EC-KCM", list[1].label)

        val json = Json.parseToJsonElement(MapGeo.aircraftJson(list)).jsonObject
        val f = json["features"]!!.jsonArray
        assertEquals(3, f.size)
        assertEquals("IBE\"1",
            f[0].jsonObject["properties"]!!.jsonObject["label"]!!.jsonPrimitive.content)
        assertEquals(-3.1,
            f[0].jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray[0]
                .jsonPrimitive.doubleOrNull!!, 1e-6)
    }

    @Test
    fun `geojson builders produce valid json`() {
        for (text in listOf(
            MapGeo.polygonJson(MapGeo.circle(40.0, -3.0, 5.0)),
            MapGeo.lineJson(MapGeo.circle(40.0, -3.0, 5.0)),
            MapGeo.pointJson(40.0, -3.0)
        )) {
            val f = Json.parseToJsonElement(text).jsonObject["features"] as JsonArray
            assertEquals(1, f.size)
            assertTrue(f[0] is JsonObject)
        }
        assertEquals(0, (Json.parseToJsonElement(MapGeo.EMPTY).jsonObject["features"] as JsonArray).size)
        assertEquals(0, (Json.parseToJsonElement(MapGeo.aircraftJson(emptyList()))
            .jsonObject["features"] as JsonArray).size)
    }
}
