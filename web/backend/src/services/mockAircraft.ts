import { promises as fs } from "fs";
import path from "path";

export type MockAircraftPosition = {
  callsign: string;
  lat: number;
  lon: number;
  altitude_m: number;
  last_update: string;
};

let cached: MockAircraftPosition[] | null = null;

const resolveMockPath = () =>
  path.join(__dirname, "../mock/aircraft_positions.json");

export const getMockAircraftPositions = async () => {
  if (cached) {
    return cached;
  }

  const filePath = resolveMockPath();
  const raw = await fs.readFile(filePath, "utf8");
  const parsed = JSON.parse(raw) as MockAircraftPosition[];

  cached = parsed.map((entry) => ({
    ...entry,
    callsign: entry.callsign.toUpperCase()
  }));

  return cached;
};

export const validateMockCallsigns = async (callsigns: string[]) => {
  const positions = await getMockAircraftPositions();
  const lookup = new Set(positions.map((entry) => entry.callsign.toUpperCase()));

  return callsigns.map((callsign) => {
    const normalized = callsign.trim().toUpperCase();
    return {
      callsign,
      status: lookup.has(normalized) ? "valid" : "no-data"
    } as const;
  });
};
