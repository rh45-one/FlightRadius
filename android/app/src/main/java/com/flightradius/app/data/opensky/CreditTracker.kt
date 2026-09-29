package com.flightradius.app.data.opensky

import com.flightradius.app.domain.CreditPlanner
import com.flightradius.app.domain.TimeSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What we currently know about the OpenSky `/states` credit bucket. */
data class CreditState(
    /** Last `X-Rate-Limit-Remaining` value, null when never observed. */
    val remaining: Int? = null,
    val observedAtMs: Long? = null,
    /** Whether the observation was made with an API client token. */
    val authenticated: Boolean = false,
    /** Requests are pointless until this time (after a 429). */
    val blockedUntilMs: Long? = null
) {
    val dailyQuota: Int get() = CreditPlanner.dailyQuota(authenticated, remaining)
}

/**
 * Single source of truth for credit state, fed by every OpenSky response
 * (direct mode) or the backend's credits header (backend mode).
 */
@Singleton
class CreditTracker @Inject constructor(private val time: TimeSource) {

    private val _state = MutableStateFlow(CreditState())
    val state: StateFlow<CreditState> = _state.asStateFlow()

    fun onResponse(remaining: Int?, authenticated: Boolean) {
        _state.update {
            it.copy(
                remaining = remaining ?: it.remaining,
                observedAtMs = if (remaining != null) time.nowMs() else it.observedAtMs,
                authenticated = authenticated,
                blockedUntilMs = null
            )
        }
    }

    /**
     * Records a 429. Without a server hint we back off for
     * [DEFAULT_BLOCK_MS] rather than guessing the refill boundary.
     */
    fun onRateLimited(retryAfterSec: Long?, authenticated: Boolean) {
        val now = time.nowMs()
        val blockMs = retryAfterSec?.takeIf { it > 0 }?.times(1000) ?: DEFAULT_BLOCK_MS
        _state.update {
            it.copy(
                remaining = 0,
                observedAtMs = now,
                authenticated = authenticated,
                blockedUntilMs = now + blockMs
            )
        }
    }

    /** Seconds until requests may succeed again, or null when not blocked. */
    fun blockedForSec(): Long? {
        val until = _state.value.blockedUntilMs ?: return null
        val left = until - time.nowMs()
        return if (left > 0) (left + 999) / 1000 else null
    }

    /** Credentials changed: the old observation belongs to another bucket. */
    fun reset() {
        _state.value = CreditState()
    }

    companion object {
        const val DEFAULT_BLOCK_MS = 15 * 60_000L
    }
}
