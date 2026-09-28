package com.flightradius.app.data.api

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import okhttp3.HttpUrl

/**
 * Android 17 (API 37) blocks TCP to LAN destinations unless the runtime
 * permission ACCESS_LOCAL_NETWORK (Nearby devices group) is granted.
 *
 * [isLocalNetworkHost] classifies the *configured host literal*. It does NOT
 * do DNS — a public hostname that resolves to a LAN IP still needs the
 * permission and will surface as a network failure; we map that IOException
 * to [ApiError.LocalNetworkPermissionRequired] only when the host is a LAN
 * literal, otherwise the error stays a generic Network error (documented
 * limitation — the error message hints at Nearby devices).
 */
object LocalNetwork {

    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun isLocalNetworkHost(url: HttpUrl): Boolean = isLocalNetworkHost(url.host)

    fun isLocalNetworkHost(host: String): Boolean {
        var h = host.trim().lowercase()
        if (h.isEmpty()) return false
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length - 1)

        // Loopback is exempt (not a LAN destination).
        if (h == "localhost" || h == "::1" || h.startsWith("127.")) return false

        return when {
            h.contains(':') -> isLocalIpv6(h)
            h.all { it.isDigit() || it == '.' } -> isLocalIpv4(h)
            else -> isLocalHostname(h)
        }
    }

    private fun isLocalIpv4(h: String): Boolean {
        val parts = h.split('.')
        if (parts.size != 4) return false
        val o = parts.map { it.toIntOrNull() ?: return false }
        if (o.any { it < 0 || it > 255 }) return false
        val (a, b) = o[0] to o[1]
        return when {
            a == 10 -> true                      // 10.0.0.0/8 (incl. emulator 10.0.2.2)
            a == 172 && b in 16..31 -> true      // 172.16.0.0/12
            a == 192 && b == 168 -> true         // 192.168.0.0/16
            a == 169 && b == 254 -> true         // 169.254.0.0/16 link-local
            a == 100 && b in 64..127 -> true     // 100.64.0.0/10 CGNAT
            else -> false
        }
    }

    private fun isLocalIpv6(h: String): Boolean {
        val bytes = runCatching {
            java.net.InetAddress.getByName(h).address
        }.getOrNull() ?: return false
        if (bytes.size != 16) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        // fc00::/7 (ULA)
        if (b0 and 0xFE == 0xFC) return true
        // fe80::/10 (link-local): fe80..febf
        if (b0 == 0xFE && b1 and 0xC0 == 0x80) return true
        return false
    }

    private fun isLocalHostname(h: String): Boolean {
        if (h.endsWith(".local") || h.endsWith(".lan") ||
            h.endsWith(".home.arpa") || h.endsWith(".internal")
        ) return true
        // Single-label hostnames (e.g. "nas", "router") are LAN names.
        return !h.contains('.')
    }

    /** True when the platform blocks LAN traffic for this app on [url]. */
    fun isRequired(context: Context, url: HttpUrl): Boolean =
        Build.VERSION.SDK_INT >= 37 &&
            isLocalNetworkHost(url) &&
            ContextCompat.checkSelfPermission(context, PERMISSION) !=
                PackageManager.PERMISSION_GRANTED
}
