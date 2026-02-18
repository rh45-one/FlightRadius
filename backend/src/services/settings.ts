type ApiSettings = {
  baseUrl: string;
  username?: string;
  password?: string;
};

let apiSettings: ApiSettings = {
  baseUrl: process.env.OPENSKY_BASE_URL || "https://opensky-network.org/api",
  username: process.env.OPENSKY_USERNAME || "",
  password: process.env.OPENSKY_PASSWORD || ""
};

export const getApiSettings = () => ({ ...apiSettings });

export const setApiSettings = (input: Partial<ApiSettings>) => {
  apiSettings = {
    ...apiSettings,
    ...input
  };

  return getApiSettings();
};
