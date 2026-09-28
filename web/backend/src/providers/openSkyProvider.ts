import {
  getAircraftTelemetry,
  getAircraftTelemetryByCallsigns
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

    const telemetryByIcao = await Promise.all(
      icao24s.map(async (icao24) => {
        try {
          return await getAircraftTelemetry(icao24);
        } catch (_error) {
          return null;
        }
      })
    );

    const combined = [...telemetryByCallsign, ...telemetryByIcao].filter(
      (entry): entry is NonNullable<typeof entry> => Boolean(entry)
    );

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
      last_update: new Date(entry.last_contact * 1000).toISOString()
      };
    });
  }
}
