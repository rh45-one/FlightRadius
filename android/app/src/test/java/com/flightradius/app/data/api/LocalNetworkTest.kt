package com.flightradius.app.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkTest {

    private fun isLocal(host: String) = LocalNetwork.isLocalNetworkHost(host)

    @Test
    fun `private IPv4 ranges are local`() {
        assertTrue(isLocal("10.0.0.1"))
        assertTrue(isLocal("10.0.2.2")) // emulator host loopback
        assertTrue(isLocal("172.16.5.4"))
        assertTrue(isLocal("172.31.255.255"))
        assertTrue(isLocal("192.168.1.20"))
        assertTrue(isLocal("169.254.1.1")) // link-local
        assertTrue(isLocal("100.64.0.1"))  // CGNAT
        assertTrue(isLocal("100.127.9.9"))
    }

    @Test
    fun `public IPv4 is not local`() {
        assertFalse(isLocal("8.8.8.8"))
        assertFalse(isLocal("172.15.0.1"))
        assertFalse(isLocal("172.32.0.1"))
        assertFalse(isLocal("100.63.255.255"))
        assertFalse(isLocal("100.128.0.1"))
        assertFalse(isLocal("192.167.0.1"))
    }

    @Test
    fun `loopback is not local`() {
        assertFalse(isLocal("127.0.0.1"))
        assertFalse(isLocal("127.42.0.1"))
        assertFalse(isLocal("localhost"))
        assertFalse(isLocal("::1"))
        assertFalse(isLocal("[::1]"))
    }

    @Test
    fun `local IPv6 literals are local`() {
        assertTrue(isLocal("fd00::1"))       // ULA fc00::/7
        assertTrue(isLocal("fc12:34::1"))
        assertTrue(isLocal("fe80::1"))       // link-local fe80::/10
        assertTrue(isLocal("febf::abcd"))
        assertFalse(isLocal("2001:4860:4860::8888")) // public
    }

    @Test
    fun `local hostname suffixes are local`() {
        assertTrue(isLocal("nas.local"))
        assertTrue(isLocal("server.lan"))
        assertTrue(isLocal("box.home.arpa"))
        assertTrue(isLocal("svc.internal"))
        assertTrue(isLocal("NAS.LOCAL")) // case-insensitive
    }

    @Test
    fun `single-label hostnames are local`() {
        assertTrue(isLocal("nas"))
        assertTrue(isLocal("raspberrypi"))
    }

    @Test
    fun `public FQDNs are not local`() {
        assertFalse(isLocal("example.com"))
        assertFalse(isLocal("api.opensky-network.org"))
        assertFalse(isLocal("notlocal.localx")) // near-miss suffix
        assertFalse(isLocal("internal.example.com"))
        assertFalse(isLocal(""))
    }
}
