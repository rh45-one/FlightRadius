package com.flightradius.app.ui.settings

import com.flightradius.app.data.api.BackendUrl

/** Settings field errors; mapped to strings.xml at the UI layer. */
enum class FieldError {
    EMPTY,
    NOT_A_NUMBER,
    OUT_OF_RANGE,
    INVALID_URL
}

object SettingsValidation {

    fun latitudeError(raw: String): FieldError? {
        val t = raw.trim()
        if (t.isEmpty()) return FieldError.EMPTY
        val v = t.toDoubleOrNull() ?: return FieldError.NOT_A_NUMBER
        return if (v in -90.0..90.0) null else FieldError.OUT_OF_RANGE
    }

    fun longitudeError(raw: String): FieldError? {
        val t = raw.trim()
        if (t.isEmpty()) return FieldError.EMPTY
        val v = t.toDoubleOrNull() ?: return FieldError.NOT_A_NUMBER
        return if (v in -180.0..180.0) null else FieldError.OUT_OF_RANGE
    }

    /**
     * Backend URL: blank = reset-to-default (valid); otherwise must
     * normalize to http(s) via [BackendUrl.normalize].
     */
    fun urlError(raw: String): FieldError? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        return if (BackendUrl.normalize(t) != null) null else FieldError.INVALID_URL
    }
}
