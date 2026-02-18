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

type PollingOptions = {
  intervalMs: number;
  highAccuracy: boolean;
  timeoutMs: number;
};

type PollingCallbacks = {
  onUpdate: (position: PositionData) => void;
  onError: (error: GeolocationError) => void;
};

let mockLocation: { latitude: number; longitude: number } | null = null;

export const setMockLocation = (latitude: number, longitude: number) => {
  mockLocation = { latitude, longitude };
};

export const clearMockLocation = () => {
  mockLocation = null;
};

const toPositionData = (position: GeolocationPosition): PositionData => ({
  latitude: position.coords.latitude,
  longitude: position.coords.longitude,
  accuracy_m: position.coords.accuracy,
  timestamp: position.timestamp,
  source: "gps"
});

const mapError = (error: GeolocationPositionError): GeolocationError => {
  if (error.code === error.PERMISSION_DENIED) {
    return {
      code: error.code,
      message: "Location permission denied",
      permissionStatus: "denied"
    };
  }

  if (error.code === error.POSITION_UNAVAILABLE) {
    return {
      code: error.code,
      message: "Position unavailable",
      permissionStatus: "prompt"
    };
  }

  if (error.code === error.TIMEOUT) {
    return {
      code: error.code,
      message: "Location request timed out",
      permissionStatus: "prompt"
    };
  }

  return {
    code: error.code,
    message: "Location error",
    permissionStatus: "prompt"
  };
};

export const isGeolocationSupported = () =>
  typeof navigator !== "undefined" && "geolocation" in navigator;

export const requestCurrentPosition = (options: {
  highAccuracy: boolean;
  timeoutMs: number;
}) => {
  if (mockLocation) {
    return Promise.resolve({
      latitude: mockLocation.latitude,
      longitude: mockLocation.longitude,
      accuracy_m: 5,
      timestamp: Date.now(),
      source: "gps"
    });
  }

  if (!isGeolocationSupported()) {
    return Promise.reject<GeolocationError>({
      code: "unsupported",
      message: "Geolocation is not supported",
      permissionStatus: "unsupported"
    });
  }

  return new Promise<PositionData>((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => resolve(toPositionData(position)),
      (error) => reject(mapError(error)),
      {
        enableHighAccuracy: options.highAccuracy,
        timeout: options.timeoutMs
      }
    );
  });
};

export const startPolling = (
  options: PollingOptions,
  callbacks: PollingCallbacks
) => {
  if (!isGeolocationSupported()) {
    callbacks.onError({
      code: "unsupported",
      message: "Geolocation is not supported",
      permissionStatus: "unsupported"
    });
    return () => undefined;
  }

  if (mockLocation) {
    callbacks.onUpdate({
      latitude: mockLocation.latitude,
      longitude: mockLocation.longitude,
      accuracy_m: 5,
      timestamp: Date.now(),
      source: "gps"
    });

    const intervalId = window.setInterval(() => {
      callbacks.onUpdate({
        latitude: mockLocation.latitude,
        longitude: mockLocation.longitude,
        accuracy_m: 5,
        timestamp: Date.now(),
        source: "gps"
      });
    }, options.intervalMs);

    return () => window.clearInterval(intervalId);
  }

  const watchId = navigator.geolocation.watchPosition(
    (position) => callbacks.onUpdate(toPositionData(position)),
    (error) => callbacks.onError(mapError(error)),
    {
      enableHighAccuracy: options.highAccuracy,
      timeout: options.timeoutMs
    }
  );

  const intervalId = window.setInterval(() => {
    navigator.geolocation.getCurrentPosition(
      (position) => callbacks.onUpdate(toPositionData(position)),
      (error) => callbacks.onError(mapError(error)),
      {
        enableHighAccuracy: options.highAccuracy,
        timeout: options.timeoutMs
      }
    );
  }, options.intervalMs);

  return () => {
    navigator.geolocation.clearWatch(watchId);
    window.clearInterval(intervalId);
  };
};

declare global {
  interface Window {
    __setMockLocation?: (latitude: number, longitude: number) => void;
  }
}
