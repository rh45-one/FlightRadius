import { getCacheEntry, setCacheEntry } from "./cache";
import { getApiSettings } from "./settings";

export type OpenSkyConfig = {
  baseUrl: string;
  username?: string;
  password?: string;
};

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

type OpenSkyResponse = {
  time?: number;
  states?: (Array<string | number | null>)[] | null;
};

class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

const RATE_LIMIT_MS = 5_000;
let lastFetchAt = 0;
let inFlight: Promise<OpenSkyResponse> | null = null;

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const buildAuthHeader = (config: OpenSkyConfig) => {
  if (config.username && config.password) {
    const token = Buffer.from(
      `${config.username}:${config.password}`
    ).toString("base64");
    return `Basic ${token}`;
  }
  return undefined;
};

const getConfig = (): OpenSkyConfig => {
  const settings = getApiSettings();
  return {
    baseUrl: settings.baseUrl || "https://opensky-network.org/api",
    username: settings.username || undefined,
    password: settings.password || undefined
  };
};

const fetchStates = async (config: OpenSkyConfig) => {
  const authHeader = buildAuthHeader(config);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 5000);

  console.log("OpenSky request start");

  try {
    const response = await fetch(`${config.baseUrl}/states/all`, {
      method: "GET",
      headers: authHeader ? { Authorization: authHeader } : undefined,
      signal: controller.signal
    });

    if (!response.ok) {
      throw new ApiError("OpenSky unavailable", 502);
    }

    const data = (await response.json()) as OpenSkyResponse;
    console.log("OpenSky request end");
    return data;
  } catch (error) {
    if ((error as Error).name === "AbortError") {
      throw new ApiError("OpenSky timeout", 504);
    }
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError("OpenSky unavailable", 502);
  } finally {
    clearTimeout(timeout);
  }
};

const fetchStatesQueued = async (config: OpenSkyConfig) => {
  if (inFlight) {
    return inFlight;
  }

  inFlight = (async () => {
    const now = Date.now();
    const elapsed = now - lastFetchAt;
    if (elapsed < RATE_LIMIT_MS) {
      console.log("Rate limit triggered");
      await delay(RATE_LIMIT_MS - elapsed);
    }

    lastFetchAt = Date.now();
    return fetchStates(config);
  })();

  try {
    return await inFlight;
  } finally {
    inFlight = null;
  }
};

const normalizeState = (state: Array<string | number | null>) => {
  const icao24 = typeof state[0] === "string" ? state[0].toLowerCase() : null;
  const callsign = typeof state[1] === "string" ? state[1].trim() : null;
  const longitude = typeof state[5] === "number" ? state[5] : null;
  const latitude = typeof state[6] === "number" ? state[6] : null;
  const altitude = typeof state[7] === "number" ? state[7] : null;
  const velocity = typeof state[9] === "number" ? state[9] : null;
  const heading = typeof state[10] === "number" ? state[10] : null;
  const lastContact = typeof state[4] === "number" ? state[4] : null;

  if (
    icao24 === null ||
    latitude === null ||
    longitude === null ||
    altitude === null ||
    velocity === null ||
    heading === null ||
    lastContact === null
  ) {
    throw new ApiError("Null telemetry fields", 502);
  }

  return {
    icao24,
    callsign: callsign || null,
    latitude,
    longitude,
    altitude_m: altitude,
    velocity_mps: velocity,
    heading_deg: heading,
    last_contact: lastContact
  } satisfies AircraftTelemetry;
};

export const getAircraftTelemetry = async (icao24: string) => {
  const key = icao24.toLowerCase();
  const cached = getCacheEntry<AircraftTelemetry>(key);

  if (cached) {
    console.log("Cache hit", key);
    return cached.value;
  }

  const now = Date.now();
  if (now - lastFetchAt < RATE_LIMIT_MS) {
    const stale = getCacheEntry<AircraftTelemetry>(key, { allowStale: true });
    if (stale) {
      console.log("Rate limited, returning cached", key);
      return stale.value;
    }
  }

  console.log("Cache miss", key);

  const config = getConfig();
  const response = await fetchStatesQueued(config);
  const states = response.states || [];
  const state = states.find(
    (item) => typeof item[0] === "string" && item[0].toLowerCase() === key
  );

  if (!state) {
    throw new ApiError("Aircraft not found", 404);
  }

  const telemetry = normalizeState(state);
  setCacheEntry(key, telemetry);
  return telemetry;
};

export const pingOpenSky = async () => {
  const config = getConfig();
  try {
    await fetchStatesQueued(config);
    return "reachable";
  } catch (error) {
    console.error("OpenSky ping error", error);
    return "unreachable";
  }
};

export const isValidIcao24 = (input: string) => {
  return /^[a-f0-9]{6}$/i.test(input);
};

export { ApiError };
