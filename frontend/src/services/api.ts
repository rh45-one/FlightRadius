const API_BASE = "/api";

export const getAircraftStatus = async (icao24: string) => {
  const response = await fetch(`${API_BASE}/aircraft/${icao24}`);
  if (!response.ok) {
    throw new Error("Failed to fetch aircraft status");
  }
  return response.json();
};
