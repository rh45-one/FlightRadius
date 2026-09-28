export type PositionSource = "gps" | "manual";

export type PositionData = {
  latitude: number;
  longitude: number;
  accuracy_m: number;
  timestamp: number;
  source: PositionSource;
};

export type PermissionStatus =
  | "unknown"
  | "prompt"
  | "granted"
  | "denied"
  | "unsupported"
  | "manual";

export type GeolocationError = {
  code: number | "unsupported";
  message: string;
  permissionStatus: PermissionStatus;
};

export const isGeolocationSupported: () => boolean;
export const requestLocation: (options?: {
  highAccuracy?: boolean;
  timeoutMs?: number;
  maximumAgeMs?: number;
}) => Promise<PositionData>;
export const watchLocation: (
  options: {
    highAccuracy?: boolean;
    timeoutMs?: number;
    maximumAgeMs?: number;
  },
  callbacks: {
    onUpdate: (position: PositionData) => void;
    onError: (error: GeolocationError) => void;
  }
) => number;
export const stopWatching: () => void;
export const getLastKnownLocation: () => PositionData | null;
