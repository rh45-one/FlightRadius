package com.flightradius.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsValidationTest {

    @Test
    fun `latitude range`() {
        assertEquals(FieldError.EMPTY, SettingsValidation.latitudeError(" "))
        assertEquals(FieldError.NOT_A_NUMBER, SettingsValidation.latitudeError("abc"))
        assertEquals(FieldError.OUT_OF_RANGE, SettingsValidation.latitudeError("91"))
        assertEquals(FieldError.OUT_OF_RANGE, SettingsValidation.latitudeError("-90.1"))
        assertNull(SettingsValidation.latitudeError("52.52"))
        assertNull(SettingsValidation.latitudeError("-90"))
        assertNull(SettingsValidation.latitudeError("90"))
    }

    @Test
    fun `longitude range`() {
        assertEquals(FieldError.OUT_OF_RANGE, SettingsValidation.longitudeError("181"))
        assertEquals(FieldError.OUT_OF_RANGE, SettingsValidation.longitudeError("-180.5"))
        assertNull(SettingsValidation.longitudeError("13.4"))
        assertNull(SettingsValidation.longitudeError("180"))
    }

    @Test
    fun `backend url validation`() {
        // Blank resets to the build default — valid.
        assertNull(SettingsValidation.urlError(""))
        assertNull(SettingsValidation.urlError("http://10.0.2.2:3000/"))
        assertNull(SettingsValidation.urlError("https://api.example.com"))
        assertEquals(FieldError.INVALID_URL, SettingsValidation.urlError("ftp://x"))
        assertEquals(FieldError.INVALID_URL, SettingsValidation.urlError("not a url"))
    }
}
