package com.flightradius.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.flightradius.app.domain.AircraftClass
import com.flightradius.app.domain.GroupIcon
import com.flightradius.app.ui.format.icon

private fun filled(name: String, path: String, evenOdd: Boolean = false): ImageVector =
    ImageVector.Builder("Group$name", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(path),
            fill = SolidColor(Color.Black),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
        )
        .build()

// Most paths come from Google's Material Icons (Apache-2.0, see
// assets/licenses/material-symbols-LICENSE.txt); police and fire are our own.
private val Shield = filled(
    "Shield",
    "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5l-9,-4zM10,17l-4,-4 1.41,-1.41L10,14.17l6.59,-6.59L18,9l-8,8z"
)
private val Lock = filled(
    "Lock",
    "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z"
)
private val House = filled("House", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
private val Star = filled(
    "Star",
    "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z"
)
private val Heart = filled(
    "Heart",
    "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"
)
private val Flag = filled("Flag", "M14.4,6L14,4H5v17h2v-7h5.6l0.4,2h7V6z")
private val Bolt = filled("Bolt", "M7,2v11h3v9l7,-12h-4l4,-8z")
private val Police = filled(
    "Police",
    "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5l-9,-4zM12.00,7.40 L13.35,10.74 L16.95,10.99 L14.19,13.31 L15.06,16.81 L12.00,14.90 L8.94,16.81 L9.81,13.31 L7.05,10.99 L10.65,10.74Z",
    evenOdd = true
)
private val Medical = filled(
    "Medical",
    "M19,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM17,13h-4v4h-2v-4H7v-2h4V7h2v4h4v2z",
    evenOdd = true
)
private val Fire = filled(
    "Fire",
    "M12,2c0,0 -6,6 -6,11a6,6 0 0,0 12,0c0,-2.2 -1.2,-4 -2,-5c-0.3,1.6 -1.2,2.6 -2.2,3C13.2,8.8 12.6,5 12,2z"
)
private val Briefcase = filled(
    "Briefcase",
    "M20,6h-4V4c0,-1.11 -0.89,-2 -2,-2h-4c-1.11,0 -2,0.89 -2,2v2H4c-1.11,0 -1.99,0.89 -1.99,2L2,19c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2V8c0,-1.11 -0.89,-2 -2,-2zM10,4h4v2h-4V4z"
)
private val Eye = filled(
    "Eye",
    "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z"
)
private val Camera = filled(
    "Camera",
    "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,8.8a3.2,3.2 0 1,0 0.01,0z",
    evenOdd = true
)
private val Pin = filled(
    "Pin",
    "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zM12,11.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z"
)

/** The 24dp, single-colour glyph for a group icon (tint it at the call site). */
fun GroupIcon.vector(): ImageVector = when (this) {
    GroupIcon.PLANE -> AircraftClass.AIRLINER.icon()
    GroupIcon.HELICOPTER -> AircraftClass.HELICOPTER.icon()
    GroupIcon.SHIELD -> Shield
    GroupIcon.LOCK -> Lock
    GroupIcon.HOUSE -> House
    GroupIcon.STAR -> Star
    GroupIcon.HEART -> Heart
    GroupIcon.FLAG -> Flag
    GroupIcon.BOLT -> Bolt
    GroupIcon.POLICE -> Police
    GroupIcon.MEDICAL -> Medical
    GroupIcon.FIRE -> Fire
    GroupIcon.BRIEFCASE -> Briefcase
    GroupIcon.EYE -> Eye
    GroupIcon.CAMERA -> Camera
    GroupIcon.PIN -> Pin
}
