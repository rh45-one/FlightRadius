package com.flightradius.app.ui.debug

import com.flightradius.app.domain.AircraftClass
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.AirspaceRule
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.Geo
import com.flightradius.app.domain.NearbyAircraft
import com.flightradius.app.domain.UserFix
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Synthetic nearby traffic around [fix] for GUI checks (debug console only). */
internal object DemoTraffic {

    private data class Spec(
        val callsign: String?,
        val registration: String?,
        val cls: AircraftClass,
        val distanceKm: Double,
        val bearingDeg: Double,
        val altitudeM: Double,
        val speedMps: Double,
        val trackDeg: Double,
        val typecode: String?,
        val model: String?
    )

    private val SPECS = listOf(
        Spec("PEGASO1", "EC-DEM", AircraftClass.HELICOPTER, 3.0, 40.0, 400.0, 45.0, 220.0, "EC35", "Airbus H135"),
        Spec("RYR4412", null, AircraftClass.AIRLINER, 14.0, 200.0, 9_800.0, 230.0, 75.0, "A320", "Airbus A320"),
        Spec(null, "EC-LPX", AircraftClass.LIGHT, 6.5, 130.0, 900.0, 55.0, 310.0, "C172", "Cessna 172"),
        Spec("GLDR7", null, AircraftClass.GLIDER, 11.0, 300.0, 1_800.0, 28.0, 120.0, null, null),
        Spec(null, null, AircraftClass.DRONE, 7.0, 250.0, 90.0, 12.0, 10.0, null, null),
        Spec("BALLOON2", null, AircraftClass.BALLOON, 9.0, 75.0, 600.0, 4.0, 95.0, null, null),
        Spec(null, null, AircraftClass.UNKNOWN, 18.0, 15.0, 3_200.0, 120.0, 190.0, null, null)
    )

    fun build(fix: UserFix, radiusKm: Double, rules: List<AirspaceRule>): List<NearbyAircraft> {
        val enabled = rules.filter { it.enabled }
        return SPECS.filter { it.distanceKm <= radiusKm }.mapIndexed { i, s ->
            val (lat, lon) = destination(fix.lat, fix.lon, s.bearingDeg, s.distanceKm)
            NearbyAircraft(
                icao24 = "de%04x".format(i + 1),
                callsign = s.callsign,
                cls = s.cls,
                lat = lat,
                lon = lon,
                distanceKm = s.distanceKm,
                bearingDeg = s.bearingDeg,
                altitudeM = s.altitudeM,
                velocityMps = s.speedMps,
                trackDeg = s.trackDeg,
                registration = s.registration,
                typecode = s.typecode,
                model = s.model,
                matchesRule = enabled.any { it.matches(s.distanceKm, s.altitudeM, s.cls) }
            )
        }.sortedBy { it.distanceKm }
    }

    /** Fake observations for up to three tracked aircraft; the rest report no data. */
    fun tracked(
        tracked: List<TrackedAircraft>,
        radiusKm: Double,
        fix: UserFix
    ): Pair<List<AircraftObservation>, List<TrackedAircraft>> {
        val distances = listOf(radiusKm * 0.8, radiusKm * 1.6, radiusKm * 3.4)
        val bearings = listOf(310.0, 95.0, 160.0)
        val observed = tracked.take(3).mapIndexed { i, t ->
            val (lat, lon) = destination(fix.lat, fix.lon, bearings[i], distances[i])
            AircraftObservation(
                aircraftId = t.id,
                callsign = t.identifier.takeIf { t.type == IdentifierType.CALLSIGN },
                icao24 = t.identifier.takeIf { t.type == IdentifierType.ICAO24 },
                distanceKm = distances[i], lat = lat, lon = lon,
                altitudeM = 10_500.0 - i * 4_000.0, velocityMps = 220.0 - i * 40.0,
                headingDeg = 40.0 + i * 100.0, bearingDeg = bearings[i],
                closingSpeedKmh = 300.0 - i * 250.0, effectiveRadiusKm = radiusKm
            )
        }.sortedBy { it.distanceKm }
        return observed to tracked.drop(3)
    }

    private fun destination(lat: Double, lon: Double, bearingDeg: Double, km: Double): Pair<Double, Double> {
        val r = 6371.0
        val d = km / r
        val b = Math.toRadians(bearingDeg)
        val p1 = Math.toRadians(lat)
        val l1 = Math.toRadians(lon)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return Math.toDegrees(p2) to Math.toDegrees(l2)
    }
}
