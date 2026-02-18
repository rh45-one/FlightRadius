const API_BASE = "/api";

export type AircraftTelemetry = {
  icao24: string;
  callsign: string | null;
  latitude: number;
  longitude: number;
  altitude_m: number;
  velocity_mps: number;
  heading_deg: number;
  last_contact: number;
};

export type UserLocationPayload = {
  latitude: number;
  longitude: number;
  accuracy_m: number;
  timestamp: number;
  source: "gps" | "manual";
};

export const getAircraftStatus = async (icao24: string) => {
  const response = await fetch(`${API_BASE}/aircraft/${icao24}`);
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error || "Failed to fetch aircraft status");
  }
  return (await response.json()) as AircraftTelemetry;
};

export const updateApiSettings = async (payload: {
  baseUrl: string;
  authUrl: string;
  username: string;
  password: string;
  clientId: string;
  clientSecret: string;
}) => {
  const response = await fetch(`${API_BASE}/settings/api`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error || "Failed to update API settings");
  }

  return response.json();
};

export const postUserLocation = async (payload: UserLocationPayload) => {
  const response = await fetch(`${API_BASE}/user/location`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error || "Failed to ingest location");
  }
};
