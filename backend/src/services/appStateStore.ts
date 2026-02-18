import { promises as fs } from "fs";
import path from "path";

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
    gpsPollingIntervalSec: 20,
    manualLatitude: "",
    manualLongitude: "",
    gpsAccuracyMode: "balanced",
    apiBaseUrl: "/api",
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

const filePath =
  process.env.APP_STATE_PATH || path.join(__dirname, "../../data/app-state.json");

let cachedState: AppState | null = null;
let writePromise: Promise<void> | null = null;

const ensureDir = async () => {
  const dir = path.dirname(filePath);
  await fs.mkdir(dir, { recursive: true });
};

const readStateFile = async () => {
  try {
    await ensureDir();
    const raw = await fs.readFile(filePath, "utf8");
    return JSON.parse(raw) as AppState;
  } catch (_error) {
    return null;
  }
};

const writeStateFile = async (state: AppState) => {
  await ensureDir();
  const payload = JSON.stringify(state, null, 2);
  await fs.writeFile(filePath, payload, "utf8");
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

export const getAppState = async () => {
  if (cachedState) {
    return cachedState;
  }

  const fileState = await readStateFile();
  cachedState = mergeState(fileState || {}, defaultState);
  return cachedState;
};

export const saveAppState = async (incoming: Partial<AppState>) => {
  const current = await getAppState();
  cachedState = mergeState(incoming, current);

  if (!writePromise) {
    writePromise = (async () => {
      await writeStateFile(cachedState as AppState);
      writePromise = null;
    })();
  }

  await writePromise;
  return cachedState;
};
