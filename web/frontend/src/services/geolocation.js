const DEFAULT_OPTIONS = {
  enableHighAccuracy: true,
  timeout: 10000,
  maximumAge: 0
};

let lastKnownLocation = null;
let watchId = null;

const buildOptions = (options) => ({
  enableHighAccuracy:
    typeof options?.highAccuracy === "boolean"
      ? options.highAccuracy
      : DEFAULT_OPTIONS.enableHighAccuracy,
  timeout:
    typeof options?.timeoutMs === "number"
      ? options.timeoutMs
      : DEFAULT_OPTIONS.timeout,
  maximumAge:
    typeof options?.maximumAgeMs === "number"
      ? options.maximumAgeMs
      : DEFAULT_OPTIONS.maximumAge
});

const toPositionData = (position) => ({
  latitude: position.coords.latitude,
  longitude: position.coords.longitude,
  accuracy_m: position.coords.accuracy,
  timestamp: position.timestamp,
  source: "gps"
});

const mapError = (error) => {
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
      message: "Location unavailable",
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

export const requestLocation = (options) => {
  if (!isGeolocationSupported()) {
    return Promise.reject({
      code: "unsupported",
      message: "Geolocation is not supported",
      permissionStatus: "unsupported"
    });
  }

  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const data = toPositionData(position);
        lastKnownLocation = data;
        resolve(data);
      },
      (error) => reject(mapError(error)),
      buildOptions(options)
    );
  });
};

export const watchLocation = (options, callbacks) => {
  if (!isGeolocationSupported()) {
    callbacks.onError({
      code: "unsupported",
      message: "Geolocation is not supported",
      permissionStatus: "unsupported"
    });
    return -1;
  }

  watchId = navigator.geolocation.watchPosition(
    (position) => {
      const data = toPositionData(position);
      lastKnownLocation = data;
      callbacks.onUpdate(data);
    },
    (error) => callbacks.onError(mapError(error)),
    buildOptions(options)
  );

  return watchId;
};

export const stopWatching = () => {
  if (watchId !== null && isGeolocationSupported()) {
    navigator.geolocation.clearWatch(watchId);
  }
  watchId = null;
};

export const getLastKnownLocation = () => lastKnownLocation;
