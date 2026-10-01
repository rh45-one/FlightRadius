import { promises as fs } from "fs";
import path from "path";
import { isAllowedApiUrl, redactUrl } from "./settings";

export type StoredAircraft = {
  id: string;
  icao24?: string;
  callsign?: string;
  notes?: string;
  createdAt: string;
};

export type StoredSettings = {
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

export type UiPreferences = {
  theme: "dark" | "light";
  cardDensity: "comfortable" | "compact";
  timeFormat: "24h" | "12h";
};

export type FleetGroup = {
  id: string;
  name: string;
  description?: string;
  color: string;
  icon: string;
};

export type FleetAircraft = {
  id: string;
  callsign: string;
  groupId?: string;
  createdAt: string;
};

export type AppState = {
  settings: StoredSettings;
  ui: UiPreferences;
  aircraft: StoredAircraft[];
  fleet: {
    groups: FleetGroup[];
    fleetAircraft: FleetAircraft[];
  };
};

const defaultState: AppState = {
  settings: {
    refreshIntervalSec: 12,
    distanceUnit: "km",
    maxTrackedWarning: 24,
    locationMode: "gps",
    autoRefreshOnMovement: true,
    autoEnableLocationOnDashboard: false,
    distanceUpdateIntervalSec: 15,
    gpsPollingIntervalSec: 20,
    manualLatitude: "",
    manualLongitude: "",
    gpsAccuracyMode: "balanced",
    apiBaseUrl: "https://opensky-network.org/api",
    apiAuthUrl:
      "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token",
    apiUsername: "",
    apiPassword: "",
    apiClientId: "",
    apiClientSecret: ""
  },
  ui: {
    theme: "dark",
    cardDensity: "comfortable",
    timeFormat: "24h"
  },
  aircraft: [],
  fleet: {
    groups: [],
    fleetAircraft: []
  }
};

export const SECRET_MASK = "********";

const SECRET_FIELDS = [
  "apiPassword",
  "apiClientSecret",
  "apiUsername",
  "apiClientId"
] as const;

const filePath =
  process.env.APP_STATE_PATH || path.join(__dirname, "../../data/app-state.json");

let cachedState: AppState | null = null;
let writeChain: Promise<void> = Promise.resolve();

const ensureDir = async () => {
  const dir = path.dirname(filePath);
  await fs.mkdir(dir, { recursive: true });
};

const readStateFile = async () => {
  await ensureDir();
  let raw: string;
  try {
    raw = await fs.readFile(filePath, "utf8");
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return null;
    }
    // Unreadable for another reason: don't pretend there is no data (a later
    // save would overwrite it).
    throw error;
  }
  try {
    return JSON.parse(raw) as AppState;
  } catch (_error) {
    const backup = `${filePath}.corrupt-${Date.now()}`;
    try {
      await fs.rename(filePath, backup);
      console.warn(`App state file is corrupt; moved to ${backup} and starting with defaults`);
    } catch (renameError) {
      console.warn("App state file is corrupt and could not be moved aside", renameError);
    }
    return null;
  }
};

/** Atomic: write a temp file, then rename over the real one. */
const writeStateFile = async (state: AppState) => {
  await ensureDir();
  const payload = JSON.stringify(state, null, 2);
  const tmp = `${filePath}.tmp`;
  await fs.writeFile(tmp, payload, "utf8");
  await fs.rename(tmp, filePath);
};

const mergeState = (incoming: Partial<AppState>, base: AppState) => ({
  ...base,
  ...incoming,
  settings: {
    ...base.settings,
    ...(incoming.settings || {})
  },
  ui: {
    ...base.ui,
    ...(incoming.ui || {})
  },
  fleet: {
    ...base.fleet,
    ...(incoming.fleet || {})
  }
});

const normalizeSettings = (settings: StoredSettings, base: StoredSettings) => {
  const normalizeUrl = (value: unknown, fallback: string) => {
    if (typeof value !== "string" || !value) return fallback;
    if (isAllowedApiUrl(value)) return value;
    console.warn(`Stored API URL not allowed (${redactUrl(value)}); using the default`);
    return fallback;
  };

  return {
    ...settings,
    apiBaseUrl: normalizeUrl(settings.apiBaseUrl, base.apiBaseUrl),
    apiAuthUrl: normalizeUrl(settings.apiAuthUrl, base.apiAuthUrl)
  };
};

export const getAppState = async () => {
  if (cachedState) {
    return cachedState;
  }

  const fileState = await readStateFile();
  const merged = mergeState(fileState || {}, defaultState);
  cachedState = {
    ...merged,
    settings: normalizeSettings(merged.settings, defaultState.settings)
  };
  return cachedState;
};

export const maskSecrets = (state: AppState): AppState => ({
  ...state,
  settings: {
    ...state.settings,
    ...Object.fromEntries(
      SECRET_FIELDS.filter((field) => state.settings[field]).map((field) => [
        field,
        SECRET_MASK
      ])
    )
  }
});

export const saveAppState = async (incoming: Partial<AppState>) => {
  const current = await getAppState();
  const sanitized = { ...incoming };

  if (incoming.settings) {
    const settings = { ...incoming.settings };
    for (const field of SECRET_FIELDS) {
      if (settings[field] === SECRET_MASK) {
        settings[field] = current.settings[field];
      }
    }
    sanitized.settings = settings;
  }

  const merged = mergeState(sanitized, current);
  const next: AppState = {
    ...merged,
    settings: normalizeSettings(merged.settings, defaultState.settings)
  };
  cachedState = next;

  // Serialize writes: each one runs after the previous finishes and writes
  // whatever cachedState is by then, so the newest state always reaches disk.
  writeChain = writeChain
    .catch(() => undefined)
    .then(() => writeStateFile(cachedState as AppState));
  await writeChain;
  return next;
};
