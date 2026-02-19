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

    return combined.map((entry) => ({
      callsign: (entry.callsign || entry.icao24).trim().toUpperCase(),
      lat: entry.latitude,
      lon: entry.longitude,
      altitude_m: entry.altitude_m,
      last_update: new Date(entry.last_contact * 1000).toISOString()
    }));
  }
}
