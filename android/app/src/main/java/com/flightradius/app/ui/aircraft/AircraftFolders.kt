package com.flightradius.app.ui.aircraft

import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.TrackedAircraft

/** One row of the Aircraft tab: folder headers, their aircraft, and the "No group" section. */
sealed interface FolderItem {
    /** True for the last row of its card (drives the rounded bottom corners). */
    val last: Boolean

    data class Header(
        val group: Fleet,
        /** All aircraft in the group, regardless of the search query. */
        val count: Int,
        val expanded: Boolean,
        override val last: Boolean
    ) : FolderItem

    data class Member(
        val aircraft: TrackedAircraft,
        /** null = ungrouped. */
        val groupId: Long?,
        override val last: Boolean,
        /** First row of a card without a header (flat list). */
        val first: Boolean = false
    ) : FolderItem

    data class EmptyGroup(val groupId: Long) : FolderItem {
        override val last: Boolean get() = true
    }

    data class UngroupedHeader(override val last: Boolean = false) : FolderItem
}

object AircraftFolders {

    private fun matches(a: TrackedAircraft, query: String): Boolean =
        query.isEmpty() || a.identifier.contains(query, ignoreCase = true) ||
            a.notes?.contains(query, ignoreCase = true) == true

    /**
     * Builds the list: groups by name, each with its aircraft (sorted by
     * identifier), then the ungrouped aircraft. With no groups at all the
     * result is the plain flat list. While searching, groups without a match
     * are hidden and groups with one are shown expanded.
     */
    fun build(
        groups: List<Fleet>,
        aircraft: List<TrackedAircraft>,
        query: String,
        collapsedIds: Set<Long>
    ): List<FolderItem> {
        val q = query.trim()
        val searching = q.isNotEmpty()
        val sorted = aircraft.sortedBy { it.identifier }
        if (groups.isEmpty()) {
            val shown = sorted.filter { matches(it, q) }
            return shown.mapIndexed { i, a ->
                FolderItem.Member(a, null, last = i == shown.lastIndex, first = i == 0)
            }
        }

        val groupOf = HashMap<Long, Long>()
        for (g in groups) for (id in g.memberIds) groupOf[id] = g.id

        val out = ArrayList<FolderItem>()
        for (g in groups.sortedBy { it.name.lowercase() }) {
            val all = sorted.filter { groupOf[it.id] == g.id }
            val shown = all.filter { matches(it, q) }
            if (searching && shown.isEmpty()) continue
            val expanded = searching || g.id !in collapsedIds
            when {
                !expanded -> out += FolderItem.Header(g, all.size, false, last = true)
                all.isEmpty() -> {
                    out += FolderItem.Header(g, 0, true, last = false)
                    out += FolderItem.EmptyGroup(g.id)
                }
                else -> {
                    out += FolderItem.Header(g, all.size, true, last = false)
                    shown.forEachIndexed { i, a ->
                        out += FolderItem.Member(a, g.id, last = i == shown.lastIndex)
                    }
                }
            }
        }
        val ungrouped = sorted.filter { it.id !in groupOf && matches(it, q) }
        if (ungrouped.isNotEmpty()) {
            out += FolderItem.UngroupedHeader()
            ungrouped.forEachIndexed { i, a ->
                out += FolderItem.Member(a, null, last = i == ungrouped.lastIndex)
            }
        }
        return out
    }

    /** Aircraft ids of one group as currently shown (for "Select all"). */
    fun idsOf(group: Fleet): Set<Long> = group.memberIds

    /** The previous group of each aircraft (null = ungrouped), for Undo. */
    fun previousAssignments(ids: Collection<Long>, groups: List<Fleet>): Map<Long, Long?> {
        val groupOf = HashMap<Long, Long>()
        for (g in groups) for (id in g.memberIds) groupOf[id] = g.id
        return ids.associateWith { groupOf[it] }
    }
}

/** What a "Move to group" did, kept for the snackbar and Undo. */
data class GroupMove(
    val previous: Map<Long, Long?>,
    val count: Int,
    /** Target group name; null = removed from group. */
    val targetName: String?
)

/** Where an aircraft's alert radius comes from (mirrors `effectiveRadiusKm`). */
enum class RadiusSource { OWN, GROUP, GLOBAL }

data class RadiusInfo(val km: Double, val source: RadiusSource)

/** Own radius, else the aircraft's group radius, else the global default. */
fun radiusInfo(aircraft: TrackedAircraft, group: Fleet?, globalKm: Double): RadiusInfo {
    aircraft.alertRadiusKm?.let { return RadiusInfo(it, RadiusSource.OWN) }
    group?.alertRadiusKm?.let { return RadiusInfo(it, RadiusSource.GROUP) }
    return RadiusInfo(globalKm, RadiusSource.GLOBAL)
}
