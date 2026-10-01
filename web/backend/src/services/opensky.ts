import { getCacheEntry, setCacheEntry } from "./cache";
import { createHash } from "crypto";
import { getApiSettings } from "./settings";

export type OpenSkyConfig = {
  baseUrl: string;
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

type OpenSkyState = Array<string | number | boolean | null>;

type OpenSkyResponse = {
  time?: number;
  states?: OpenSkyState[] | null;
};

type TokenResponse = {
  access_token?: string;
  expires_in?: number;
  token_type?: string;
};

class ApiError extends Error {
  status: number;
  retryAfterSec?: number;

  constructor(message: string, status: number, retryAfterSec?: number) {
    super(message);
    this.status = status;
    this.retryAfterSec = retryAfterSec;
  }
}

/** Minimum spacing between any two upstream `/states/all` calls. */
const RATE_LIMIT_MS = 5_000;
/** Global snapshots are reused this long (OpenSky resolution is 5–10 s). */
const SNAPSHOT_TTL_MS = 10_000;
/** icao24 filters per request; each request costs 1 credit regardless. */
const ICAO24_CHUNK_SIZE = 100;
const DEFAULT_AUTH_URL =
  "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token";
/** Placeholder address used for the 1-credit reachability probe. */
const PROBE_ICAO24 = "000000";

let lastFetchAt = 0;
let upstreamQueue: Promise<unknown> = Promise.resolve();
const inFlight = new Map<string, Promise<OpenSkyResponse>>();
let globalSnapshot: { states: OpenSkyState[]; fetchedAt: number } | null = null;
let creditsRemaining: number | null = null;
let tokenCache: { key: string; accessToken: string; expiresAt: number } | null = null;
let tokenInFlight: { key: string; promise: Promise<string> } | null = null;
let warnedAboutBasicAuth = false;

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const getConfig = (): OpenSkyConfig => {
  const settings = getApiSettings();
  if (
    !warnedAboutBasicAuth &&
    settings.username &&
    settings.password &&
    !(settings.clientId && settings.clientSecret)
  ) {
    warnedAboutBasicAuth = true;
    console.warn(
      "OpenSky no longer accepts username/password; configure an API client " +
        "(client ID + secret). Falling back to anonymous access."
    );
  }
  return {
    baseUrl: settings.baseUrl || "https://opensky-network.org/api",
    authUrl: settings.authUrl || undefined,
    clientId: settings.clientId || undefined,
    clientSecret: settings.clientSecret || undefined
  };
};

/** Last `X-Rate-Limit-Remaining` value seen from OpenSky, if any. */
export const getCreditsRemaining = () => creditsRemaining;

/** Identifies a credential set without keeping the raw secret around. */
const tokenKey = (config: OpenSkyConfig) =>
  [
    config.authUrl || DEFAULT_AUTH_URL,
    config.clientId || "",
    createHash("sha256").update(config.clientSecret || "").digest("hex")
  ].join("|");

const fetchAccessToken = async (config: OpenSkyConfig, key: string) => {
  if (!config.clientId || !config.clientSecret) {
    throw new ApiError("OpenSky auth not configured", 401);
  }

  const body = new URLSearchParams({
    grant_type: "client_credentials",
    client_id: config.clientId,
    client_secret: config.clientSecret
  });

  const response = await fetch(config.authUrl || DEFAULT_AUTH_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString()
  });

  if (!response.ok) {
    throw new ApiError("OpenSky auth failed", 502);
  }

  const payload = (await response.json()) as TokenResponse;
  if (!payload.access_token) {
    throw new ApiError("OpenSky auth failed", 502);
  }

  const expiresIn = payload.expires_in ?? 1800;
  tokenCache = {
    key,
    accessToken: payload.access_token,
    expiresAt: Date.now() + (expiresIn - 60) * 1000
  };
  return payload.access_token;
};

const getAccessToken = async (config: OpenSkyConfig) => {
  if (!config.clientId || !config.clientSecret) {
    return null;
  }
  const key = tokenKey(config);
  if (tokenCache && tokenCache.key === key && Date.now() < tokenCache.expiresAt) {
    return tokenCache.accessToken;
  }
  if (!tokenInFlight || tokenInFlight.key !== key) {
    const promise = fetchAccessToken(config, key).finally(() => {
      if (tokenInFlight?.key === key) tokenInFlight = null;
    });
    tokenInFlight = { key, promise };
  }
  return tokenInFlight.promise;
};

