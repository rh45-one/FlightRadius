package com.flightradius.app.ui.format

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.flightradius.app.domain.AircraftClass

private fun glyph(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

private val Airliner = glyph(
    "Airliner",
    "M21.5 15.5v-2l-8.5-5V3.5a1.5 1.5 0 0 0-3 0v5l-8.5 5v2l8.5-2.5v5.5L7.5 20v1.5l4.5-1.25 4.5 1.25V20l-2.5-1.5v-5.5l8.5 2.5z"
)
private val Light = glyph(
    "LightAircraft",
    "M10.8 2h2.4v6h8.3v2.6h-8.3V16l3 1.6V20H7.8v-2.4l3-1.6v-5.4H2.5V8h8.3z"
)
private val Helicopter = glyph(
    "Helicopter",
    "M3 3.5h18V5H3zM11.2 5h1.6v2.6h-1.6zM12 7.6c3.3 0 6 1.8 6 4.4s-2.7 4.4-6 4.4c-1.6 0-3-.4-4-1.1L3.5 16v-2l3 .6C6.2 13.9 6 13 6 12c0-2.6 2.7-4.4 6-4.4zM6 18.5h12V20H6z"
)
private val Glider = glyph(
    "Glider",
    "M2 11.2h20v1.7h-7.5l-.7 6.6H16V21H8v-1.5h2.2l-.7-6.6H2z"
)
private val Balloon = glyph(
    "Balloon",
    "M12 2c3.6 0 6.5 2.8 6.5 6.3 0 2.6-1.6 4.9-3.6 6.2L14.5 17h-5l-.4-2.5C7.1 13.2 5.5 10.9 5.5 8.3 5.5 4.8 8.4 2 12 2zM9.7 18.5h4.6V21H9.7z"
)
private val Drone = glyph(
    "Drone",
    "M9.5 9.5h5v5h-5zM5 2.8a2.2 2.2 0 1 1 0 4.4 2.2 2.2 0 0 1 0-4.4zM19 2.8a2.2 2.2 0 1 1 0 4.4 2.2 2.2 0 0 1 0-4.4zM5 16.8a2.2 2.2 0 1 1 0 4.4 2.2 2.2 0 0 1 0-4.4zM19 16.8a2.2 2.2 0 1 1 0 4.4 2.2 2.2 0 0 1 0-4.4zM6.6 6.6l3.2 3.2-1.2 1.2-3.2-3.2zM17.4 6.6l1.2 1.2-3.2 3.2-1.2-1.2zM6.6 17.4l3.2-3.2 1.2 1.2-3.2 3.2zM17.4 17.4l-3.2-3.2 1.2-1.2 3.2 3.2z"
)
private val Military = glyph(
    "HighPerformance",
    "M12 2l2.2 7 7.8 5v2l-7.8-2.2.6 4.2 2.4 1.6V23H8v-1.4l2.4-1.6.6-4.2L3 16v-2l7.8-5z"
)
private val Unknown = glyph(
    "UnknownAircraft",
    "M11 18h2v-2h-2zm1-16C6.5 2 2 6.5 2 12s4.5 10 10 10 10-4.5 10-10S17.5 2 12 2zm0 18c-4.4 0-8-3.6-8-8s3.6-8 8-8 8 3.6 8 8-3.6 8-8 8zm0-14c-2.2 0-4 1.8-4 4h2c0-1.1.9-2 2-2s2 .9 2 2c0 2-3 1.8-3 5h2c0-2.2 3-2.5 3-5 0-2.2-1.8-4-4-4z"
)

fun AircraftClass.icon(): ImageVector = when (this) {
    AircraftClass.AIRLINER -> Airliner
    AircraftClass.LIGHT -> Light
    AircraftClass.HELICOPTER -> Helicopter
    AircraftClass.GLIDER -> Glider
    AircraftClass.BALLOON -> Balloon
    AircraftClass.DRONE -> Drone
    AircraftClass.MILITARY -> Military
    AircraftClass.UNKNOWN -> Unknown
}
