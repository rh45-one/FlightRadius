package com.flightradius.app.domain

/** How often the app looks for a newer release on launch. */
enum class UpdateFrequency(val intervalMs: Long?) {
    ON_LAUNCH(0L),
    DAILY(24L * 60 * 60 * 1000),
    WEEKLY(7L * 24 * 60 * 60 * 1000),
    NEVER(null);

    /** True when a check is due at [nowMs] given the last check time (0 = never checked). */
    fun isDue(nowMs: Long, lastCheckMs: Long): Boolean {
        val interval = intervalMs ?: return false
        if (this == ON_LAUNCH || lastCheckMs <= 0L) return true
        // A clock that went backwards must not postpone checks forever.
        return nowMs < lastCheckMs || nowMs - lastCheckMs >= interval
    }
}