const buildStatesUrl = (baseUrl: string, icao24s?: string[]) => {
  const params = new URLSearchParams();
  for (const icao24 of icao24s ?? []) {
    params.append("icao24", icao24);
  }
  const query = params.toString();
  return `${baseUrl}/states/all${query ? `?${query}` : ""}`;
};

const recordCredits = (response: Response) => {
  const raw = response.headers.get("X-Rate-Limit-Remaining");
  const value = raw === null ? NaN : Number(raw);
  if (Number.isFinite(value)) {
    creditsRemaining = value;
  }
};

const fetchStates = async (
  config: OpenSkyConfig,
  icao24s: string[] | undefined,
  allowRetry = true
): Promise<OpenSkyResponse> => {
  const token = await getAccessToken(config);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 5000);

  try {
    const response = await fetch(buildStatesUrl(config.baseUrl, icao24s), {
      method: "GET",
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      signal: controller.signal
    });
    recordCredits(response);

    if (response.status === 401 && token && allowRetry) {
      tokenCache = null;
      return fetchStates(config, icao24s, false);
    }

    if (response.status === 429) {
      const retryAfter = Number(
        response.headers.get("X-Rate-Limit-Retry-After-Seconds")
      );
      creditsRemaining = 0;
      throw new ApiError(
        "OpenSky credits exhausted",
        429,
        Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined
      );
    }

    if (!response.ok) {
      const body = await response.text().catch(() => "");
      console.log("OpenSky response error", {
        status: response.status,
        body: body.slice(0, 200)
      });
      throw new ApiError("OpenSky unavailable", 502);
    }

    return (await response.json()) as OpenSkyResponse;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if ((error as Error).name === "AbortError") {
      throw new ApiError("OpenSky timeout", 504);
    }
    throw new ApiError("OpenSky unavailable", 502);
  } finally {
    clearTimeout(timeout);
  }
};

/**
 * Serializes upstream calls behind the rate limiter and deduplicates
 * identical concurrent queries (keyed by the icao24 filter, or "global").
 */
const fetchStatesQueued = (config: OpenSkyConfig, icao24s?: string[]) => {
  const key = icao24s && icao24s.length > 0 ? [...icao24s].sort().join(",") : "global";
  const existing = inFlight.get(key);
  if (existing) {
    return existing;
  }

  const request = upstreamQueue
    .catch(() => undefined)
    .then(async () => {
      const elapsed = Date.now() - lastFetchAt;
      if (elapsed < RATE_LIMIT_MS) {
        await delay(RATE_LIMIT_MS - elapsed);
      }
      lastFetchAt = Date.now();
      return fetchStates(config, icao24s);
    })
    .finally(() => {
      inFlight.delete(key);
    });

  upstreamQueue = request;
  inFlight.set(key, request);
  return request;
};

const getGlobalStates = async () => {
  if (globalSnapshot && Date.now() - globalSnapshot.fetchedAt < SNAPSHOT_TTL_MS) {
    return globalSnapshot.states;
  }
  const response = await fetchStatesQueued(getConfig());
  const states = response.states ?? [];
  globalSnapshot = { states, fetchedAt: Date.now() };
  return states;
};

