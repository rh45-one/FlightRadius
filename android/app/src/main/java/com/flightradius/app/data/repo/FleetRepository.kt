package com.flightradius.app.data.repo

import com.flightradius.app.data.db.FleetDao
import com.flightradius.app.data.db.FleetEntity
import com.flightradius.app.data.db.FleetMemberEntity
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.GroupIcon
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Palette fallback when a backend fleet color string can't be parsed. */
val FLEET_COLOR_PALETTE = intArrayOf(
    0xFF22D3EE.toInt(), // cyan-400
    0xFF6366F1.toInt(), // indigo-500
    0xFF34D399.toInt(), // emerald-400
    0xFFFBBF24.toInt(), // amber-400
    0xFFFB7185.toInt(), // rose-400
    0xFFA78BFA.toInt(), // violet-400
    0xFF4ADE80.toInt(), // green-400
    0xFFF472B6.toInt()  // pink-400
)

/** Parses "#RRGGBB"/"#AARRGGBB" (also without '#'); null when unparseable. */
fun parseHexColorArgb(raw: String?): Int? {
    val s = raw?.trim()?.removePrefix("#") ?: return null
    return when (s.length) {
        6 -> s.toLongOrNull(16)?.toInt()?.let { it or 0xFF000000.toInt() }
        8 -> s.toLongOrNull(16)?.toInt()
        else -> null
    }
}

@Singleton
class FleetRepository @Inject constructor(
    private val fleetDao: FleetDao
) {
    val fleets: Flow<List<Fleet>> =
        fleetDao.observeFleetsWithMembers().map { list ->
            list.map { it.toDomain() }
        }

    suspend fun getAll(): List<Fleet> =
        fleetDao.getFleetsWithMembers().map { it.toDomain() }

    /** Returns the fleet id (creating it if missing), or null on failure. */
    suspend fun getOrCreate(
        name: String,
        colorArgb: Int? = null,
        alertRadiusKm: Double? = null,
        icon: GroupIcon = GroupIcon.PLANE
    ): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        fleetDao.findByName(trimmed)?.let { return it.id }
        val index = fleetDao.getFleetsWithMembers().size
        val rowId = fleetDao.insertIgnore(
            FleetEntity(
                name = trimmed,
                colorArgb = colorArgb ?: FLEET_COLOR_PALETTE[index % FLEET_COLOR_PALETTE.size],
                alertRadiusKm = alertRadiusKm,
                createdAt = System.currentTimeMillis(),
                iconKey = icon.name
            )
        )
        return if (rowId > 0) rowId else fleetDao.findByName(trimmed)?.id
    }

    suspend fun update(fleet: Fleet) {
        val existing = fleetDao.findById(fleet.id) ?: return
        fleetDao.update(
            existing.copy(
                name = fleet.name,
                colorArgb = fleet.colorArgb,
                alertRadiusKm = fleet.alertRadiusKm,
                iconKey = fleet.icon.name
            )
        )
    }

    suspend fun remove(fleetId: Long) = fleetDao.deleteById(fleetId)

    /**
     * Puts the aircraft into [fleetId] (null = no group) in one transaction,
     * replacing any previous membership.
     */
    suspend fun setGroup(aircraftIds: Collection<Long>, fleetId: Long?) {
        if (aircraftIds.isEmpty()) return
        fleetDao.applyAssignments(aircraftIds.associateWith { fleetId })
    }

    /** Restores exact previous memberships (aircraft id -> group id or null). */
    suspend fun applyAssignments(assignments: Map<Long, Long?>) {
        if (assignments.isEmpty()) return
        fleetDao.applyAssignments(assignments)
    }

    /**
     * Adds a membership only when the aircraft has no group yet (backend import
     * keeps the first group encountered). Returns true when it was added.
     */
    suspend fun addMemberIfUngrouped(fleetId: Long, aircraftId: Long): Boolean =
        fleetDao.addMember(FleetMemberEntity(fleetId = fleetId, aircraftId = aircraftId)) > 0

    private fun com.flightradius.app.data.db.FleetWithMembers.toDomain() = Fleet(
        id = fleet.id,
        name = fleet.name,
        colorArgb = fleet.colorArgb,
        alertRadiusKm = fleet.alertRadiusKm,
        memberIds = memberIds.toSet(),
        icon = GroupIcon.fromKey(fleet.iconKey)
    )
}
