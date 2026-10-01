package com.flightradius.app.util.log

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timeMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val fields: Map<String, Any?>,
    val throwable: String? = null
)

private val SENSITIVE_KEY =
    Regex("(?i)secret|password|token|client_?id|username|authorization")

/**
 * Structured logger with a 500-entry in-memory ring buffer for the debug
 * console.
 *
 * - DEBUG/INFO are buffered AND sent to logcat only when [debugEnabled]
 *   returns true (settings flag or debug build). When debug is off they are
 *   dropped entirely.
 * - WARN/ERROR are always buffered and always sent to logcat.
 * - Field values whose key matches secret|password|token|client_id|username|
 *   authorization are redacted to "***".
 *
 * Usable from pure-ish code: [debugEnabled]/[clock] are simple providers,
 * installed by [com.flightradius.app.FlightRadiusApp] (RuntimeSettings-backed)
 * and replaceable in tests. Domain code must not call this (android.util.Log).
 */
object AppLog {

    const val CAPACITY = 500

    /** Debug gate. Default off; the app installs a RuntimeSettings reader. */
    @Volatile
    var debugEnabled: () -> Boolean = { false }

    /** Clock for entry timestamps; replaceable in tests. */
    @Volatile
    var clock: () -> Long = { System.currentTimeMillis() }

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    /** Ring buffer of recent entries for the debug console. */
    val entries: StateFlow<List<LogEntry>> = _entries

    fun clear() {
        _entries.value = emptyList()
    }

    fun d(
        tag: String,
        msg: String,
        vararg fields: Pair<String, Any?>,
        throwable: Throwable? = null
    ) = record(LogLevel.DEBUG, tag, msg, fields, throwable, debugGated = true)

    fun i(
        tag: String,
        msg: String,
        vararg fields: Pair<String, Any?>,
        throwable: Throwable? = null
    ) = record(LogLevel.INFO, tag, msg, fields, throwable, debugGated = true)

    fun w(
        tag: String,
        msg: String,
        vararg fields: Pair<String, Any?>,
        throwable: Throwable? = null
    ) = record(LogLevel.WARN, tag, msg, fields, throwable, debugGated = false)

    fun e(
        tag: String,
        msg: String,
        vararg fields: Pair<String, Any?>,
        throwable: Throwable? = null
    ) = record(LogLevel.ERROR, tag, msg, fields, throwable, debugGated = false)

    private fun record(
        level: LogLevel,
        tag: String,
        msg: String,
        fields: Array<out Pair<String, Any?>>,
        throwable: Throwable?,
        debugGated: Boolean
    ) {
        if (debugGated && !debugEnabled()) return

        val safeFields = fields.toMap().mapValues { (k, v) ->
            if (SENSITIVE_KEY.containsMatchIn(k)) "***" else v
        }
        val rendered = buildString {
            append(msg)
            safeFields.forEach { (k, v) -> append(" $k=$v") }
        }

        when (level) {
            LogLevel.DEBUG -> Log.d(tag, rendered, throwable)
            LogLevel.INFO -> Log.i(tag, rendered, throwable)
            LogLevel.WARN -> Log.w(tag, rendered, throwable)
            LogLevel.ERROR -> Log.e(tag, rendered, throwable)
        }

        val entry = LogEntry(
            timeMs = clock(),
            level = level,
            tag = tag,
            message = msg,
            fields = safeFields,
            throwable = throwable?.let { "${it.javaClass.simpleName}: ${it.message}" }
        )
        _entries.update { list ->
            if (list.size >= CAPACITY) list.drop(list.size - CAPACITY + 1) + entry
            else list + entry
        }
    }
}
