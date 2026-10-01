package com.flightradius.app.ui.aircraft

import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.Identifiers
import com.flightradius.app.domain.ParsedIdentifier

/** Result of validating one add-aircraft identifier. */
enum class AddValidation {
    EMPTY,
    INVALID_FORMAT,
    DUPLICATE,
    VALID
}

/** One parsed bulk-add row and its classification. */
data class BulkAddEntry(
    /** Stable row key (index of the token in the input); never changes on retype. */
    val id: Int = 0,
    val identifier: String,
    val type: IdentifierType?,
    val status: BulkAddStatus,
    var selected: Boolean = true,
    /** True when the raw token is valid as both callsign and icao24 —
     *  the UI offers a type-toggle badge. */
    val ambiguous: Boolean = false,
    /** For INVALID rows: the type the user forced via [BulkMode], if any. */
    val invalidFor: IdentifierType? = null
)

/** How bulk tokens without an explicit prefix are interpreted. */
enum class BulkMode { AUTO, CALLSIGNS, ICAO24 }

enum class BulkAddStatus {
    /** Valid format, not already tracked. */
    NEW,
    /** Valid format but already tracked (not insertable). */
    ALREADY_TRACKED,
    /** Input token that couldn't be classified. */
    INVALID
}

object AircraftForms {

    fun validate(
        raw: String,
        type: IdentifierType,
        existingNormalized: Set<String>
    ): AddValidation {
        if (raw.isBlank()) return AddValidation.EMPTY
        val normalized = Identifiers.normalize(raw, type)
            ?: return AddValidation.INVALID_FORMAT
        return if (normalized in existingNormalized) {
            AddValidation.DUPLICATE
        } else {
            AddValidation.VALID
        }
    }

    /** Best-effort type guess for the input field's "looks like" hint. */
    fun suggestType(raw: String): IdentifierType? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (Identifiers.isValidIcao24(t)) return IdentifierType.ICAO24
        if (Identifiers.isValidCallsign(t)) return IdentifierType.CALLSIGN
        return null
    }

    /**
     * Splits free-form input (comma/newline/whitespace), classifies each
     * token, dedupes valid identifiers, and marks already-tracked entries.
     * Order of first appearance is preserved; invalid tokens are kept as
     * INVALID rows so the user sees what was rejected. [mode] forces every
     * unprefixed token to one type; an explicit `icao:`/`callsign:` prefix wins.
     * Row ids are the token indices, so they are stable for the same text.
     */
    fun parseBulk(
        text: String,
        existingNormalized: Set<String>,
        mode: BulkMode = BulkMode.AUTO
    ): List<BulkAddEntry> {
        val forced = when (mode) {
            BulkMode.AUTO -> null
            BulkMode.CALLSIGNS -> IdentifierType.CALLSIGN
            BulkMode.ICAO24 -> IdentifierType.ICAO24
        }
        val tokens = text.split(Regex("[,\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val seen = LinkedHashSet<String>()
        val out = ArrayList<BulkAddEntry>(tokens.size)
        tokens.forEachIndexed { index, token ->
            val (prefixed, bare) = Identifiers.stripTypePrefix(token)
            val type = Identifiers.classify(bare, prefixed ?: forced)
            val normalized = type?.let { Identifiers.normalize(bare, it) }
            if (type == null || normalized == null) {
                out += BulkAddEntry(
                    id = index, identifier = token, type = null,
                    status = BulkAddStatus.INVALID, selected = false,
                    invalidFor = if (prefixed == null) forced else prefixed
                )
                return@forEachIndexed
            }
            if (!seen.add(normalized + "|" + type)) return@forEachIndexed // in-input duplicate
            val status = if (normalized in existingNormalized) {
                BulkAddStatus.ALREADY_TRACKED
            } else {
                BulkAddStatus.NEW
            }
            // 6-hex tokens are valid as both types (only when unprefixed).
            val ambiguous = prefixed == null && bare.length == 6 &&
                Identifiers.isValidCallsign(bare) &&
                Identifiers.isValidIcao24(bare)
            out += BulkAddEntry(
                id = index, identifier = normalized, type = type, status = status,
                selected = status == BulkAddStatus.NEW,
                ambiguous = ambiguous
            )
        }
        return out
    }

    /**
     * Re-derives the list for a new [mode] from the same text, keeping the
     * user's checkbox choice for rows that still exist (same id) and are NEW.
     */
    fun reparse(
        text: String,
        existingNormalized: Set<String>,
        mode: BulkMode,
        previous: List<BulkAddEntry>
    ): List<BulkAddEntry> {
        val before = previous.associateBy { it.id }
        return parseBulk(text, existingNormalized, mode).map { e ->
            val old = before[e.id]
            if (old != null && old.status == BulkAddStatus.NEW &&
                e.status == BulkAddStatus.NEW
            ) e.copy(selected = old.selected) else e
        }
    }

    /**
     * Flips the type of the row with [id] (callsign <-> icao24), if it exists
     * and is ambiguous. Rows are matched by id because the identifier itself
     * changes case on retype.
     */
    fun toggleType(
        entries: List<BulkAddEntry>,
        id: Int,
        existingNormalized: Set<String>
    ): List<BulkAddEntry> = entries.map { e ->
        if (e.id != id || !e.ambiguous) e else {
            val newType = if (e.type == IdentifierType.ICAO24) {
                IdentifierType.CALLSIGN
            } else IdentifierType.ICAO24
            retype(e, newType, existingNormalized)
        }
    }

    /**
     * Re-derives an entry after the user toggles its type badge
     * (callsign <-> icao24). Only meaningful for [BulkAddEntry.ambiguous].
     */
    fun retype(
        entry: BulkAddEntry,
        newType: IdentifierType,
        existingNormalized: Set<String>
    ): BulkAddEntry {
        val normalized = Identifiers.normalize(entry.identifier, newType)
            ?: return entry
        val status = if (normalized in existingNormalized) {
            BulkAddStatus.ALREADY_TRACKED
        } else {
            BulkAddStatus.NEW
        }
        val keepChoice = entry.status == BulkAddStatus.NEW && status == BulkAddStatus.NEW
        return entry.copy(
            identifier = normalized, type = newType, status = status,
            selected = if (keepChoice) entry.selected else status == BulkAddStatus.NEW
        )
    }
}
