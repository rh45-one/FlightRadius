package com.flightradius.app.domain

/** Minimal MAJOR.MINOR.PATCH comparison (leading "v" allowed; anything else is malformed). */
object SemVer {
    private val PATTERN = Regex("""v?(\d+)\.(\d+)\.(\d+)""")

    fun parse(raw: String?): List<Int>? {
        val m = PATTERN.matchEntire(raw?.trim() ?: return null) ?: return null
        return m.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    }

    /** Negative / zero / positive like compareTo; null when either side is malformed. */
    fun compare(a: String?, b: String?): Int? {
        val x = parse(a) ?: return null
        val y = parse(b) ?: return null
        for (i in 0 until 3) {
            if (x[i] != y[i]) return x[i].compareTo(y[i])
        }
        return 0
    }

    /** True only when [latest] is strictly newer than [current]; malformed means no update. */
    fun isNewer(latest: String?, current: String?): Boolean = (compare(latest, current) ?: 0) > 0

    fun strip(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")
}
