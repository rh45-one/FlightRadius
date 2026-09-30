package com.flightradius.app.data.aircraftdb

import com.flightradius.app.domain.AircraftClass
import com.flightradius.app.domain.AircraftMeta
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Per-cycle icao24 lookups with an in-memory LRU in front of Room. */
@Singleton
class AircraftMetaRepository @Inject constructor(
    private val db: AircraftMetaDatabase,
    private val manager: AircraftDatabaseManager
) {
    private companion object {
        const val CACHE_SIZE = 2_000
    }

    /** Access-ordered; a null value caches "not in the database". */
    private val cache = object : LinkedHashMap<String, AircraftMeta?>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AircraftMeta?>?) =
            size > CACHE_SIZE
    }

    init {
        manager.onDataChanged = { synchronized(cache) { cache.clear() } }
    }

    suspend fun lookup(icao24s: Collection<String>): Map<String, AircraftMeta> {
        if (!manager.hasData || icao24s.isEmpty()) return emptyMap()
        val out = HashMap<String, AircraftMeta>()
        val missing = ArrayList<String>()
        synchronized(cache) {
            for (id in icao24s) {
                if (cache.containsKey(id)) cache[id]?.let { out[id] = it } else missing += id
            }
        }
        for (chunk in missing.chunked(500)) {
            val found = db.dao().findByIds(chunk).associateBy { it.icao24 }
            synchronized(cache) {
                for (id in chunk) {
                    val meta = found[id]?.toMeta()
                    cache[id] = meta
                    if (meta != null) out[id] = meta
                }
            }
        }
        return out
    }

    /** Exact, case-insensitive registration match (e.g. `EC-KZX`). */
    suspend fun findByRegistration(raw: String): Pair<String, AircraftMeta>? {
        if (!manager.hasData) return null
        val reg = raw.trim().uppercase(Locale.ROOT)
        if (reg.length < 3) return null
        val row = db.dao().findByRegistration(reg) ?: return null
        return row.icao24 to row.toMeta()
    }

    private fun AircraftMetaEntity.toMeta() =
        AircraftMeta(AircraftClass.fromCode(cls), typecode, registration, model, operator)
}
