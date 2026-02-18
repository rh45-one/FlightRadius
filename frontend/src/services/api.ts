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
  const response = await fetch(
    `${API_BASE}/aircraft/${encodeURIComponent(icao24)}`
  );
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error || "Failed to fetch aircraft status");
  }
  return (await response.json()) as AircraftTelemetry;
};

export const getAircraftStatusByCallsign = async (callsign: string) => {
  const response = await fetch(
    `${API_BASE}/aircraft/callsign/${encodeURIComponent(callsign)}`
  );
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error || "Failed to fetch aircraft status");
  }
  return (await response.json()) as AircraftTelemetry;
};

export const validateCallsigns = async (callsigns: string[]) => {
  const response = await fetch(`${API_BASE}/aircraft/validate-callsigns`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ callsigns })
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error || "Failed to validate callsigns");
  }

  return (await response.json()) as {
    status: string;
    results: { callsign: string; status: "valid" | "no-data" }[];
  };
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
