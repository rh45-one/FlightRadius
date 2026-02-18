type ApiSettings = {
  baseUrl: string;
  username?: string;
  password?: string;
  authUrl: string;
  clientId?: string;
  clientSecret?: string;
};

let apiSettings: ApiSettings = {
  baseUrl: process.env.OPENSKY_BASE_URL || "https://opensky-network.org/api",
  authUrl:
    process.env.OPENSKY_AUTH_URL ||
    "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token",
  username: process.env.OPENSKY_USERNAME || "",
  password: process.env.OPENSKY_PASSWORD || "",
  clientId: process.env.OPENSKY_CLIENT_ID || "",
  clientSecret: process.env.OPENSKY_CLIENT_SECRET || ""
};

export const getApiSettings = () => ({ ...apiSettings });

export const setApiSettings = (input: Partial<ApiSettings>) => {
  const update: Partial<ApiSettings> = {};

  const applyIfNonEmpty = <K extends keyof ApiSettings>(key: K) => {
    const value = input[key];
    if (typeof value === "string" && value.trim() !== "") {
      update[key] = value as ApiSettings[K];
    }
  };

  applyIfNonEmpty("baseUrl");
  applyIfNonEmpty("authUrl");
  applyIfNonEmpty("username");
  applyIfNonEmpty("password");
  applyIfNonEmpty("clientId");
  applyIfNonEmpty("clientSecret");

  apiSettings = {
    ...apiSettings,
    ...update
  };

  return getApiSettings();
};
