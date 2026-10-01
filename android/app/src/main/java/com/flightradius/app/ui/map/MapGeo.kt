package com.flightradius.app.ui.map

import com.flightradius.app.domain.MonitoringSnapshot
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class MapKind { TRACKED, MATCH, NEARBY }

/** One aircraft on the map. [id] is `t:<aircraftId>` for tracked or `n:<icao24>` for nearby. */
data class MapAircraft(
    val id: String,
    val kind: MapKind,
    val lat: Double,
    val lon: Double,
    val rotationDeg: Double,
    val label: String
)

data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double)

/** Pure GeoJSON / geometry helpers for the map (no MapLibre types). */
object MapGeo {

    private const val EARTH_KM = 6371.0

    fun aircraft(snapshot: MonitoringSnapshot): List<MapAircraft> {
        val tracked = snapshot.ranked.map { o ->
            MapAircraft(
                id = "t:${o.aircraftId}", kind = MapKind.TRACKED, lat = o.lat, lon = o.lon,
                rotationDeg = o.headingDeg ?: 0.0, label = o.callsign ?: o.icao24 ?: "?"
            )
        }
        val nearby = snapshot.nearby.map { a ->
            MapAircraft(
                id = "n:${a.icao24}",
                kind = if (a.matchesRule) MapKind.MATCH else MapKind.NEARBY,
                lat = a.lat, lon = a.lon, rotationDeg = a.trackDeg ?: 0.0, label = a.displayName
            )
        }
        return tracked + nearby
    }

    /** Closed ring of [steps] points at [radiusKm] around a point, as (lat, lon). */
    fun circle(lat: Double, lon: Double, radiusKm: Double, steps: Int = 360): List<Pair<Double, Double>> {
        val d = radiusKm / EARTH_KM
        val p1 = Math.toRadians(lat)
        val l1 = Math.toRadians(lon)
        val pts = (0 until steps).map { i ->
            val b = Math.toRadians(360.0 * i / steps)
            val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
            val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
            Math.toDegrees(p2) to Math.toDegrees(l2)
        }
        return pts + pts.first()
    }

    /** Smallest box containing the circle (used to frame the camera). */
    fun bounds(lat: Double, lon: Double, radiusKm: Double): GeoBounds {
        val dLat = Math.toDegrees(radiusKm / EARTH_KM)
        val dLon = Math.toDegrees(radiusKm / (EARTH_KM * max(cos(Math.toRadians(lat)), 0.01)))
        return GeoBounds(lat - dLat, lon - dLon, lat + dLat, lon + dLon)
    }

    private fun num(v: Double) = String.format(Locale.ROOT, "%.6f", v)

    private fun ring(points: List<Pair<Double, Double>>) =
        points.joinToString(",", "[", "]") { "[${num(it.second)},${num(it.first)}]" }

    fun polygonJson(points: List<Pair<Double, Double>>): String =
        """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},""" +
            """"geometry":{"type":"Polygon","coordinates":[${ring(points)}]}}]}"""

    fun lineJson(points: List<Pair<Double, Double>>): String =
        """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},""" +
            """"geometry":{"type":"LineString","coordinates":${ring(points)}}}]}"""

    fun pointJson(lat: Double, lon: Double): String =
        """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},""" +
            """"geometry":{"type":"Point","coordinates":[${num(lon)},${num(lat)}]}}]}"""

    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    private fun esc(s: String) = buildString {
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c.code < 0x20 -> append(' ')
            else -> append(c)
        }
    }

    fun aircraftJson(list: List<MapAircraft>): String =
        list.joinToString(",", """{"type":"FeatureCollection","features":[""", "]}") { a ->
            """{"type":"Feature","properties":{"id":"${esc(a.id)}","kind":"${a.kind.name}",""" +
                """"rot":${num(a.rotationDeg)},"label":"${esc(a.label)}"},""" +
                """"geometry":{"type":"Point","coordinates":[${num(a.lon)},${num(a.lat)}]}}"""
        }
}
