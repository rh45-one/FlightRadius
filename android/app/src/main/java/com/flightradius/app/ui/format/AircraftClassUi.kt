package com.flightradius.app.ui.format

import com.flightradius.app.R
import com.flightradius.app.domain.AircraftClass

fun AircraftClass.labelRes(): Int = when (this) {
    AircraftClass.AIRLINER -> R.string.aircraft_class_airliner
    AircraftClass.LIGHT -> R.string.aircraft_class_light
    AircraftClass.HELICOPTER -> R.string.aircraft_class_helicopter
    AircraftClass.GLIDER -> R.string.aircraft_class_glider
    AircraftClass.BALLOON -> R.string.aircraft_class_balloon
    AircraftClass.DRONE -> R.string.aircraft_class_drone
    AircraftClass.MILITARY -> R.string.aircraft_class_military
    AircraftClass.UNKNOWN -> R.string.aircraft_class_unknown
}
