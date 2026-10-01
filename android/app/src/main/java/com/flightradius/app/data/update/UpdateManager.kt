package com.flightradius.app.data.update

import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.di.ApplicationScope
import com.flightradius.app.domain.SemVer
import com.flightradius.app.util.log.AppLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/** State of the "Check now" row in Settings. */
sealed interface ManualCheckState {
    data object Idle : ManualCheckState
    data object Checking : ManualCheckState
    data class UpToDate(val current: String) : ManualCheckState
    data class Available(val update: AvailableUpdate) : ManualCheckState {
        val version: String get() = update.version
        val url: String get() = update.url
    }
    data class Failed(val kind: UpdateFailure) : ManualCheckState
}

@Singleton
class UpdateManager @Inject constructor(
    private val checker: UpdateChecker,
    private val settings: SettingsRepository,
    private val currentVersion: CurrentVersion,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _manual = MutableStateFlow<ManualCheckState>(ManualCheckState.Idle)
    val manual: StateFlow<ManualCheckState> = _manual

    private val bannerShown = MutableStateFlow(false)

    /** The update to announce with the banner: newer than installed, once per process. */
    val bannerCandidate: StateFlow<AvailableUpdate?> = combine(
        settings.settings.map { it.availableUpdate }, bannerShown
    ) { update, shown ->
        update?.takeIf { !shown && SemVer.isNewer(it.version, currentVersion.name) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun markBannerShown() { bannerShown.value = true }

    /** Once per process: drop a stale persisted update, then check if the frequency says it's due. */
    fun checkOnLaunch(nowMs: Long = System.currentTimeMillis()) {
        scope.launch {
            val s = settings.settings.first()
            s.availableUpdate?.let {
                if (!SemVer.isNewer(it.version, currentVersion.name)) settings.setAvailableUpdate(null)
            }
            if (!s.updateFrequency.isDue(nowMs, s.lastUpdateCheckAtMs)) return@launch
            when (val r = checker.check()) {
                is UpdateCheckResult.Failed -> AppLog.w(TAG, "auto update check failed", "kind" to r.kind, "err" to r.detail)
                else -> record(r, nowMs)
            }
        }
    }

    fun checkNow() {
        if (_manual.value == ManualCheckState.Checking) return
        _manual.value = ManualCheckState.Checking
        scope.launch {
            val now = System.currentTimeMillis()
            _manual.value = when (val r = checker.check()) {
                is UpdateCheckResult.Failed -> {
                    AppLog.w(TAG, "update check failed", "kind" to r.kind, "err" to r.detail)
                    ManualCheckState.Failed(r.kind)
                }
                is UpdateCheckResult.UpToDate -> {
                    record(r, now); ManualCheckState.UpToDate(r.current)
                }
                is UpdateCheckResult.Available -> {
                    record(r, now); ManualCheckState.Available(availableOf(r.release))
                }
            }
        }
    }

    private suspend fun record(r: UpdateCheckResult, nowMs: Long) {
        settings.setLastUpdateCheck(nowMs)
        when (r) {
            is UpdateCheckResult.Available ->
                settings.setAvailableUpdate(availableOf(r.release))
            is UpdateCheckResult.UpToDate -> settings.setAvailableUpdate(null)
            is UpdateCheckResult.Failed -> Unit
        }
    }

    /** The release plus the APK for this device when one exists and its URL is trusted. */
    private fun availableOf(release: ReleaseInfo): AvailableUpdate {
        val asset = UpdateAssets.pick(release.assets, release.version, android.os.Build.SUPPORTED_ABIS)
            ?.takeIf { UpdateUrls.isSafeAssetUrl(
                it.url, com.flightradius.app.BuildConfig.UPDATE_REPO, UpdateConfig.relaxAssetHost) }
            ?.let { UpdateAsset(it.name, it.url, it.size, it.digest) }
        return AvailableUpdate(release.version, release.htmlUrl, asset)
    }

    private companion object {
        const val TAG = "UpdateManager"
    }
}

/** The installed versionName (injectable so tests can pin it). */
class CurrentVersion(val name: String)
