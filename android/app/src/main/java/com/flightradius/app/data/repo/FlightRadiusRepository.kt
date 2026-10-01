package com.flightradius.app.data.repo

import com.flightradius.app.data.api.ApiError
import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.api.AppStateDto
import com.flightradius.app.data.api.AppStateUpdateRequestDto
import com.flightradius.app.data.api.BackendSettingsDto
import com.flightradius.app.data.api.ComputeRequestDto
import com.flightradius.app.data.api.ComputeResponseDto
import com.flightradius.app.data.api.DistanceResultDto
import com.flightradius.app.data.api.FleetGroupRequestDto
import com.flightradius.app.data.api.FlightRadiusApi
import com.flightradius.app.data.api.GroupProximityDto
import com.flightradius.app.data.api.HealthResponseDto
import com.flightradius.app.data.api.IoErrorMapper
import com.flightradius.app.data.api.ApiSettingsStatusDto
import com.flightradius.app.data.api.PostLocationRequestDto
import com.flightradius.app.data.api.AircraftTelemetryDto
import com.flightradius.app.data.api.ValidateCallsignsRequestDto
import com.flightradius.app.data.api.ValidateCallsignsResponseDto
import com.flightradius.app.data.api.safeApiCall
import com.flightradius.app.domain.ComputeOutcome
import com.flightradius.app.domain.ComputeResultEntry
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.GroupOutcome
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.LocationSource
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.UserFix
import com.flightradius.app.data.api.retryable
import com.flightradius.app.domain.retryWithBackoff
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

private const val SECRET_MASK = "********"

data class ImportReport(
    val aircraftAdded: Int,
    val aircraftSkipped: Int,
    val fleetsAdded: Int,
    val membershipsAdded: Int,
    /** Memberships dropped because the aircraft is already in another group. */
    val membershipsSkipped: Int = 0
)

/**
 * Talks to the FlightRadius backend. Never stores OpenSky credentials
 * on-device; updateCredentials forwards them and only non-null fields are
 * sent (the backend shallow-merges `settings`).
 */
