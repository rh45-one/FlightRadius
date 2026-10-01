package com.flightradius.app.data.update

import com.flightradius.app.data.net.await
import com.flightradius.app.domain.SemVer
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0L,
    /** GitHub's "sha256:<hex>" form; null on older releases. */
    val digest: String? = null
)

@Serializable
data class ReleaseInfo(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val htmlUrl: String,
    val name: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<ReleaseAsset> = emptyList()
) {
    /** Version without the leading "v". */
    val version: String get() = SemVer.strip(tag)
}

/** The APK chosen for this device, with what is needed to verify it. */
data class UpdateAsset(val name: String, val url: String, val size: Long, val digest: String?)

/**
 * A newer release that was found (persisted so the banner can show on later
 * launches). [asset] is null when no installable APK exists: the UI then only
 * offers the release page.
 */
data class AvailableUpdate(val version: String, val url: String, val asset: UpdateAsset? = null)

enum class UpdateFailure { RATE_LIMITED, NETWORK, OTHER }

/** Release page URLs are only trusted when they point at github.com over https. */
object UpdateUrls {
    fun fallback(repo: String) = "https://github.com/$repo/releases/latest"

    /**
     * APK downloads must come from this repo's release assets on github.com over
     * https. [relaxed] (debug builds with the test flag only) accepts any host.
     */
    fun isSafeAssetUrl(url: String?, repo: String, relaxed: Boolean = false): Boolean {
        val uri = try { java.net.URI(url?.trim().orEmpty()) } catch (_: Exception) { return false }
        if (uri.userInfo != null) return false
        if (relaxed) return uri.scheme.equals("https", true) || uri.scheme.equals("http", true)
        return uri.scheme.equals("https", true) &&
            uri.host.equals("github.com", true) &&
            uri.rawPath?.startsWith("/$repo/releases/download/") == true
    }

    fun safe(url: String?, repo: String): String {
        val uri = try { java.net.URI(url?.trim().orEmpty()) } catch (_: Exception) { null }
        val ok = uri != null &&
            uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.userInfo == null
        return if (ok) url!!.trim() else fallback(repo)
    }
}

sealed interface UpdateCheckResult {
    data class UpToDate(val current: String) : UpdateCheckResult
    data class Available(val release: ReleaseInfo) : UpdateCheckResult
    /** [kind] drives the user-facing text; [detail] is raw and only meant for logs. */
    data class Failed(val kind: UpdateFailure, val detail: String) : UpdateCheckResult
}

/** Asks GitHub for the latest release of [repo] and compares it with [currentVersion]. */
class UpdateChecker(
    private val client: OkHttpClient,
    private val apiBase: String,
    private val repo: String,
    private val currentVersion: String
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateCheckResult {
        val request = Request.Builder()
            .url("${apiBase.trimEnd('/')}/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()
        return try {
            client.newCall(request).await().use { r ->
                when {
                    r.code == 404 -> UpdateCheckResult.UpToDate(currentVersion)
                    r.code == 403 || r.code == 429 ->
                        UpdateCheckResult.Failed(UpdateFailure.RATE_LIMITED, "HTTP ${r.code}")
                    !r.isSuccessful -> UpdateCheckResult.Failed(UpdateFailure.OTHER, "HTTP ${r.code}")
                    else -> {
                        val release = json.decodeFromString<ReleaseInfo>(r.body.string())
                        if (SemVer.isNewer(release.version, currentVersion)) {
                            UpdateCheckResult.Available(
                                release.copy(htmlUrl = UpdateUrls.safe(release.htmlUrl, repo)))
                        } else {
                            UpdateCheckResult.UpToDate(currentVersion)
                        }
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IOException) {
            UpdateCheckResult.Failed(UpdateFailure.NETWORK, e.message ?: "network error")
        } catch (e: Exception) {
            UpdateCheckResult.Failed(UpdateFailure.OTHER, e.message ?: "unexpected response")
        }
    }
}
