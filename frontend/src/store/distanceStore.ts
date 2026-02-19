import { create } from "zustand";
import { persist } from "zustand/middleware";

export type DistanceResult = {
  callsign: string;
  distance_km: number;
  lat: number;
  lon: number;
  altitude_m: number;
  last_update: string;
};

export type GroupDistanceResult = {
  group_name: string;
  closest_aircraft: DistanceResult | null;
  members_ranked: DistanceResult[];
  missing: string[];
};

type DistanceState = {
  aircraftDistances: Record<string, DistanceResult>;
  aircraftRanked: DistanceResult[];
  fleetDistances: GroupDistanceResult[];
  closestOverall: DistanceResult | null;
  missingCallsigns: string[];
  lastComputedAt: number | null;
  errorMessage: string | null;
  setDistanceResults: (payload: {
    aircraftResults: DistanceResult[];
    fleetResults: GroupDistanceResult[];
    closestOverall: DistanceResult | null;
    missing: string[];
  }) => void;
  setError: (message: string | null) => void;
};

const buildLookup = (results: DistanceResult[]) =>
  results.reduce<Record<string, DistanceResult>>((acc, result) => {
    acc[result.callsign] = result;
    return acc;
  }, {});

export const useDistanceStore = create<DistanceState>()(
  persist(
    (set) => ({
      aircraftDistances: {},
      aircraftRanked: [],
      fleetDistances: [],
      closestOverall: null,
      missingCallsigns: [],
      lastComputedAt: null,
      errorMessage: null,
      setDistanceResults: ({
        aircraftResults,
        fleetResults,
        closestOverall,
        missing
      }) =>
        set(() => ({
          aircraftDistances: buildLookup(aircraftResults),
          aircraftRanked: aircraftResults,
          fleetDistances: fleetResults,
          closestOverall,
          missingCallsigns: missing,
          lastComputedAt: Date.now(),
          errorMessage: null
        })),
      setError: (message) => set(() => ({ errorMessage: message }))
    }),
    {
      name: "flightRadius.distances",
      partialize: (state) => ({
        aircraftDistances: state.aircraftDistances,
        aircraftRanked: state.aircraftRanked,
        fleetDistances: state.fleetDistances,
        closestOverall: state.closestOverall,
        missingCallsigns: state.missingCallsigns,
        lastComputedAt: state.lastComputedAt
      })
    }
  )
);
