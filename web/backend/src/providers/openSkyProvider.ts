import {
  getAircraftTelemetryByCallsigns,
  getAircraftTelemetryByIcao24s
} from "../services/opensky";
import { DistancePosition } from "../services/distanceEngine";

export class OpenSkyProvider {
  static async get_positions(input: {
    callsigns?: string[];
    icao24s?: string[];
  }) {
    return this.getPositions(input);
  }

  static async getPositions(input: {
    callsigns?: string[];
    icao24s?: string[];
  }): Promise<DistancePosition[]> {
    const callsigns = input.callsigns || [];
    const icao24s = input.icao24s || [];

    const telemetryByCallsign = callsigns.length > 0
      ? await getAircraftTelemetryByCallsigns(callsigns)
      : [];

    // One icao24-filtered request (1 credit) instead of a lookup per aircraft.
    const telemetryByIcao = icao24s.length > 0
      ? await getAircraftTelemetryByIcao24s(icao24s)
      : [];

    const combined = [...telemetryByCallsign, ...telemetryByIcao];

    return combined.map((entry) => {
      const fallbackLat = 40.4168;
      const fallbackLon = -3.7038;
      const hasValidLat = Number.isFinite(entry.latitude);
      const hasValidLon = Number.isFinite(entry.longitude);
      if (!hasValidLat || !hasValidLon) {
        console.log("[DISTANCE] Using fallback coordinates", {
          callsign: entry.callsign,
          icao24: entry.icao24
        });
      }

      return {
      callsign: (entry.callsign || entry.icao24).trim().toUpperCase(),
      icao24: entry.icao24.toLowerCase(),
        lat: hasValidLat ? entry.latitude : fallbackLat,
        lon: hasValidLon ? entry.longitude : fallbackLon,
      altitude_m: entry.altitude_m,
      last_update: new Date(entry.last_contact * 1000).toISOString(),
      velocity_mps: entry.velocity_mps ?? null,
      heading_deg: entry.heading_deg ?? null,
      last_contact: entry.last_contact ?? null
      };
    });
  }
}
