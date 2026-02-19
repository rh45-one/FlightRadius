import { getAircraftTelemetryByCallsigns } from "../services/opensky";
import { DistancePosition } from "../services/distanceEngine";

export class OpenSkyProvider {
  static async get_positions(callsigns: string[]) {
    return this.getPositions(callsigns);
  }

  static async getPositions(callsigns: string[]): Promise<DistancePosition[]> {
    const telemetry = await getAircraftTelemetryByCallsigns(callsigns);
    return telemetry
      .filter((entry) => Boolean(entry.callsign))
      .map((entry) => ({
        callsign: (entry.callsign || "").trim().toUpperCase(),
        lat: entry.latitude,
        lon: entry.longitude,
        altitude_m: entry.altitude_m,
        last_update: new Date(entry.last_contact * 1000).toISOString()
      }));
  }
}
