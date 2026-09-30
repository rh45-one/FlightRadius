package com.flightradius.app.data.aircraftdb

import com.flightradius.app.domain.AircraftClass
import com.flightradius.app.domain.DbClassMapper
import com.flightradius.app.domain.Doc8643Type
import java.io.BufferedReader
import java.io.Reader
import java.util.Locale

/** One row to store: a class and/or a registration. */
data class MetaRow(
    val icao24: String,
    val cls: AircraftClass,
    val typecode: String?,
    val registration: String?,
    val model: String?,
    val operator: String?
)

data class ImportStats(
    val totalLines: Long,
    val parsedRows: Long,
    val keptRows: Long,
    val rowsWithClass: Long
)

/** Header-driven mapping from one CSV record to a [MetaRow]. */
class AircraftDbRowMapper(header: List<String>, private val doc8643: Map<String, Doc8643Type>) {

    private val index: Map<String, Int> =
        header.withIndex().associate { (i, name) -> name.trim().lowercase(Locale.ROOT) to i }

    private val icao24 = col("icao24")
    private val registration = col("registration")
    private val model = col("model")
    private val operator = col("operator")
    private val typecode = col("typecode")
    private val classColumn = col("icaoaircraftclass") ?: col("icaoaircrafttype")
    private val category = col("categorydescription")

    val isUsable: Boolean get() = icao24 != null

    private fun col(name: String): Int? = index[name]

    private fun List<String>.field(i: Int?): String? =
        i?.let { getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }

    fun map(fields: List<String>): MetaRow? {
        val id = fields.field(icao24)?.lowercase(Locale.ROOT) ?: return null
        if (id.length != 6 || !id.all { it in '0'..'9' || it in 'a'..'f' }) return null
        val reg = fields.field(registration)?.uppercase(Locale.ROOT)
        val type = fields.field(typecode)
        val cls = DbClassMapper.classify(
            fields.field(classColumn), type, fields.field(category), doc8643
        )
        if (cls == AircraftClass.UNKNOWN && reg == null) return null
        return MetaRow(id, cls, type, reg, fields.field(model), fields.field(operator))
    }
}

object AircraftDbImporter {

    const val BATCH_SIZE = 5_000

    /** Parses `doc8643AircraftTypes.csv` into Designator -> type. */
    fun parseDoc8643(reader: Reader): Map<String, Doc8643Type> {
        val buffered = reader as? BufferedReader ?: BufferedReader(reader)
        val headerLine = buffered.readLine() ?: return emptyMap()
        val quote = CsvParser.detectQuote(headerLine)
        val header = CsvParser.parseRecord(headerLine.trimEnd('\r'), quote) ?: return emptyMap()
        val lower = header.map { it.trim().lowercase(Locale.ROOT) }
        val designator = lower.indexOf("designator")
        val description = lower.indexOf("description")
        val wtc = lower.indexOf("wtc")
        if (designator < 0 || description < 0) return emptyMap()
        val out = HashMap<String, Doc8643Type>()
        val rr = CsvParser.RecordReader(buffered, quote)
        while (true) {
            val f = rr.next() ?: break
            val key = f.getOrNull(designator)?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
                ?: continue
            out.putIfAbsent(
                key,
                Doc8643Type(
                    f.getOrNull(description)?.trim().orEmpty(),
                    f.getOrNull(wtc)?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
        }
        return out
    }

    /**
     * Streams [input], handing batches of [BATCH_SIZE] rows to [sink]. Any
     * exception from [sink] (or cancellation) aborts the import; nothing is
     * committed here, so callers keep the old data by only swapping after
     * this returns.
     */
    suspend fun import(
        input: Reader,
        doc8643: Map<String, Doc8643Type>,
        sink: suspend (List<MetaRow>) -> Unit
    ): ImportStats {
        val buffered = input as? BufferedReader ?: BufferedReader(input, 1 shl 16)
        val headerLine = buffered.readLine()?.removePrefix("\uFEFF")
            ?: return ImportStats(0, 0, 0, 0)
        val quote = CsvParser.detectQuote(headerLine)
        val header = CsvParser.parseRecord(headerLine.trimEnd('\r'), quote)
            ?: return ImportStats(1, 0, 0, 0)
        val mapper = AircraftDbRowMapper(header, doc8643)
        if (!mapper.isUsable) return ImportStats(1, 0, 0, 0)

        val rr = CsvParser.RecordReader(buffered, quote)
        var parsed = 0L
        var kept = 0L
        var withClass = 0L
        val batch = ArrayList<MetaRow>(BATCH_SIZE)
        while (true) {
            val fields = rr.next() ?: break
            if (fields.size == 1 && fields[0].isEmpty()) continue
            parsed++
            val row = mapper.map(fields) ?: continue
            kept++
            if (row.cls != AircraftClass.UNKNOWN) withClass++
            batch += row
            if (batch.size >= BATCH_SIZE) {
                sink(ArrayList(batch))
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) sink(ArrayList(batch))
        return ImportStats(parsed + 1, parsed, kept, withClass)
    }
}
