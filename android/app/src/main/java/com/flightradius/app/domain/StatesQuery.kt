package com.flightradius.app.domain

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** WGS84 bounding box in decimal degrees (no antimeridian wrap). */
data class BoundingBox(
    val latMin: Double,
    val lonMin: Double,
    val latMax: Double,
    val lonMax: Double
) {
    /** OpenSky bills area as latitude range × longitude range (sq°). */
    val areaSqDeg: Double get() = (latMax - latMin) * (lonMax - lonMin)

    companion object {
        private const val KM_PER_DEG_LAT = 111.32

        /**
         * Smallest box containing a circle of [radiusKm] around a point,
         * clamped to valid coordinates. Near the poles the longitude span
         * saturates at the full range; boxes crossing the antimeridian are
         * clipped at ±180° (OpenSky boxes cannot wrap).
         */
        fun around(lat: Double, lon: Double, radiusKm: Double): BoundingBox {
            val dLat = radiusKm / KM_PER_DEG_LAT
            val cosLat = cos(Math.toRadians(lat))
            val dLon = if (cosLat < 1e-6) 180.0
            else min(180.0, radiusKm / (KM_PER_DEG_LAT * cosLat))
            return BoundingBox(
                latMin = max(-90.0, lat - dLat),
                lonMin = max(-180.0, lon - dLon),
                latMax = min(90.0, lat + dLat),
                lonMax = min(180.0, lon + dLon)
            )
        }
    }
}

/** The three `/states/all` query shapes the app issues. */
sealed interface StatesQuery {
    /** Every aircraft worldwide (4 credits). */
    data object Global : StatesQuery

    /** Specific transponders (1 credit per request). */
    data class ByIcao24(val icao24s: List<String>) : StatesQuery

    /** Everything inside a box (1–4 credits by area). */
    data class ByArea(val box: BoundingBox) : StatesQuery
}

/** OpenSky `/states/all` credit pricing (REST docs, "API Credits"). */
object OpenSkyPricing {
    /** icao24 filters per request; OpenSky bills a filtered request as 1. */
    const val ICAO24_CHUNK_SIZE = 100

    fun credits(query: StatesQuery): Int = when (query) {
        StatesQuery.Global -> 4
        is StatesQuery.ByIcao24 ->
            if (query.icao24s.isEmpty()) 0
            else (query.icao24s.size + ICAO24_CHUNK_SIZE - 1) / ICAO24_CHUNK_SIZE
        is StatesQuery.ByArea -> areaCredits(query.box.areaSqDeg)
    }

    fun areaCredits(areaSqDeg: Double): Int = when {
        areaSqDeg <= 25.0 -> 1
        areaSqDeg <= 100.0 -> 2
        areaSqDeg <= 400.0 -> 3
        else -> 4
    }
}
