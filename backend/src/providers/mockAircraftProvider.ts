import { getMockAircraftPositions } from "../services/mockAircraft";
import { DistancePosition } from "../services/distanceEngine";

export class MockAircraftProvider {
  static async get_positions(callsigns: string[]) {
    return this.getPositions(callsigns);
  }

  static async getPositions(callsigns: string[]) {
    const positions = await getMockAircraftPositions();
    if (!callsigns || callsigns.length === 0) {
      return positions as DistancePosition[];
    }

    const lookup = new Map(
      positions.map((entry) => [entry.callsign.toUpperCase(), entry])
    );

    return callsigns
      .map((callsign) => lookup.get(callsign.trim().toUpperCase()))
      .filter((entry): entry is DistancePosition => Boolean(entry));
  }
}