@Singleton
class FlightRadiusRepository @Inject constructor(
    private val api: FlightRadiusApi,
    private val json: Json,
    private val aircraftRepository: AircraftRepository,
    private val fleetRepository: FleetRepository,
    /**
     * Reclassifies IOException (API 37 LAN blocking ->
     * ApiError.LocalNetworkPermissionRequired). Provided by
     * [com.flightradius.app.data.api.LocalNetworkGuard] via DI.
     */
    private val ioErrorMapper: IoErrorMapper
) {
    private fun DistanceResultDto.toEntry() = ComputeResultEntry(
        callsign = callsign,
        icao24 = icao24,
        distanceKm = distance_km,
        lat = lat,
        lon = lon,
        altitudeM = altitude_m,
        velocityMps = velocity_mps,
        headingDeg = heading_deg,
        lastContactSec = last_contact
    )

    private fun GroupProximityDto.toOutcome() = GroupOutcome(
        groupName = group_name ?: "",
        closest = closest_aircraft?.toEntry(),
        membersRanked = members_ranked.map { it.toEntry() },
        missing = missing
    )

    private fun ComputeResponseDto.toOutcome() = ComputeOutcome(
        results = results.map { it.toEntry() },
        missing = missing,
        closest = closest?.toEntry(),
        groups = groups.map { it.toOutcome() }
    )

    /**
     * POST /api/distance/compute. [fleets] are sent as backend groups using
     * their callsign-typed members (icao24 members can't be resolved inside
     * groups by the backend anyway). Retried once on retryable failures.
     */
    suspend fun compute(
        fix: UserFix,
        aircraft: List<TrackedAircraft>,
        fleets: List<Fleet>
    ): ApiResult<ComputeOutcome> {
        val callsigns = aircraft.filter { it.type == IdentifierType.CALLSIGN }
            .map { it.identifier }
        val icao24s = aircraft.filter { it.type == IdentifierType.ICAO24 }
            .map { it.identifier }
        val aircraftById = aircraft.associateBy { it.id }
        val groups = fleets.map { fleet ->
            FleetGroupRequestDto(
                name = fleet.name,
                callsigns = fleet.memberIds
                    .mapNotNull { aircraftById[it] }
                    .filter { it.type == IdentifierType.CALLSIGN }
                    .map { it.identifier }
            )
        }
        val request = ComputeRequestDto(
            user_location = com.flightradius.app.data.api.UserLocationDto(
                lat = fix.lat,
                lon = fix.lon
            ),
            callsigns = callsigns,
            icao24s = icao24s,
            groups = groups
        )
        return retryWithBackoff(
            maxAttempts = 2,
            shouldRetry = { r: ApiResult<ComputeResponseDto> -> r.retryable }
        ) {
            safeApiCall(json, ioErrorMapper::map) { api.compute(request) }
        }.let { result ->
            when (result) {
                is ApiResult.Success -> ApiResult.Success(result.data.toOutcome())
                is ApiResult.Failure -> ApiResult.Failure(result.error)
            }
        }
    }

    /** POST /api/aircraft/validate-callsigns. */
    suspend fun validateCallsigns(
        callsigns: List<String>
    ): ApiResult<ValidateCallsignsResponseDto> =
        safeApiCall(json, ioErrorMapper::map) { api.validateCallsigns(ValidateCallsignsRequestDto(callsigns)) }

    /** GET /api/aircraft/{icao24}. */
    suspend fun lookupIcao24(icao24: String): ApiResult<AircraftTelemetryDto> =
        safeApiCall(json, ioErrorMapper::map) { api.aircraftTelemetry(icao24.lowercase()) }

    /** POST /api/user/location. No retry — a missed fix is harmless. */
    suspend fun postLocation(fix: UserFix): ApiResult<Unit> =
        when (val r = safeApiCall(json, ioErrorMapper::map) {
            api.postLocation(
                PostLocationRequestDto(
                    latitude = fix.lat,
                    longitude = fix.lon,
                    accuracy_m = fix.accuracyM ?: 0.0,
                    timestamp = fix.timeMs,
                    source = when (fix.source) {
                        LocationSource.GPS -> "gps"
                        LocationSource.MANUAL -> "manual"
                    }
                )
            )
        }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> ApiResult.Failure(r.error)
        }

    /** GET /api/health — triggers a real OpenSky fetch on the backend; call
     *  only on explicit user "Test connection", never in the loop. */
    suspend fun health(): ApiResult<HealthResponseDto> =
        safeApiCall(json, ioErrorMapper::map) { api.health() }

    /** GET /api/settings/api — auth mode + whether creds are configured. */
    suspend fun getApiSettingsStatus(): ApiResult<ApiSettingsStatusDto> =
        safeApiCall(json, ioErrorMapper::map) { api.apiSettingsStatus() }

    /**
     * Forwards the OpenSky API client to the backend via POST /api/app/state
     * (the backend shallow-merges `settings`). Only non-null, non-masked
     * fields are sent; empty string clears. Values are never logged.
     */
    suspend fun updateCredentials(
        clientId: String? = null,
        clientSecret: String? = null
    ): ApiResult<Unit> {
        fun keep(v: String?): String? = v?.takeIf { it != SECRET_MASK }
        val patch = BackendSettingsDto(apiClientId = keep(clientId), apiClientSecret = keep(clientSecret))
        if (patch.apiClientId == null && patch.apiClientSecret == null) return ApiResult.Success(Unit)
        return when (val r = safeApiCall(json, ioErrorMapper::map) {
            api.updateAppState(AppStateUpdateRequestDto(settings = patch))
        }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> ApiResult.Failure(r.error)
        }
    }

    /**
     * GET /api/app/state -> merge into Room. Never POSTs aircraft/fleets.
     * - aircraft[]: icao24 present -> ICAO24 entry; else callsign -> CALLSIGN.
     *   Existing identifiers skipped.
     * - fleet.groups[]: matched by name (created when missing); hex color
     *   parsed, else palette default.
     * - fleet.fleetAircraft[]: callsigns become tracked aircraft too;
     *   groupId resolves membership via the backend group's name.
     */
    suspend fun importFromBackend(): ApiResult<ImportReport> {
        val state = when (val r = safeApiCall(json, ioErrorMapper::map) { api.appState() }) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> return ApiResult.Failure(r.error)
        }
        return ApiResult.Success(mergeAppState(state))
    }

    private suspend fun mergeAppState(state: AppStateDto): ImportReport {
        var aircraftAdded = 0
        var aircraftSkipped = 0
        var fleetsAdded = 0
        var membershipsAdded = 0
        var membershipsSkipped = 0

        val localByIdentifier = aircraftRepository.getAll()
            .associateBy { it.identifier }
            .toMutableMap()

        suspend fun ensureCallsign(raw: String?, createdAt: Long): TrackedAircraft? {
            val normalized = com.flightradius.app.domain.Identifiers
                .normalize(raw ?: return null, IdentifierType.CALLSIGN) ?: return null
            localByIdentifier[normalized]?.let { aircraftSkipped++; return it }
            val id = aircraftRepository.add(normalized, IdentifierType.CALLSIGN, createdAt = createdAt)
            return if (id != null) {
                aircraftAdded++
                TrackedAircraft(id, normalized, IdentifierType.CALLSIGN, createdAt = createdAt)
                    .also { localByIdentifier[normalized] = it }
            } else {
                aircraftSkipped++
                localByIdentifier[normalized]
            }
        }

        // 1. aircraft[]
        for (a in state.aircraft) {
            val createdAt = a.createdAt.toEpochMs()
            if (!a.icao24.isNullOrBlank()) {
                val normalized = com.flightradius.app.domain.Identifiers
                    .normalize(a.icao24, IdentifierType.ICAO24)
                if (normalized == null) { aircraftSkipped++; continue }
                if (localByIdentifier.containsKey(normalized)) { aircraftSkipped++; continue }
                val id = aircraftRepository.add(
                    normalized, IdentifierType.ICAO24,
                    notes = a.notes, createdAt = createdAt
                )
                if (id != null) {
                    aircraftAdded++
                    localByIdentifier[normalized] =
                        TrackedAircraft(id, normalized, IdentifierType.ICAO24,
                            notes = a.notes, createdAt = createdAt)
                } else aircraftSkipped++
            } else if (!a.callsign.isNullOrBlank()) {
                val addedBefore = aircraftAdded
                val skippedBefore = aircraftSkipped
                ensureCallsign(a.callsign, createdAt)
                // Invalid identifier: ensureCallsign changed nothing.
                if (aircraftAdded == addedBefore && aircraftSkipped == skippedBefore) {
                    aircraftSkipped++
                }
            } else {
                aircraftSkipped++
            }
        }

        // 2. fleet groups: backendId -> local fleet
        val localFleetByBackendId = HashMap<String, Fleet>()
        val existingFleets = fleetRepository.getAll().associateBy { it.name }
        for (g in state.fleet?.groups.orEmpty()) {
            val name = g.name?.trim().takeUnless { it.isNullOrEmpty() } ?: continue
            val existing = existingFleets[name]
            val colorArgb = parseHexColorArgb(g.color)
            val fleetId = if (existing != null) {
                existing.id
            } else {
                fleetRepository.getOrCreate(name = name, colorArgb = colorArgb)
            }
            if (fleetId == null) continue
            if (existing == null) fleetsAdded++
            val gId = g.id ?: continue
            localFleetByBackendId[gId] = Fleet(
                id = fleetId, name = name,
                colorArgb = colorArgb ?: existing?.colorArgb
                    ?: FLEET_COLOR_PALETTE[0],
                alertRadiusKm = existing?.alertRadiusKm,
                memberIds = existing?.memberIds ?: emptySet()
            )
        }

        // 3. fleetAircraft[]: ensure aircraft + membership
        for (fa in state.fleet?.fleetAircraft.orEmpty()) {
            val createdAt = fa.createdAt.toEpochMs()
            val aircraft = ensureCallsign(fa.callsign, createdAt) ?: continue
            val fleet = fa.groupId?.let { localFleetByBackendId[it] } ?: continue
            if (aircraft.id !in fleet.memberIds) {
                // An aircraft is in at most one group: first one wins.
                if (fleetRepository.addMemberIfUngrouped(fleet.id, aircraft.id)) {
                    membershipsAdded++
                } else {
                    membershipsSkipped++
                }
            }
        }

        return ImportReport(
            aircraftAdded = aircraftAdded,
            aircraftSkipped = aircraftSkipped,
            fleetsAdded = fleetsAdded,
            membershipsAdded = membershipsAdded,
            membershipsSkipped = membershipsSkipped
        )
    }

    private fun String?.toEpochMs(): Long =
        this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: System.currentTimeMillis()
}
