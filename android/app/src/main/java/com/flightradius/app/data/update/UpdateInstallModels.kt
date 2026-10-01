package com.flightradius.app.data.update

import com.flightradius.app.BuildConfig

/** Debug-only switches for end-to-end tests; release builds are pinned to GitHub. */
object UpdateConfig {
    const val DEFAULT_API_BASE = "https://api.github.com"

    /** Pure so a unit test can prove release ignores the relaxed flag. */
    fun effectiveRelax(debug: Boolean, flag: Boolean) = debug && flag

    fun effectiveApiBase(debug: Boolean, configured: String) =
        if (debug) configured else DEFAULT_API_BASE

    val relaxAssetHost = effectiveRelax(BuildConfig.DEBUG, BuildConfig.UPDATE_RELAX_ASSET_HOST)
    val apiBase = effectiveApiBase(BuildConfig.DEBUG, BuildConfig.UPDATE_API_BASE)
}

object UpdateAssets {
    /**
     * `FlightRadius-<v>-arm64-v8a.apk` on arm64 devices, otherwise
     * `FlightRadius-<v>-universal.apk`; null when the release has neither.
     */
    fun pick(assets: List<ReleaseAsset>, version: String, supportedAbis: Array<String>): ReleaseAsset? {
        val wanted = buildList {
            if ("arm64-v8a" in supportedAbis) add("FlightRadius-$version-arm64-v8a.apk")
            add("FlightRadius-$version-universal.apk")
        }
        for (name in wanted) assets.firstOrNull { it.name == name }?.let { return it }
        return null
    }

    /** "sha256:<64 hex>" -> lowercase hex, or null for anything else. */
    fun parseSha256(digest: String?): String? {
        val d = digest?.trim() ?: return null
        if (!d.startsWith("sha256:", ignoreCase = true)) return null
        val hex = d.substring(7).lowercase()
        return hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }
}

enum class InstallFailure {
    NETWORK, SIZE_MISMATCH, DIGEST_MISMATCH,
    /** The release has no usable digest: never install, offer the release page. */
    NO_DIGEST,
    /** Signed with a different key than the installed app. */
    SIGNATURE, STORAGE, GENERIC;

    /** Failures where "Open release page" is the useful next step. */
    val offersReleasePage: Boolean
        get() = this == NO_DIGEST || this == SIGNATURE || this == DIGEST_MISMATCH || this == SIZE_MISMATCH

    companion object {
        // PackageInstaller.STATUS_* values (kept literal so this stays JVM-testable).
        const val STATUS_PENDING_USER_ACTION = -1
        const val STATUS_SUCCESS = 0
        const val STATUS_FAILURE = 1
        const val STATUS_FAILURE_BLOCKED = 2
        const val STATUS_FAILURE_ABORTED = 3
        const val STATUS_FAILURE_INVALID = 4
        const val STATUS_FAILURE_CONFLICT = 5
        const val STATUS_FAILURE_STORAGE = 6
        const val STATUS_FAILURE_INCOMPATIBLE = 7

        /** Maps a final install status to a failure; null = success/cancel/pending (not a failure). */
        fun fromStatus(status: Int): InstallFailure? = when (status) {
            STATUS_SUCCESS, STATUS_PENDING_USER_ACTION, STATUS_FAILURE_ABORTED -> null
            STATUS_FAILURE_CONFLICT, STATUS_FAILURE_INCOMPATIBLE -> SIGNATURE
            STATUS_FAILURE_STORAGE -> STORAGE
            else -> GENERIC
        }
    }
}

sealed interface InstallState {
    data object Idle : InstallState
    /** On mobile data: waiting for the user to confirm a download of [sizeBytes]. */
    data class ConfirmMetered(val sizeBytes: Long) : InstallState
    data class Downloading(val percent: Int) : InstallState
    data object Verifying : InstallState
    /** "Install unknown apps" is not granted yet. */
    data object WaitingForPermission : InstallState
    data object Installing : InstallState
    data class Failed(val kind: InstallFailure) : InstallState
}