const normalizeState = (state: OpenSkyState) => {
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

const tryNormalize = (state: OpenSkyState) => {
  try {
    const telemetry = normalizeState(state);
    setCacheEntry(telemetry.icao24, telemetry);
    return telemetry;
  } catch (_error) {
    return null;
  }
};

const normalizeCallsign = (callsign: string) => callsign.trim().toUpperCase();

const stateCallsign = (state: OpenSkyState) =>
  typeof state[1] === "string" ? state[1].trim().toUpperCase() : "";

export const isValidCallsign = (input: string) =>
  /^[A-Z0-9]{2,8}$/.test(normalizeCallsign(input));

export const isValidIcao24 = (input: string) => /^[a-f0-9]{6}$/i.test(input);

/**
 * Telemetry for several aircraft by callsign. OpenSky has no callsign filter,
 * so this needs a global snapshot (4 credits), reused for SNAPSHOT_TTL_MS.
 */
export const getAircraftTelemetryByCallsigns = async (callsigns: string[]) => {
  const targets = new Set(callsigns.map(normalizeCallsign));
  if (targets.size === 0) {
    return [] as AircraftTelemetry[];
  }

  const states = await getGlobalStates();
  return states
    .filter((state) => targets.has(stateCallsign(state)))
    .map(tryNormalize)
    .filter((entry): entry is AircraftTelemetry => entry !== null);
};

export const getAircraftTelemetryByCallsign = async (callsign: string) => {
  const target = normalizeCallsign(callsign);
  const states = await getGlobalStates();
  const state = states.find((item) => stateCallsign(item) === target);
  if (!state) {
    throw new ApiError("Aircraft not found", 404);
  }
  const telemetry = normalizeState(state);
  setCacheEntry(telemetry.icao24, telemetry);
  return telemetry;
};

export const validateCallsigns = async (callsigns: string[]) => {
  const targets = new Set(callsigns.map(normalizeCallsign));
  const states = await getGlobalStates();
  const found = new Set(
    states.map(stateCallsign).filter((value) => value && targets.has(value))
  );

  return callsigns.map((callsign) => ({
    callsign,
    status: found.has(normalizeCallsign(callsign)) ? "valid" : "no-data"
  }) as const);
};

/**
 * Telemetry for several aircraft by icao24 using OpenSky's icao24 filter
 * (1 credit per request of up to ICAO24_CHUNK_SIZE addresses). Fresh cache
 * entries are served without an upstream call.
 */
export const getAircraftTelemetryByIcao24s = async (icao24s: string[]) => {
  const keys = Array.from(new Set(icao24s.map((value) => value.toLowerCase())));
  const results: AircraftTelemetry[] = [];
  const misses: string[] = [];

  for (const key of keys) {
    const cached = getCacheEntry<AircraftTelemetry>(key);
    if (cached) {
      results.push(cached.value);
    } else {
      misses.push(key);
    }
  }

  const config = getConfig();
  for (let i = 0; i < misses.length; i += ICAO24_CHUNK_SIZE) {
    const chunk = misses.slice(i, i + ICAO24_CHUNK_SIZE);
    const response = await fetchStatesQueued(config, chunk);
    for (const state of response.states ?? []) {
      const telemetry = tryNormalize(state);
      if (telemetry) {
        results.push(telemetry);
      }
    }
  }

  return results;
};

export const getAircraftTelemetry = async (icao24: string) => {
  const key = icao24.toLowerCase();
  const cached = getCacheEntry<AircraftTelemetry>(key);
  if (cached) {
    return cached.value;
  }

  if (Date.now() - lastFetchAt < RATE_LIMIT_MS) {
    const stale = getCacheEntry<AircraftTelemetry>(key, { allowStale: true });
    if (stale) {
      return stale.value;
    }
  }

  const [telemetry] = await getAircraftTelemetryByIcao24s([key]);
  if (!telemetry) {
    throw new ApiError("Aircraft not found", 404);
  }
  return telemetry;
};

const PING_TTL_MS = 60_000;
let pingCache: { result: string; at: number } | null = null;
let pingInFlight: Promise<string> | null = null;

/**
 * Reachability probe: a 1-credit icao24 query instead of a global fetch.
 * Cached for 60 s and deduplicated, so /api/health spends at most 1 credit/min.
 */
export const pingOpenSky = async () => {
  if (process.env.OPENSKY_ENABLED !== "true") {
    return "disabled";
  }
  if (pingCache && Date.now() - pingCache.at < PING_TTL_MS) {
    return pingCache.result;
  }
  if (!pingInFlight) {
    pingInFlight = (async () => {
      let result: string;
      try {
        await fetchStatesQueued(getConfig(), [PROBE_ICAO24]);
        result = "reachable";
      } catch (error) {
        console.error("OpenSky ping error", error);
        result = "unreachable";
      }
      pingCache = { result, at: Date.now() };
      return result;
    })().finally(() => {
      pingInFlight = null;
    });
  }
  return pingInFlight;
};

export { ApiError };
