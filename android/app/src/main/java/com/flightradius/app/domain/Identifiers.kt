package com.flightradius.app.domain

/** A parsed identifier (normalized) plus its type. */
data class ParsedIdentifier(val identifier: String, val type: IdentifierType)

object Identifiers {

    val CALLSIGN_REGEX = Regex("^[A-Z0-9]{2,8}$")
    val ICAO24_REGEX = Regex("^[a-f0-9]{6}$", RegexOption.IGNORE_CASE)

    fun normalizeCallsign(raw: String): String = raw.trim().uppercase()
    fun normalizeIcao24(raw: String): String = raw.trim().lowercase()

    private val ICAO_PREFIX = Regex("^(icao(24)?|hex):", RegexOption.IGNORE_CASE)
    private val CALLSIGN_PREFIX = Regex("^callsign:", RegexOption.IGNORE_CASE)

    /**
     * Strips an optional "icao:" / "icao24:" / "callsign:" type prefix from a
     * bulk-input token. Returns (explicitType?, bareToken).
     */
    fun stripTypePrefix(token: String): Pair<IdentifierType?, String> {
        ICAO_PREFIX.find(token)?.let {
            return IdentifierType.ICAO24 to token.substring(it.value.length)
        }
        CALLSIGN_PREFIX.find(token)?.let {
            return IdentifierType.CALLSIGN to token.substring(it.value.length)
        }
        return null to token
    }

    fun isValidCallsign(raw: String): Boolean = CALLSIGN_REGEX.matches(normalizeCallsign(raw))
    fun isValidIcao24(raw: String): Boolean = ICAO24_REGEX.matches(raw.trim())

    /**
     * Classifies a raw identifier. Six-hex-character strings are ambiguous
     * (valid as both callsign and icao24) and default to CALLSIGN unless
     * [explicit] type is given.
     */
    fun classify(raw: String, explicit: IdentifierType? = null): IdentifierType? {
        val trimmed = raw.trim()
        if (explicit != null) {
            return when (explicit) {
                IdentifierType.CALLSIGN -> if (isValidCallsign(trimmed)) explicit else null
                IdentifierType.ICAO24 -> if (isValidIcao24(trimmed)) explicit else null
            }
        }
        if (isValidIcao24(trimmed) && trimmed.length == 6) {
            // Ambiguous 6-hex: valid callsign too -> default CALLSIGN.
            return IdentifierType.CALLSIGN
        }
        return if (isValidCallsign(trimmed)) IdentifierType.CALLSIGN else null
    }

    /** Normalizes [raw] for the given [type]; null when invalid. */
    fun normalize(raw: String, type: IdentifierType): String? {
        val trimmed = raw.trim()
        return when (type) {
            IdentifierType.CALLSIGN -> normalizeCallsign(trimmed).takeIf { isValidCallsign(trimmed) }
            IdentifierType.ICAO24 -> normalizeIcao24(trimmed).takeIf { isValidIcao24(trimmed) }
        }
    }

    /**
     * Splits free-form input on commas, whitespace and newlines; classifies,
     * normalizes and dedupes. Invalid tokens are dropped. Tokens are treated
     * as callsigns unless [explicitType] is given (e.g. a dedicated
     * "add by ICAO24" field) or the token carries an "icao:"/"callsign:"
     * prefix, which wins over [explicitType].
     */
    fun parseBulk(text: String, explicitType: IdentifierType? = null): List<ParsedIdentifier> {
        val seen = LinkedHashSet<String>()
        return text.split(Regex("[,\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                val (prefixed, bare) = stripTypePrefix(token)
                classify(bare, prefixed ?: explicitType)?.let { type ->
                    normalize(bare, type)?.let { ParsedIdentifier(it, type) }
                }
            }
            .filter { seen.add(it.identifier + "|" + it.type) }
    }
}
