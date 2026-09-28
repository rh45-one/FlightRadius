import { calculateDistanceKm } from "./distance";
export type DistancePosition = {
  callsign: string;
  icao24?: string;
  lat: number;
  lon: number;
  altitude_m: number;
  last_update: string;
};

export type DistanceResult = {
  callsign: string;
  icao24?: string;
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

export type FleetGroupInput = {
  name: string;
  callsigns: string[];
};

export type FleetProximityResult = {
  group_name: string;
  closest_aircraft: DistanceResult | null;
  members_ranked: DistanceResult[];
  missing: string[];
};

const normalizeCallsign = (callsign: string) => callsign.trim().toUpperCase();

export const buildDistanceResults = (
  userLocation: UserLocation,
  positions: DistancePosition[],
  identifiers: string[]
): DistanceSummary => {
  const lookup = new Map(
    positions.flatMap((entry) => {
      const keys = [entry.callsign.toUpperCase()];
      if (entry.icao24) {
        keys.push(entry.icao24.toLowerCase());
      }
      return keys.map((key) => [key, entry] as const);
    })
  );

  const results: DistanceResult[] = [];
  const missing: string[] = [];

  for (const identifier of identifiers) {
    const normalized = normalizeCallsign(identifier);
    const entry = lookup.get(normalized) || lookup.get(identifier.toLowerCase());
    if (!entry) {
      missing.push(normalized);
      continue;
    }

    results.push({
      callsign: entry.callsign.toUpperCase(),
      icao24: entry.icao24,
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

export const buildGroupProximity = (
  userLocation: UserLocation,
  positions: DistancePosition[],
  groups: FleetGroupInput[]
): FleetProximityResult[] => {
  return groups.map((group) => {
    const summary = buildDistanceResults(userLocation, positions, group.callsigns);
    return {
      group_name: group.name,
      closest_aircraft: summary.closest,
      members_ranked: summary.results,
      missing: summary.missing
    };
  });
};
