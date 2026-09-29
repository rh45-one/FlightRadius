package com.flightradius.app.data.opensky

import com.flightradius.app.domain.StateVector
import com.flightradius.app.domain.StatesSnapshot
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Parses `/states/all` bodies. State vectors are positional arrays of mixed
 * types, so this walks the JSON tree instead of using generated serializers.
 * A malformed top level throws [SerializationException]; individual
 * malformed rows are skipped so one bad vector never loses the snapshot.
 */
object StateVectorParser {

    fun parse(json: Json, body: String): StatesSnapshot {
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw SerializationException("states response is not a JSON object")
        val rows = root["states"] as? JsonArray
        return StatesSnapshot(
            timeSec = root["time"].primitive()?.longOrNull,
            states = rows?.mapNotNull { (it as? JsonArray)?.let(::parseRow) }.orEmpty()
        )
    }

    /** Field indices per the OpenSky REST docs ("State Vectors"). */
    internal fun parseRow(row: JsonArray): StateVector? {
        val icao24 = row.string(0)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: return null
        return StateVector(
            icao24 = icao24,
            callsign = row.string(1)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            originCountry = row.string(2),
            lastContactSec = row.long(4),
            lon = row.double(5)?.takeIf { it in -180.0..180.0 },
            lat = row.double(6)?.takeIf { it in -90.0..90.0 },
            baroAltitudeM = row.double(7),
            onGround = row.boolean(8),
            velocityMps = row.double(9),
            trackDeg = row.double(10),
            verticalRateMps = row.double(11),
            geoAltitudeM = row.double(13),
            squawk = row.string(14),
            positionSource = row.long(16)?.toInt(),
            category = row.long(17)?.toInt()
        )
    }

    private fun JsonElement?.primitive(): JsonPrimitive? =
        (this as? JsonPrimitive)?.takeUnless { it is JsonNull }

    private fun JsonArray.at(i: Int): JsonPrimitive? = getOrNull(i).primitive()

    private fun JsonArray.string(i: Int): String? = at(i)?.takeIf { it.isString }?.content

    private fun JsonArray.double(i: Int): Double? =
        at(i)?.takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

    private fun JsonArray.long(i: Int): Long? =
        at(i)?.takeUnless { it.isString }?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

    private fun JsonArray.boolean(i: Int): Boolean? = at(i)?.takeUnless { it.isString }?.booleanOrNull
}
