package com.flightradius.app.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Relative paths (no leading slash) so a configured base URL may carry a
 * path prefix, e.g. https://host:8443/sub/ .
 */
interface FlightRadiusApi {

    @POST("api/distance/compute")
    suspend fun compute(@Body body: ComputeRequestDto): Response<ComputeResponseDto>

    @POST("api/distance/fleets")
    suspend fun fleets(@Body body: FleetsRequestDto): Response<FleetsResponseDto>

    @POST("api/aircraft/validate-callsigns")
    suspend fun validateCallsigns(
        @Body body: ValidateCallsignsRequestDto
    ): Response<ValidateCallsignsResponseDto>

    @GET("api/aircraft/{icao24}")
    suspend fun aircraftTelemetry(
        @Path("icao24") icao24: String
    ): Response<AircraftTelemetryDto>

    @POST("api/user/location")
    suspend fun postLocation(@Body body: PostLocationRequestDto): Response<StatusResponseDto>

    @GET("api/health")
    suspend fun health(): Response<HealthResponseDto>

    @GET("api/settings/api")
    suspend fun apiSettingsStatus(): Response<ApiSettingsStatusDto>

    @GET("api/app/state")
    suspend fun appState(): Response<AppStateDto>

    @POST("api/app/state")
    suspend fun updateAppState(@Body body: AppStateUpdateRequestDto): Response<AppStateDto>
}
