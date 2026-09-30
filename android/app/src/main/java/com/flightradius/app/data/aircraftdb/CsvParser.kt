package com.flightradius.app.data.aircraftdb

import java.io.BufferedReader

/**
 * Tolerant CSV tokenizer for the OpenSky aircraft database. Fields quoted
 * with [quote] (`'` in the current dump, `"` in the legacy one) escape the
 * quote by doubling it; unquoted fields are taken verbatim up to the next
 * comma. A quote character only opens a quoted field at the field start.
 */
object CsvParser {

    /** Parsed fields, or null when the record ends inside an open quote. */
    fun parseRecord(text: String, quote: Char): List<String>? {
        val fields = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        val n = text.length
        while (true) {
            sb.setLength(0)
            if (i < n && text[i] == quote) {
                i++
                var closed = false
                while (i < n) {
                    val c = text[i]
                    if (c == quote) {
                        if (i + 1 < n && text[i + 1] == quote) {
                            sb.append(quote); i += 2
                        } else {
                            i++; closed = true; break
                        }
                    } else {
                        sb.append(c); i++
                    }
                }
                if (!closed) return null
                // Tolerate junk between the closing quote and the comma.
                while (i < n && text[i] != ',') { sb.append(text[i]); i++ }
            } else {
                while (i < n && text[i] != ',') { sb.append(text[i]); i++ }
            }
            fields += sb.toString()
            if (i >= n) break
            i++ // comma
            if (i >= n) { fields += ""; break }
        }
        return fields
    }

    /** `'` or `"` depending on the first character of the header line. */
    fun detectQuote(header: String): Char =
        if (header.trimStart().startsWith("\"")) '"' else '\''

    /**
     * Streams records, joining physical lines when a quoted field spans
     * them (at most [MAX_JOIN] lines; beyond that the first line is taken
     * as a malformed record so one stray quote cannot swallow the file).
     */
    class RecordReader(private val reader: BufferedReader, val quote: Char) {
        private val pending = ArrayDeque<String>()

        private fun line(): String? =
            if (pending.isNotEmpty()) pending.removeFirst() else reader.readLine()

        fun next(): List<String>? {
            val first = line()?.trimEnd('\r') ?: return null
            parseRecord(first, quote)?.let { return it }
            val extra = ArrayList<String>()
            var text = first
            while (extra.size < MAX_JOIN) {
                val more = line()?.trimEnd('\r') ?: break
                extra += more
                text = text + "\n" + more
                parseRecord(text, quote)?.let { return it }
            }
            for (l in extra.asReversed()) pending.addFirst(l)
            return parseRecord(first + quote, quote)
        }
    }

    private const val MAX_JOIN = 5
}
