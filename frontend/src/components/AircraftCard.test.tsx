import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import AircraftCard from "./AircraftCard";

const baseAircraft = {
  id: "1",
  callsign: "BAW123",
  createdAt: new Date().toISOString()
};

describe("AircraftCard", () => {
  it("renders distance and last update", () => {
    render(
      <AircraftCard
        aircraft={baseAircraft}
        onRemove={() => undefined}
        distanceUnit="km"
        status="live"
        distanceData={{
          callsign: "BAW123",
          distance_km: 215.12,
          lat: 51.47,
          lon: -0.45,
          altitude_m: 10600,
          last_update: "2026-02-19T09:45:00Z"
        }}
      />
    );

    expect(screen.getByText(/215.12 km/i)).toBeInTheDocument();
    expect(screen.getByText(/Last update/i)).toBeInTheDocument();
  });
});
