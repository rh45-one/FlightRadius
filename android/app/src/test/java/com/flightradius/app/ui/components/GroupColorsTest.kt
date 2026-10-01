package com.flightradius.app.ui.components

import androidx.compose.ui.graphics.Color
import com.flightradius.app.data.repo.FLEET_COLOR_PALETTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupColorsTest {
    private val colors = FLEET_COLOR_PALETTE.toList() +
        0xFFFF0000.toInt() + 0xFF00FFFF.toInt() + 0xFF000000.toInt() + 0xFFFFFFFF.toInt()

    @Test fun `icon colour has at least 3 to 1 contrast on every palette colour, red and cyan`() {
        for (argb in colors) {
            val ratio = contrastRatio(onGroupColor(argb), Color(argb))
            assertTrue("0x${argb.toUInt().toString(16)} -> $ratio", ratio >= 3.0)
        }
    }

    @Test fun `light colours get dark icons and dark colours get white`() {
        assertEquals(Color(0xFF1C1C1E), onGroupColor(0xFF22D3EE.toInt())) // cyan
        assertEquals(Color(0xFF1C1C1E), onGroupColor(0xFFFBBF24.toInt())) // yellow
        assertEquals(Color(0xFF1C1C1E), onGroupColor(0xFF00FFFF.toInt()))
        assertEquals(Color.White, onGroupColor(0xFF6366F1.toInt())) // indigo
        assertEquals(Color.White, onGroupColor(0xFF000000.toInt()))
    }

    @Test fun `contrast ratio basics`() {
        assertEquals(21.0, contrastRatio(Color.White, Color.Black), 0.01)
        assertEquals(1.0, contrastRatio(Color.Red, Color.Red), 0.0001)
    }
}
