import { describe, expect, it } from "vitest";
import { calculateDistanceKm } from "./distance";
import { buildDistanceResults, buildGroupProximity } from "./distanceEngine";
import { MockAircraftPosition } from "./mockAircraft";

describe("calculateDistanceKm", () => {
  it("returns a reasonable distance between London and Paris", () => {
    const distance = calculateDistanceKm(51.5074, -0.1278, 48.8566, 2.3522);
    expect(distance).toBeGreaterThan(343);
    expect(distance).toBeLessThan(344);
  });
});

describe("buildDistanceResults", () => {
  const positions: MockAircraftPosition[] = [
    {
      callsign: "AAA100",
      lat: 52.52,
      lon: 13.405,
      altitude_m: 11000,
      last_update: "2026-02-19T09:40:00Z"
    },
    {
      callsign: "BBB200",
      lat: 48.8566,
      lon: 2.3522,
      altitude_m: 10500,
      last_update: "2026-02-19T09:41:00Z"
    },
    {
      callsign: "CCC300",
      lat: 41.9028,
      lon: 12.4964,
      altitude_m: 10200,
      last_update: "2026-02-19T09:42:00Z"
    }
  ];

  it("selects the closest aircraft and sorts results", () => {
    const result = buildDistanceResults(
      { lat: 50.1109, lon: 8.6821 },
      positions,
      ["AAA100", "BBB200", "CCC300"]
    );

    expect(result.closest?.callsign).toBe("AAA100");
    expect(result.results[0].callsign).toBe("AAA100");
    expect(result.results).toHaveLength(3);
  });

  it("tracks missing callsigns", () => {
    const result = buildDistanceResults(
      { lat: 50.1109, lon: 8.6821 },
      positions,
      ["AAA100", "ZZZ999"]
    );

    expect(result.missing).toEqual(["ZZZ999"]);
    expect(result.results).toHaveLength(1);
  });

  it("ranks group results by proximity", () => {
    const groupResult = buildDistanceResults(
      { lat: 50.1109, lon: 8.6821 },
      positions,
      ["CCC300", "BBB200"]
    );

    expect(groupResult.results[0].callsign).toBe("BBB200");
    expect(groupResult.results[1].callsign).toBe("CCC300");
  });

  it("aggregates fleet proximity", () => {
    const result = buildGroupProximity(
      { lat: 50.1109, lon: 8.6821 },
      positions,
      [
        { name: "Fleet A", callsigns: ["AAA100", "CCC300"] },
        { name: "Fleet B", callsigns: ["BBB200"] }
      ]
    );

    expect(result).toHaveLength(2);
    expect(result[0].group_name).toBe("Fleet A");
    expect(result[0].closest_aircraft?.callsign).toBe("AAA100");
    expect(result[1].members_ranked[0].callsign).toBe("BBB200");
  });
});
