import { calculateDistanceKm } from "./distance";
import { MockAircraftPosition } from "./mockAircraft";

export type DistanceResult = {
  callsign: string;
  distance_km: number;
  lat: number;
  lon: number;
  altitude_m: number;
  last_update: string;
};

export type DistanceSummary = {
  results: DistanceResult[];
  missing: string[];
  closest: DistanceResult | null;
};

export type UserLocation = {
  lat: number;
  lon: number;
};

const normalizeCallsign = (callsign: string) => callsign.trim().toUpperCase();

export const buildDistanceResults = (
  userLocation: UserLocation,
  positions: MockAircraftPosition[],
  callsigns: string[]
): DistanceSummary => {
  const lookup = new Map(
    positions.map((entry) => [entry.callsign.toUpperCase(), entry])
  );

  const results: DistanceResult[] = [];
  const missing: string[] = [];

  for (const callsign of callsigns) {
    const normalized = normalizeCallsign(callsign);
    const entry = lookup.get(normalized);
    if (!entry) {
      missing.push(normalized);
      continue;
    }

    results.push({
      callsign: normalized,
      distance_km: calculateDistanceKm(
        userLocation.lat,
        userLocation.lon,
        entry.lat,
        entry.lon
      ),
      lat: entry.lat,
      lon: entry.lon,
      altitude_m: entry.altitude_m,
      last_update: entry.last_update
    });
  }

  results.sort((a, b) => a.distance_km - b.distance_km);

  return {
    results,
    missing,
    closest: results.length > 0 ? results[0] : null
  };
};
