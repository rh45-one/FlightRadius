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
    val identifier: String,
    val type: IdentifierType?,
    val status: BulkAddStatus,
    var selected: Boolean = true,
    /** True when the raw token is valid as both callsign and icao24 —
     *  the UI offers a type-toggle badge. */
    val ambiguous: Boolean = false
)

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
     * INVALID rows so the user sees what was rejected.
     */
    fun parseBulk(
        text: String,
        existingNormalized: Set<String>
    ): List<BulkAddEntry> {
        val tokens = text.split(Regex("[,\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val seen = LinkedHashSet<String>()
        val out = ArrayList<BulkAddEntry>(tokens.size)
        for (token in tokens) {
            val (prefixed, bare) = Identifiers.stripTypePrefix(token)
            val type = Identifiers.classify(bare, prefixed)
            val normalized = type?.let { Identifiers.normalize(bare, it) }
            if (type == null || normalized == null) {
                out += BulkAddEntry(
                    identifier = token, type = null,
                    status = BulkAddStatus.INVALID, selected = false
                )
                continue
            }
            if (!seen.add(normalized)) continue // in-input duplicate
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
                identifier = normalized, type = type, status = status,
                selected = status == BulkAddStatus.NEW,
                ambiguous = ambiguous
            )
        }
        return out
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
        return entry.copy(
            identifier = normalized, type = newType, status = status,
            selected = status == BulkAddStatus.NEW
        )
    }
}
