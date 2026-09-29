package com.flightradius.app.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Every response field is nullable-with-default so a partial/odd backend
// response never crashes parsing. Lists default to empty.
// (Request DTOs keep required fields non-null: omitting them would just make
// the backend return 400 anyway.)

// ---------- /api/distance/compute ----------

@Serializable
data class UserLocationDto(
    val lat: Double? = null,
    val lon: Double? = null
)

@Serializable
data class FleetGroupRequestDto(
    val name: String,
    val callsigns: List<String>
)

@Serializable
data class ComputeRequestDto(
    val user_location: UserLocationDto,
    val callsigns: List<String> = emptyList(),
    val icao24s: List<String> = emptyList(),
    val groups: List<FleetGroupRequestDto> = emptyList()
)

@Serializable
data class DistanceResultDto(
    val callsign: String? = null,
    val icao24: String? = null,
    val distance_km: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val altitude_m: Double? = null,
    val last_update: String? = null,
    val velocity_mps: Double? = null,
    val heading_deg: Double? = null,
    /** Unix seconds (OpenSky last_contact; may be fractional). */
    val last_contact: Double? = null
)

@Serializable
data class GroupProximityDto(
    val group_name: String? = null,
    val closest_aircraft: DistanceResultDto? = null,
    val members_ranked: List<DistanceResultDto> = emptyList(),
    val missing: List<String> = emptyList()
)

@Serializable
data class ComputeResponseDto(
    val results: List<DistanceResultDto> = emptyList(),
    val closest: DistanceResultDto? = null,
    val missing: List<String> = emptyList(),
    val groups: List<GroupProximityDto> = emptyList()
)

// ---------- /api/aircraft/validate-callsigns ----------

@Serializable
data class ValidateCallsignsRequestDto(
    val callsigns: List<String>
)

@Serializable
data class CallsignValidationDto(
    val callsign: String? = null,
    /** "valid" | "no-data" */
    val status: String? = null
)

@Serializable
data class ValidateCallsignsResponseDto(
    val status: String? = null,
    val results: List<CallsignValidationDto> = emptyList(),
    val invalid: List<String> = emptyList()
)

// ---------- /api/aircraft/{icao24} ----------

@Serializable
data class AircraftTelemetryDto(
    val icao24: String? = null,
    val callsign: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude_m: Double? = null,
    val velocity_mps: Double? = null,
    val heading_deg: Double? = null,
    /** Unix seconds. */
    val last_contact: Double? = null
)

// ---------- generic {"status":"ok"} responses ----------

@Serializable
data class StatusResponseDto(
    val status: String? = null
)

// ---------- /api/user/location ----------

@Serializable
data class PostLocationRequestDto(
    val latitude: Double,
    val longitude: Double,
    val accuracy_m: Double,
    /** Epoch milliseconds. */
    val timestamp: Long,
    /** "gps" | "manual" */
    val source: String
)

// ---------- /api/health ----------

@Serializable
data class HealthResponseDto(
    val status: String? = null,
    val uptime: Double? = null,
    /** "disabled" | "reachable" | "unreachable" */
    val opensky_status: String? = null,
    val cache_entries: Int? = null,
    val location_ingest_status: String? = null,
    val last_location_timestamp: Long? = null
)

// ---------- /api/settings/api ----------

@Serializable
data class ApiInfoDto(
    val baseUrl: String? = null,
    val authUrl: String? = null,
    /** "oauth2" | "basic" | "anonymous" */
    val authMode: String? = null,
    val clientConfigured: Boolean? = null,
    val basicConfigured: Boolean? = null
)

@Serializable
data class ApiSettingsStatusDto(
    val status: String? = null,
    val api: ApiInfoDto? = null
)

// ---------- /api/app/state ----------

@Serializable
data class BackendSettingsDto(
    val apiClientId: String? = null,
    val apiClientSecret: String? = null
)

@Serializable
data class BackendAircraftDto(
    val id: String? = null,
    val icao24: String? = null,
    val callsign: String? = null,
    val notes: String? = null,
    val createdAt: String? = null
)

@Serializable
data class BackendFleetGroupDto(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val color: String? = null,
    val icon: String? = null
)

@Serializable
data class BackendFleetAircraftDto(
    val id: String? = null,
    val callsign: String? = null,
    val groupId: String? = null,
    val createdAt: String? = null
)

@Serializable
data class BackendFleetDto(
    val groups: List<BackendFleetGroupDto> = emptyList(),
    val fleetAircraft: List<BackendFleetAircraftDto> = emptyList()
)

@Serializable
data class AppStateDto(
    val settings: JsonObject? = null,
    val ui: JsonObject? = null,
    val aircraft: List<BackendAircraftDto> = emptyList(),
    val fleet: BackendFleetDto? = null
)

/**
 * PATCH-style body for POST /api/app/state. With explicitNulls=false,
 * null fields are omitted and the backend shallow-merges — so posting only
 * the settings we intend to change never touches aircraft/fleets.
 */
@Serializable
data class AppStateUpdateRequestDto(
    val settings: BackendSettingsDto? = null
)

// ---------- error bodies ----------

@Serializable
data class ErrorBodyDto(
    val error: String? = null,
    val status: Int? = null,
    val invalid: List<String> = emptyList()
)
