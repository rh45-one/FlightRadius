package com.flightradius.app.ui.format

import com.flightradius.app.domain.DistanceUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Alert-radius choices and parsing, shared by Settings and Bulk add. */
object RadiusInput {
    const val MIN_KM = 0.5
    const val MAX_KM = 500.0

    private val KM_STEPS: List<Int> = (1..50).toList() + (55..200 step 5).toList()
    private val MI_STEPS: List<Int> = (1..30).toList() + (35..125 step 5).toList()

    /** Allowed slider values in the user's unit: fine steps first, then coarse. */
    fun steps(unit: DistanceUnit): List<Int> =
        if (unit == DistanceUnit.MI) MI_STEPS else KM_STEPS

    fun toKm(value: Double, unit: DistanceUnit): Double =
        if (unit == DistanceUnit.MI) value / Format.KM_TO_MI else value

    /** Index of the step nearest to a stored radius (any value, e.g. 10.3 km). */
    fun nearestIndex(km: Double, unit: DistanceUnit): Int {
        val v = Format.kmToUnit(km, unit)
        val steps = steps(unit)
        var best = 0
        for (i in steps.indices) {
            if (abs(steps[i] - v) < abs(steps[best] - v)) best = i
        }
        return best
    }

    /** Radius in km for a slider index (clamped). */
    fun kmAt(index: Int, unit: DistanceUnit): Double {
        val steps = steps(unit)
        return toKm(steps[index.coerceIn(0, steps.lastIndex)].toDouble(), unit)
    }

    /** The stored radius rounded to its slider step, in km. */
    fun snapKm(km: Double, unit: DistanceUnit): Double = kmAt(nearestIndex(km, unit), unit)

    /**
     * Parses user text in [unit] (decimal comma or point). Returns km, or null
     * when it isn't a number or is outside 0.5–500 km.
     */
    fun parseKm(text: String, unit: DistanceUnit): Double? {
        val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!v.isFinite()) return null
        val km = toKm(v, unit)
        return km.takeIf { it in MIN_KM..MAX_KM }
    }

    /** Prefill text for a km radius in [unit]: whole numbers without decimals. */
    fun toFieldText(km: Double, unit: DistanceUnit): String {
        val v = Format.kmToUnit(km, unit)
        val rounded = (v * 10).roundToInt() / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else rounded.toString().replace(
            '.', java.text.DecimalFormatSymbols.getInstance().decimalSeparator)
    }
}
