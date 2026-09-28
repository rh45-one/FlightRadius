package com.flightradius.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendUrlTest {

    @Test
    fun `accepts plain http url and adds trailing slash`() {
        assertEquals(
            "http://10.0.2.2:3000/",
            BackendUrl.normalize("http://10.0.2.2:3000")?.toString()
        )
    }

    @Test
    fun `keeps existing trailing slash`() {
        assertEquals(
            "http://10.0.2.2:3000/",
            BackendUrl.normalize("http://10.0.2.2:3000/")?.toString()
        )
    }

    @Test
    fun `preserves path prefix`() {
        assertEquals(
            "https://host:8443/sub/",
            BackendUrl.normalize("https://host:8443/sub")?.toString()
        )
        assertEquals(
            "https://host:8443/sub/",
            BackendUrl.normalize("https://host:8443/sub/")?.toString()
        )
    }

    @Test
    fun `rejects non http schemes`() {
        assertNull(BackendUrl.normalize("ftp://host"))
        assertNull(BackendUrl.normalize("ws://host:3000"))
    }

    @Test
    fun `rejects blank and garbage`() {
        assertNull(BackendUrl.normalize(null))
        assertNull(BackendUrl.normalize(""))
        assertNull(BackendUrl.normalize("   "))
        assertNull(BackendUrl.normalize("not a url"))
    }
}
