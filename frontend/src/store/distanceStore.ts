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
  name: string;
  results: DistanceResult[];
  closest: DistanceResult | null;
  missing: string[];
};

type DistanceState = {
  aircraftDistances: Record<string, DistanceResult>;
  groupClosest: Record<string, DistanceResult | null>;
  groupResults: GroupDistanceResult[];
  missingCallsigns: string[];
  lastComputedAt: number | null;
  errorMessage: string | null;
  setDistanceResults: (payload: {
    results: DistanceResult[];
    groups: GroupDistanceResult[];
    missing: string[];
  }) => void;
  setError: (message: string | null) => void;
};

const buildLookup = (results: DistanceResult[]) =>
  results.reduce<Record<string, DistanceResult>>((acc, result) => {
    acc[result.callsign] = result;
    return acc;
  }, {});

const buildGroupClosest = (groups: GroupDistanceResult[]) =>
  groups.reduce<Record<string, DistanceResult | null>>((acc, group) => {
    acc[group.name] = group.closest;
    return acc;
  }, {});

export const useDistanceStore = create<DistanceState>()(
  persist(
    (set) => ({
      aircraftDistances: {},
      groupClosest: {},
      groupResults: [],
      missingCallsigns: [],
      lastComputedAt: null,
      errorMessage: null,
      setDistanceResults: ({ results, groups, missing }) =>
        set(() => ({
          aircraftDistances: buildLookup(results),
          groupResults: groups,
          groupClosest: buildGroupClosest(groups),
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
        groupClosest: state.groupClosest,
        groupResults: state.groupResults,
        missingCallsigns: state.missingCallsigns,
        lastComputedAt: state.lastComputedAt
      })
    }
  )
);
