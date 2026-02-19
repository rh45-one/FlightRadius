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

export type AppStatePayload = {
  settings: {
    refreshIntervalSec: number;
    distanceUnit: "km" | "mi";
    maxTrackedWarning: number;
    locationMode: "gps" | "manual";
    autoRefreshOnMovement: boolean;
    autoEnableLocationOnDashboard: boolean;
    distanceUpdateIntervalSec: number;
    gpsPollingIntervalSec: number;
    manualLatitude: string;
    manualLongitude: string;
    gpsAccuracyMode: "high" | "balanced";
    apiBaseUrl: string;
    apiAuthUrl: string;
    apiUsername: string;
    apiPassword: string;
    apiClientId: string;
    apiClientSecret: string;
  };
  ui: {
    theme: "dark" | "light";
    cardDensity: "comfortable" | "compact";
    timeFormat: "24h" | "12h";
  };
  aircraft: {
    id: string;
    icao24?: string;
    callsign?: string;
    notes?: string;
    createdAt: string;
  }[];
  fleet: {
    groups: {
      id: string;
      name: string;
      description?: string;
      color: string;
      icon: string;
    }[];
    fleetAircraft: {
      id: string;
      callsign: string;
      groupId?: string;
      createdAt: string;
    }[];
  };
};

export type DistanceResult = {
  callsign: string;
  distance_km: number;
  lat: number;
  lon: number;
  altitude_m: number;
  last_update: string;
};

export type FleetProximityResult = {
  group_name: string;
  closest_aircraft: DistanceResult | null;
  members_ranked: DistanceResult[];
  missing: string[];
};

export type DistanceAircraftResponse = {
  results: DistanceResult[];
  closest: DistanceResult | null;
  missing: string[];
};

export type DistanceFleetResponse = {
  fleets: FleetProximityResult[];
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

export const getAppState = async () => {
  const response = await fetch(`${API_BASE}/app/state`);
  if (!response.ok) {
    throw new Error("Failed to load app state");
  }
  return (await response.json()) as AppStatePayload;
};

export const saveAppState = async (payload: AppStatePayload) => {
  const response = await fetch(`${API_BASE}/app/state`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    throw new Error("Failed to save app state");
  }
  return (await response.json()) as AppStatePayload;
};

export const computeAircraftDistances = async (payload: {
  lat: number;
  lon: number;
  callsigns?: string[];
  icao24s?: string[];
}) => {
  const response = await fetch(`${API_BASE}/distance/aircraft`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error || "Failed to compute aircraft distances");
  }

  return (await response.json()) as DistanceAircraftResponse;
};

export const computeFleetDistances = async (payload: {
  lat: number;
  lon: number;
  fleets: { name: string; callsigns: string[] }[];
}) => {
  const response = await fetch(`${API_BASE}/distance/fleets`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error || "Failed to compute fleet distances");
  }

  return (await response.json()) as DistanceFleetResponse;
};
