package com.flightradius.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {
    @Test fun `tags map to options`() {
        assertEquals(AppLanguageOption.ENGLISH, AppLanguageOption.fromTag("en"))
        assertEquals(AppLanguageOption.SPANISH, AppLanguageOption.fromTag("es"))
    }

    @Test fun `regional tags map to their language`() {
        assertEquals(AppLanguageOption.SPANISH, AppLanguageOption.fromTag("es-ES"))
        assertEquals(AppLanguageOption.SPANISH, AppLanguageOption.fromTag("es-419"))
        assertEquals(AppLanguageOption.ENGLISH, AppLanguageOption.fromTag("en-GB"))
    }

    @Test fun `unknown empty or missing tags mean system`() {
        for (t in listOf(null, "", "  ", "fr", "de-DE", "zz", "!!!"))
            assertEquals("$t", AppLanguageOption.SYSTEM, AppLanguageOption.fromTag(t))
    }

    @Test fun `option tags round trip`() {
        for (o in AppLanguageOption.entries) assertEquals(o, AppLanguageOption.fromTag(o.tag))
        assertNull(AppLanguageOption.SYSTEM.tag)
    }

    @Test fun `locale building`() {
        assertNull(AppLanguage.localeFor(null))
        assertNull(AppLanguage.localeFor(""))
        assertEquals("es", AppLanguage.localeFor("es")!!.language)
        assertEquals("en", AppLanguage.localeFor("en")!!.language)
    }
}
