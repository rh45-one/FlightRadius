import { getCacheEntry, setCacheEntry } from "./cache";
import { getApiSettings } from "./settings";

export type OpenSkyConfig = {
  baseUrl: string;
  username?: string;
  password?: string;
  authUrl?: string;
  clientId?: string;
  clientSecret?: string;
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

type TokenResponse = {
  access_token?: string;
  expires_in?: number;
  token_type?: string;
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
let tokenCache: { accessToken: string; expiresAt: number } | null = null;
let tokenInFlight: Promise<string> | null = null;

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const buildBasicAuthHeader = (config: OpenSkyConfig) => {
  if (!config.username || !config.password) {
    return undefined;
  }

  const token = Buffer.from(`${config.username}:${config.password}`).toString(
    "base64"
  );
  return `Basic ${token}`;
};

const getConfig = (): OpenSkyConfig => {
  const settings = getApiSettings();
  return {
    baseUrl: settings.baseUrl || "https://opensky-network.org/api",
    authUrl: settings.authUrl || undefined,
    username: settings.username || undefined,
    password: settings.password || undefined,
    clientId: settings.clientId || undefined,
    clientSecret: settings.clientSecret || undefined
  };
};

const resetToken = () => {
  tokenCache = null;
};

const fetchAccessToken = async (config: OpenSkyConfig) => {
  if (!config.clientId || !config.clientSecret) {
    throw new ApiError("OpenSky auth not configured", 401);
  }

  const authUrl =
    config.authUrl ||
    "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token";

  console.log("OpenSky token request start");

  const body = new URLSearchParams({
    grant_type: "client_credentials",
    client_id: config.clientId,
    client_secret: config.clientSecret
  });

  const response = await fetch(authUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body: body.toString()
  });

  if (!response.ok) {
    throw new ApiError("OpenSky auth failed", 502);
  }

  const payload = (await response.json()) as TokenResponse;
  const accessToken = payload.access_token;
  const expiresIn = payload.expires_in ?? 1800;

  if (!accessToken) {
    throw new ApiError("OpenSky auth failed", 502);
  }

  tokenCache = {
    accessToken,
    expiresAt: Date.now() + (expiresIn - 60) * 1000
  };

  console.log("OpenSky token request end");
  return accessToken;
};

const getAccessToken = async (config: OpenSkyConfig) => {
  if (!config.clientId || !config.clientSecret) {
    return null;
  }

  if (tokenCache && Date.now() < tokenCache.expiresAt) {
    return tokenCache.accessToken;
  }

  if (tokenInFlight) {
    return tokenInFlight;
  }

  tokenInFlight = fetchAccessToken(config);
  try {
    return await tokenInFlight;
  } finally {
    tokenInFlight = null;
  }
};

const buildAuthHeader = async (config: OpenSkyConfig) => {
  const token = await getAccessToken(config);
  if (token) {
    return { header: `Bearer ${token}`, scheme: "bearer" } as const;
  }

  const basic = buildBasicAuthHeader(config);
  if (basic) {
    return { header: basic, scheme: "basic" } as const;
  }

  return { header: undefined, scheme: "none" } as const;
};

const fetchStates = async (config: OpenSkyConfig, allowRetry = true) => {
  const auth = await buildAuthHeader(config);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 5000);

  console.log("OpenSky request start", {
    auth: auth.scheme,
    baseUrl: config.baseUrl
  });

  try {
    const response = await fetch(`${config.baseUrl}/states/all`, {
      method: "GET",
      headers: auth.header ? { Authorization: auth.header } : undefined,
      signal: controller.signal
    });

    if (response.status === 401 && auth.scheme === "bearer" && allowRetry) {
      resetToken();
      return fetchStates(config, false);
    }

    if (!response.ok) {
      const body = await response.text().catch(() => "");
      console.log("OpenSky response error", {
        status: response.status,
        body: body.slice(0, 200)
      });
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

const normalizeCallsign = (callsign: string) => callsign.trim().toUpperCase();

export const isValidCallsign = (input: string) =>
  /^[A-Z0-9]{2,8}$/.test(normalizeCallsign(input));

const getStatesSnapshot = async () => {
  const config = getConfig();
  const response = await fetchStatesQueued(config);
  return response.states || [];
};

export const getAircraftTelemetryByCallsigns = async (callsigns: string[]) => {
  const targets = new Set(callsigns.map(normalizeCallsign));
  if (targets.size === 0) {
    return [] as AircraftTelemetry[];
  }

  const states = await getStatesSnapshot();
  const telemetry: AircraftTelemetry[] = [];

  for (const state of states) {
    const value = typeof state[1] === "string" ? state[1].trim().toUpperCase() : "";
    if (!value || !targets.has(value)) {
      continue;
    }

    try {
      const normalized = normalizeState(state);
      telemetry.push(normalized);
      setCacheEntry(normalized.icao24, normalized);
    } catch (error) {
      console.warn("Skipped telemetry entry", error);
    }
  }

  return telemetry;
};

export const getAircraftTelemetryByCallsign = async (callsign: string) => {
  const target = normalizeCallsign(callsign);
  const states = await getStatesSnapshot();
  const state = states.find((item) => {
    const value = typeof item[1] === "string" ? item[1].trim().toUpperCase() : "";
    return value === target;
  });

  if (!state) {
    throw new ApiError("Aircraft not found", 404);
  }

  const telemetry = normalizeState(state);
  setCacheEntry(telemetry.icao24, telemetry);
  return telemetry;
};

export const validateCallsigns = async (callsigns: string[]) => {
  const targets = new Set(callsigns.map(normalizeCallsign));
  const states = await getStatesSnapshot();
  const found = new Set<string>();

  for (const state of states) {
    const value = typeof state[1] === "string" ? state[1].trim().toUpperCase() : "";
    if (value && targets.has(value)) {
      found.add(value);
    }
  }

  return callsigns.map((callsign) => {
    const normalized = normalizeCallsign(callsign);
    return {
      callsign,
      status: found.has(normalized) ? "valid" : "no-data"
    } as const;
  });
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
  if (process.env.OPENSKY_ENABLED !== "true") {
    return "disabled";
  }

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
