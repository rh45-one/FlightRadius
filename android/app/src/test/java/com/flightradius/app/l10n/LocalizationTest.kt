package com.flightradius.app.l10n

import com.flightradius.app.ui.format.W
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Keeps values/ and values-es/ in lock-step (keys, placeholders, no stray English). */
class LocalizationTest {

    private class Res(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
        val arrays: Map<String, List<String>>
    )

    private fun load(dir: String): Res {
        val file = File("src/main/res/$dir/strings.xml")
        assertTrue("missing ${file.absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        val strings = LinkedHashMap<String, String>()
        val plurals = LinkedHashMap<String, Map<String, String>>()
        val arrays = LinkedHashMap<String, List<String>>()
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i) as? Element ?: continue
            when (n.tagName) {
                "string" -> strings[n.getAttribute("name")] = unescape(n.textContent)
                "plurals" -> plurals[n.getAttribute("name")] = items(n, "quantity")
                    .mapValues { unescape(it.value) }
                "string-array" -> arrays[n.getAttribute("name")] =
                    (0 until n.getElementsByTagName("item").length).map {
                        unescape(n.getElementsByTagName("item").item(it).textContent)
                    }
            }
        }
        return Res(strings, plurals, arrays)
    }

    private fun items(n: Element, attr: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val list = n.getElementsByTagName("item")
        for (i in 0 until list.length) {
            val e = list.item(i) as Element
            out[e.getAttribute(attr)] = e.textContent
        }
        return out
    }

    /** Android resource escapes: \' \" \n \\ and surrounding quotes. */
    private fun unescape(raw: String): String {
        var s = raw
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1)
        return s.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")
    }

    private val en = load("values")
    private val es = load("values-es")

    private val placeholder = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z%]")

    private fun placeholders(s: String) = placeholder.findAll(s).map { it.value }.sorted().toList()

    @Test
    fun `identical key sets`() {
        assertEquals(en.strings.keys.sorted(), es.strings.keys.sorted())
        assertEquals(en.plurals.keys.sorted(), es.plurals.keys.sorted())
        assertEquals(en.arrays.keys.sorted(), es.arrays.keys.sorted())
        for ((k, v) in en.arrays) assertEquals("array $k size", v.size, es.arrays.getValue(k).size)
    }

    @Test
    fun `placeholders match per key`() {
        for ((k, v) in en.strings) {
            assertEquals("string $k", placeholders(v), placeholders(es.strings.getValue(k)))
        }
        for ((k, items) in en.plurals) {
            assertEquals("plural $k quantities", items.keys, es.plurals.getValue(k).keys)
            for ((q, v) in items) {
                assertEquals("plural $k/$q", placeholders(v), placeholders(es.plurals.getValue(k).getValue(q)))
            }
        }
    }

    /** Strings that are legitimately identical in English and Spanish. */
    private val sameInBoth = setOf(
        "app_name", "nav_radar", "fleets_color", "location_mode_gps", "location_mode_manual",
        "aircraft_type_icao24", "detail_icao24", "db_state_percent", "radar_dial_description",
        "words_backend_ok", "words_backend_opensky", "words_loc_fix", "words_loc_manual",
        "words_err_http", "words_coverage_lt1", "words_mon_error", "bulk_mode_icao24", "language_english", "language_spanish"
    )

    @Test
    fun `no untranslated copies`() {
        val offenders = en.strings.filter { (k, v) ->
            k !in sameInBoth && v == es.strings.getValue(k) && v.any { it.isLetter() }
        }.keys
        assertTrue("untranslated: $offenders", offenders.isEmpty())
        for ((k, items) in en.plurals) {
            for ((q, v) in items) {
                assertTrue("plural $k/$q untranslated", v != es.plurals.getValue(k).getValue(q))
            }
        }
        val spanishCompass = es.arrays.getValue("compass_points")
        assertTrue(spanishCompass != en.arrays.getValue("compass_points"))
    }

    @Test
    fun `spanish compass uses O for west`() {
        val short = es.arrays.getValue("compass_short")
        assertEquals(listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO"), short)
        assertEquals(listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"),
            en.arrays.getValue("compass_short"))
    }

    @Test
    fun `word table english fallbacks match values strings`() {
        // Resource names are words_<lowercase enum>-ish; compare through the enum's own res name.
        val byRes = en.strings
        for (w in W.entries) {
            val name = resName(w)
            val text = byRes[name] ?: error("no English string $name for $w")
            assertEquals("fallback drift for $w", text, w.en)
        }
    }

    private fun resName(w: W): String {
        // Generated names follow the enum: AGE_NOW -> words_age_now, BACKEND_UNREACHABLE_OPENSKY -> words_backend_unreachable_opensky
        return "words_" + w.name.lowercase()
    }
}
