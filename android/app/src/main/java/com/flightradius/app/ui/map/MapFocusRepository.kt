package com.flightradius.app.ui.map

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One-shot request for the Map tab to centre on an aircraft ("t:<id>" / "n:<icao24>"). */
@Singleton
class MapFocusRepository @Inject constructor() {
    private val _request = MutableStateFlow<String?>(null)
    val request: StateFlow<String?> = _request

    fun focus(aircraftKey: String) { _request.value = aircraftKey }
    fun consume() { _request.value = null }
}
