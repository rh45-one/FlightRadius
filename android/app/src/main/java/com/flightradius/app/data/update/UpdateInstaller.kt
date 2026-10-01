package com.flightradius.app.data.update

import android.content.Intent
import com.flightradius.app.util.log.AppLog
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** The system side of an install; behind an interface so the flow is testable with fakes. */
interface PackageInstallGateway {
    /** Whether "Install unknown apps" is granted for this app. */
    fun canRequestInstalls(): Boolean

    /** Commits a PackageInstaller session; the result comes back via [UpdateInstaller.onInstallStatus]. */
    fun commit(apk: File, version: String)
}

interface UpdateNotifier {
    fun progress(version: String, percent: Int)
    fun tapToInstall(version: String, confirm: Intent)
    fun clear()
}

/**
 * Downloads the chosen APK after the user asked for it, verifies size and
 * SHA-256, and hands it to the PackageInstaller. Unverified bytes are never
 * installed. Nothing happens in the background without [start] being called.
 */
class UpdateInstaller(
    private val client: OkHttpClient,
    private val gateway: PackageInstallGateway,
    private val notifier: UpdateNotifier,
    cacheDir: File,
    private val isMetered: () -> Boolean,
    private val isAppVisible: () -> Boolean,
    private val startConfirmation: (Intent) -> Unit,
    private val repo: String,
    private val relaxHosts: Boolean,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val dir = File(cacheDir, "updates")
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state

    private var job: Job? = null
    private var pendingUpdate: AvailableUpdate? = null
    private var pendingFile: File? = null
    private var pendingVersion: String? = null

    private val busy: Boolean
        get() = _state.value.let {
            it is InstallState.Downloading || it is InstallState.Verifying || it is InstallState.Installing
        }

    /** Starts (or, on mobile data, asks to confirm) the download of [update]'s APK. */
    fun start(update: AvailableUpdate, allowMetered: Boolean = false) {
        if (busy) return
        val asset = update.asset
        if (asset == null || !UpdateUrls.isSafeAssetUrl(asset.url, repo, relaxHosts)) {
            _state.value = InstallState.Failed(InstallFailure.GENERIC); return
        }
        val sha = UpdateAssets.parseSha256(asset.digest)
        if (sha == null) { _state.value = InstallState.Failed(InstallFailure.NO_DIGEST); return }
        if (!allowMetered && isMetered()) {
            pendingUpdate = update
            _state.value = InstallState.ConfirmMetered(asset.size)
            return
        }
        pendingUpdate = null
        _state.value = InstallState.Downloading(0)
        job = scope.launch { run(update.version, asset, sha) }
    }

    fun confirmMetered() {
        val u = pendingUpdate ?: return
        start(u, allowMetered = true)
    }

    /** Cancel a download or dismiss a pending question / failure. */
    fun cancel() {
        job?.cancel()
        job = null
        pendingUpdate = null
        pendingFile?.delete()
        pendingFile = null
        notifier.clear()
        _state.value = InstallState.Idle
    }

    fun dismissFailure() {
        if (_state.value is InstallState.Failed) _state.value = InstallState.Idle
    }

    /** Call from the activity's onResume: continues once "Install unknown apps" was granted. */
    fun onResume() {
        if (_state.value != InstallState.WaitingForPermission) return
        val file = pendingFile
        val version = pendingVersion
        if (file != null && version != null && gateway.canRequestInstalls()) {
            installNow(file, version)
        } else {
            // Not granted: keep the APK for the next attempt / app start.
            _state.value = InstallState.Idle
        }
    }

    /** Removes leftovers from earlier attempts (call at app start). */
    fun clearOldFiles() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** PackageInstaller session result, delivered by the receiver. */
    fun onInstallStatus(status: Int, confirmIntent: Intent?) {
        when (status) {
            InstallFailure.STATUS_PENDING_USER_ACTION -> {
                val intent = confirmIntent ?: return
                val version = pendingVersion ?: ""
                if (isAppVisible()) startConfirmation(intent) else notifier.tapToInstall(version, intent)
            }
            else -> {
                notifier.clear()
                pendingFile?.delete(); pendingFile = null
                _state.value = InstallFailure.fromStatus(status)
                    ?.let { InstallState.Failed(it) } ?: InstallState.Idle
            }
        }
    }

    private suspend fun run(version: String, asset: UpdateAsset, sha256: String) {
        dir.mkdirs()
        val file = File(dir, "FlightRadius-$version.apk")
        try {
            withContext(io) { download(version, asset, sha256, file) }
        } catch (ce: CancellationException) {
            file.delete(); throw ce
        } catch (e: VerificationFailed) {
            file.delete()
            _state.value = InstallState.Failed(e.kind); return
        } catch (e: IOException) {
            AppLog.w(TAG, "download failed", throwable = e)
            file.delete()
            _state.value = InstallState.Failed(InstallFailure.NETWORK); return
        }
        notifier.clear()
        pendingFile = file
        pendingVersion = version
        if (gateway.canRequestInstalls()) {
            installNow(file, version)
        } else {
            _state.value = InstallState.WaitingForPermission
        }
    }

    private suspend fun download(version: String, asset: UpdateAsset, sha256: String, file: File) {
        val request = Request.Builder().url(asset.url).get().build()
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = asset.size.takeIf { it > 0 } ?: body.contentLength()
            var lastPercent = -1
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        written += n
                        val percent = if (total > 0) (written * 100 / total).toInt().coerceIn(0, 100) else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = InstallState.Downloading(percent)
                            notifier.progress(version, percent)
                        }
                    }
                }
            }
        }
        _state.value = InstallState.Verifying
        if (asset.size > 0 && written != asset.size) throw VerificationFailed(InstallFailure.SIZE_MISMATCH)
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != sha256) throw VerificationFailed(InstallFailure.DIGEST_MISMATCH)
    }

    private fun installNow(file: File, version: String) {
        _state.value = InstallState.Installing
        try {
            gateway.commit(file, version)
        } catch (e: Exception) {
            AppLog.w(TAG, "install session failed", throwable = e)
            file.delete(); pendingFile = null
            _state.value = InstallState.Failed(InstallFailure.GENERIC)
        }
    }

    private class VerificationFailed(val kind: InstallFailure) : IOException(kind.name)

    companion object {
        private const val TAG = "UpdateInstaller"

        /**
         * Download client: every hop (including redirects) must be https unless the
         * debug-only relaxed flag is set. No credentials are ever attached.
         */
        fun downloadClient(relaxHosts: Boolean): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                if (!relaxHosts && !chain.request().url.isHttps) {
                    throw IOException("Refusing non-https update download")
                }
                chain.proceed(chain.request())
            }
            .build()
    }
}
