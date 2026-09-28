export type UserLocation = {
  latitude: number;
  longitude: number;
  accuracy_m: number;
  timestamp: number;
  source: "gps" | "manual";
};

type LocationState = {
  lastLocation: UserLocation | null;
};

const state: LocationState = {
  lastLocation: null
};

export const setLastLocation = (location: UserLocation) => {
  state.lastLocation = location;
};

export const getLastLocation = () => state.lastLocation;

export const getLocationIngestStatus = () =>
  state.lastLocation ? "ok" : "empty";

export const getLastLocationTimestamp = () =>
  state.lastLocation?.timestamp ?? null;
