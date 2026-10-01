package com.flightradius.app.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

private val DarkOnGroup = Color(0xFF1C1C1E)

private fun channel(c: Float): Double {
    val v = c.toDouble()
    return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

/** WCAG relative luminance (0..1). */
fun relativeLuminance(color: Color): Double =
    0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

/** WCAG contrast ratio (1..21) between two opaque colours. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** Icon / checkmark colour on a solid group colour: white or near-black, whichever contrasts more. */
fun onGroupColor(argb: Int): Color {
    val bg = Color(argb)
    return if (contrastRatio(Color.White, bg) >= contrastRatio(DarkOnGroup, bg)) Color.White
    else DarkOnGroup
}

