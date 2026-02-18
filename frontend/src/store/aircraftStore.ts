import { create } from "zustand";

export type Aircraft = {
  id: string;
  icao24?: string;
  callsign?: string;
  notes?: string;
  createdAt: string;
};

export type TrackingSettings = {
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

type AircraftState = {
  aircraft: Aircraft[];
  settings: TrackingSettings;
  ui: UiPreferences;
  addAircraft: (input: Omit<Aircraft, "id" | "createdAt">) => void;
  removeAircraft: (id: string) => void;
  updateSettings: (partial: Partial<TrackingSettings>) => void;
  updateUi: (partial: Partial<UiPreferences>) => void;
  setAircraft: (aircraft: Aircraft[]) => void;
  setSettings: (settings: TrackingSettings) => void;
  setUi: (ui: UiPreferences) => void;
};

const createId = () => {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

export const useAircraftStore = create<AircraftState>()((set) => ({
  aircraft: [],
  settings: {
    refreshIntervalSec: 12,
    distanceUnit: "km",
    maxTrackedWarning: 24,
    locationMode: "gps",
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
  addAircraft: (input) =>
    set((state) => ({
      aircraft: [
        {
          ...input,
          id: createId(),
          createdAt: new Date().toISOString()
        },
        ...state.aircraft
      ]
    })),
  removeAircraft: (id) =>
    set((state) => ({
      aircraft: state.aircraft.filter((item) => item.id !== id)
    })),
  updateSettings: (partial) =>
    set((state) => ({
      settings: { ...state.settings, ...partial }
    })),
  updateUi: (partial) =>
    set((state) => ({
      ui: { ...state.ui, ...partial }
    })),
  setAircraft: (aircraft) => set(() => ({ aircraft })),
  setSettings: (settings) => set(() => ({ settings })),
  setUi: (ui) => set(() => ({ ui }))
}));
