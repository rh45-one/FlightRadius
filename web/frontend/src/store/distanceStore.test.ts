import { describe, expect, it } from "vitest";
import { useDistanceStore } from "./distanceStore";

describe("distanceStore", () => {
  it("stores closest overall aircraft", () => {
    useDistanceStore.setState({
      aircraftDistances: {},
      aircraftRanked: [],
      fleetDistances: [],
      closestOverall: null,
      missingCallsigns: [],
      lastComputedAt: null,
      errorMessage: null
    });

    useDistanceStore.getState().setDistanceResults({
      aircraftResults: [
        {
          callsign: "AAA100",
          distance_km: 120,
          lat: 50,
          lon: 8,
          altitude_m: 10000,
          last_update: "2026-02-19T09:00:00Z"
        }
      ],
      fleetResults: [],
      closestOverall: {
        callsign: "AAA100",
        distance_km: 120,
        lat: 50,
        lon: 8,
        altitude_m: 10000,
        last_update: "2026-02-19T09:00:00Z"
      },
      missing: []
    });

    expect(useDistanceStore.getState().closestOverall?.callsign).toBe("AAA100");
  });
});
