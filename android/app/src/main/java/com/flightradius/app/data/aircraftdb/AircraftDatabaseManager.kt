package com.flightradius.app.data.aircraftdb

import com.flightradius.app.ui.format.W
import com.flightradius.app.ui.format.Words
import com.flightradius.app.data.net.await
import com.flightradius.app.data.prefs.AircraftDbMeta
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.di.ApplicationScope
import com.flightradius.app.di.OpenSkyHttp
import com.flightradius.app.domain.TimeSource
import com.flightradius.app.service.ConnectivityMonitor
import com.flightradius.app.util.log.AppLog
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface DbState {
    data object NotDownloaded : DbState
    data class Downloading(val progress: Float) : DbState
    data class Ready(
        val importedAtMs: Long,
        val rowCount: Int,
        val classifiedCount: Int = 0,
        val sourceKey: String = "",
        val updateAvailable: Boolean = false
    ) : DbState
    data class Failed(val message: String) : DbState
}

/**
 * Downloads the OpenSky aircraft database and imports it into the separate
 * `aircraft_meta.db`. The CSV is streamed, never stored. Runs in the app
 * scope so it survives leaving Settings; an interrupted or failed import
 * leaves the previous data in place (the swap is the last, atomic step).
 */
@Singleton
class AircraftDatabaseManager @Inject constructor(
    @OpenSkyHttp baseHttp: OkHttpClient,
    private val db: AircraftMetaDatabase,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityMonitor,
    private val time: TimeSource,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AircraftDb"
        const val LIST_URL =
            "https://s3.opensky-network.org/data-samples?list-type=2&prefix=metadata/aircraft-database-complete-"
        const val BASE_URL = "https://s3.opensky-network.org/data-samples/"
        const val DOC8643_KEY = "metadata/doc8643AircraftTypes.csv"
        const val AUTO_CHECK_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000
        private val KEY_REGEX = Regex("<Key>([^<]+\\.csv)</Key>")
    }

    private val http = baseHttp.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val dao get() = db.dao()

    private val _state = MutableStateFlow<DbState>(DbState.NotDownloaded)
    val state: StateFlow<DbState> = _state.asStateFlow()

    /** Invoked after the live table changed (clears lookup caches). */
    @Volatile var onDataChanged: () -> Unit = {}

    /** True when a completed import exists (lookups are allowed even while re-downloading). */
    @Volatile var hasData: Boolean = false
        private set

    private var job: Job? = null

    init {
        scope.launch {
            val meta = settings.aircraftDbMeta.first()
            if (meta != null && _state.value is DbState.NotDownloaded) {
                // The meta (DataStore) can be restored from a backup while the
                // excluded database file is gone: only trust it if rows exist.
                if (restoredMetaIsValid(meta, dao.hasRows())) {
                    hasData = true
                    _state.value = meta.toReady()
                } else {
                    settings.setAircraftDbMeta(null)
                }
            }
        }
    }

    private fun AircraftDbMeta.toReady() =
        DbState.Ready(importedAtMs, rowCount, classifiedCount, sourceKey)

    fun isMetered(): Boolean = connectivity.isMetered()

    fun download() {
        if (job?.isActive == true) return
        val previous = _state.value
        job = scope.launch {
            _state.value = DbState.Downloading(0f)
            try {
                runImport()
            } catch (ce: CancellationException) {
                withContext(NonCancellable) { runCatching { dao.clearStaging() } }
                _state.value = restoreState(previous)
                throw ce
            } catch (t: Throwable) {
                AppLog.w(TAG, "import failed", throwable = t)
                withContext(NonCancellable) { runCatching { dao.clearStaging() } }
                _state.value = DbState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    private suspend fun restoreState(previous: DbState): DbState = when (previous) {
        is DbState.Ready -> previous
        else -> settings.aircraftDbMeta.first()?.toReady() ?: DbState.NotDownloaded
    }

    fun cancel() {
        job?.cancel()
    }

    fun delete() {
        val running = job
        scope.launch {
            // Let the cancelled import finish its own cleanup/state restore first,
            // otherwise it could run after us and flip the state back to Ready.
            running?.cancelAndJoin()
            dao.clearMain()
            dao.clearStaging()
            runCatching { db.openHelper.writableDatabase.execSQL("VACUUM") }
            settings.setAircraftDbMeta(null)
            hasData = false
            onDataChanged()
            _state.value = DbState.NotDownloaded
        }
    }

    /** Looks for a newer dump; flips [DbState.Ready.updateAvailable]. Returns true when one exists. */
    suspend fun checkForUpdate(): Boolean {
        val meta = settings.aircraftDbMeta.first() ?: return false
        return try {
            val key = latestKey()
            val newer = when {
                key == null -> false
                key != meta.sourceKey -> true
                else -> {
                    val etag = headEtag(key)
                    etag != null && meta.etag != null && etag != meta.etag
                }
            }
            settings.setAircraftDbLastCheck(time.nowMs())
            (_state.value as? DbState.Ready)?.let { _state.value = it.copy(updateAvailable = newer) }
            newer
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            AppLog.w(TAG, "update check failed", throwable = t)
            false
        }
    }

    /** At most every 30 days, unmetered only, only once downloaded; downloads silently. */
    fun maybeAutoUpdate() {
        scope.launch {
            val meta = settings.aircraftDbMeta.first() ?: return@launch
            if (time.nowMs() - meta.lastCheckMs < AUTO_CHECK_INTERVAL_MS) return@launch
            if (!connectivity.online.value || connectivity.isMetered()) return@launch
            if (checkForUpdate()) download()
        }
    }

    private suspend fun latestKey(): String? {
        http.newCall(Request.Builder().url(LIST_URL).get().build()).await().use { r ->
            if (!r.isSuccessful) error(Words.get(W.DB_ERR_LIST, r.code))
            return KEY_REGEX.findAll(r.body.string()).map { it.groupValues[1] }.maxOrNull()
        }
    }

    private suspend fun headEtag(key: String): String? {
        http.newCall(Request.Builder().url(BASE_URL + key).head().build()).await().use { r ->
            return if (r.isSuccessful) r.header("ETag") else null
        }
    }

    private suspend fun runImport() {
        val key = latestKey() ?: error(Words.get(W.DB_ERR_NONE))
        val doc = http.newCall(Request.Builder().url(BASE_URL + DOC8643_KEY).get().build())
            .await().use { r ->
                if (!r.isSuccessful) emptyMap()
                else AircraftDbImporter.parseDoc8643(r.body.charStream())
            }

        val t0 = time.nowMs()
        dao.clearStaging()
        val request = Request.Builder().url(BASE_URL + key).get().build()
        http.newCall(request).await().use { r ->
            if (!r.isSuccessful) error(Words.get(W.DB_ERR_HTTP, r.code))
            val total = r.body.contentLength().takeIf { it > 0 }
            val etag = r.header("ETag")
            val counting = CountingInputStream(r.body.byteStream())
            val stats = AircraftDbImporter.import(
                InputStreamReader(counting, Charsets.UTF_8), doc
            ) { rows ->
                dao.insertStaging(rows.map {
                    AircraftMetaStagingEntity(
                        it.icao24, it.cls.code, it.typecode, it.registration, it.model, it.operator
                    )
                })
                if (total != null) {
                    _state.value = DbState.Downloading(
                        (counting.bytes.toFloat() / total).coerceIn(0f, 1f) * 0.98f
                    )
                }
            }
            if (stats.keptRows == 0L) error(Words.get(W.DB_ERR_EMPTY))
            dao.swap()
            // Reclaim the staging pages and fold the WAL back into the main file.
            runCatching {
                val sqlite = db.openHelper.writableDatabase
                sqlite.execSQL("VACUUM")
                sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            }
            val now = time.nowMs()
            val meta = AircraftDbMeta(key, etag, now, stats.keptRows.toInt(),
                stats.rowsWithClass.toInt(), now)
            settings.setAircraftDbMeta(meta)
            hasData = true
            onDataChanged()
            _state.value = meta.toReady()
            AppLog.i(TAG, "import done",
                "records" to stats.parsedRows, "kept" to stats.keptRows,
                "withClass" to stats.rowsWithClass, "ms" to (now - t0))
        }
    }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        @Volatile var bytes: Long = 0
            private set

        override fun read(): Int = super.read().also { if (it >= 0) bytes++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) bytes += it }
    }
}

/** Stored import metadata only counts while the main table actually has rows. */
internal fun restoredMetaIsValid(meta: AircraftDbMeta?, tableHasRows: Boolean): Boolean =
    meta != null && tableHasRows
