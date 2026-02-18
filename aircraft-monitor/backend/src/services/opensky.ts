export type OpenSkyConfig = {
  baseUrl: string;
  username?: string;
  password?: string;
};

// TODO: Implement OpenSky API client logic for future integration.
export const createOpenSkyClient = (_config: OpenSkyConfig) => {
  return {
    // TODO: fetchAircraftState(icao24: string)
  };
};
