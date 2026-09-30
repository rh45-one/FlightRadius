package com.flightradius.app.domain

/** Coarse aircraft type used for nearby-airspace rules and icons. */
enum class AircraftClass(val code: Int) {
    AIRLINER(0), LIGHT(1), HELICOPTER(2), GLIDER(3), BALLOON(4),
    DRONE(5), MILITARY(6), UNKNOWN(7);

    companion object {
        fun fromCode(code: Int): AircraftClass =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** Stored per-icao24 knowledge from the aircraft database. */
data class AircraftMeta(
    val cls: AircraftClass,
    val typecode: String? = null,
    val registration: String? = null,
    val model: String? = null,
    val operator: String? = null
)

/** One doc8643 type row: class description (e.g. `L2J`) and wake category. */
data class Doc8643Type(val description: String, val wtc: String?)

object AircraftClassifier {

    /** ADS-B emitter category first, then the stored database class. */
    fun classify(category: Int?, meta: AircraftMeta?): AircraftClass {
        fromCategory(category)?.let { return it }
        meta?.cls?.takeIf { it != AircraftClass.UNKNOWN }?.let { return it }
        return AircraftClass.UNKNOWN
    }

    fun fromCategory(category: Int?): AircraftClass? = when (category) {
        2, 12 -> AircraftClass.LIGHT
        3, 4, 5, 6 -> AircraftClass.AIRLINER
        7 -> AircraftClass.MILITARY
        8 -> AircraftClass.HELICOPTER
        9 -> AircraftClass.GLIDER
        10 -> AircraftClass.BALLOON
        14 -> AircraftClass.DRONE
        else -> null
    }
}

/** Import-time class derivation from one aircraft-database row. */
object DbClassMapper {

    fun classify(
        icaoAircraftClass: String?,
        typecode: String?,
        categoryDescription: String?,
        doc8643: Map<String, Doc8643Type>
    ): AircraftClass {
        val type = typecode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.let { doc8643[it] }
        val wtc = type?.wtc
        fromClassString(icaoAircraftClass, wtc)?.let { return it }
        type?.let { fromClassString(it.description, it.wtc) }?.let { return it }
        return fromCategoryDescription(categoryDescription) ?: AircraftClass.UNKNOWN
    }

    /** `L2J`, `H1T`, `G1P`, ... -> class, null when undecidable. */
    fun fromClassString(raw: String?, wtc: String?): AircraftClass? {
        val s = raw?.trim()?.uppercase() ?: return null
        if (s.length < 3) return null
        return when (s[0]) {
            'H', 'G', 'T' -> AircraftClass.HELICOPTER
            'L', 'S', 'A' -> when (s[2]) {
                'P', 'E' -> AircraftClass.LIGHT
                'J', 'T' ->
                    if (wtc?.trim()?.uppercase() == "L") AircraftClass.LIGHT
                    else AircraftClass.AIRLINER
                else -> null
            }
            else -> null
        }
    }

    fun fromCategoryDescription(raw: String?): AircraftClass? {
        val d = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            "rotorcraft" in d -> AircraftClass.HELICOPTER
            "glider" in d -> AircraftClass.GLIDER
            "lighter" in d -> AircraftClass.BALLOON
            "uav" in d -> AircraftClass.DRONE
            "light" in d -> AircraftClass.LIGHT
            "large" in d || "heavy" in d || "small" in d -> AircraftClass.AIRLINER
            else -> null
        }
    }
}
