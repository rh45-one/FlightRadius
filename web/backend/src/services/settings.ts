type ApiSettings = {
  baseUrl: string;
  username?: string;
  password?: string;
  authUrl: string;
  clientId?: string;
  clientSecret?: string;
};

export const DEFAULT_BASE_URL = "https://opensky-network.org/api";
export const DEFAULT_AUTH_URL =
  "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token";
const DEFAULT_ALLOWED_HOSTS = ["opensky-network.org", "auth.opensky-network.org"];

/** Hostnames the backend may send OpenSky traffic (and credentials) to. */
export const getAllowedHosts = (): string[] => {
  const raw = process.env.OPENSKY_ALLOWED_HOSTS;
  if (raw === undefined || raw.trim() === "") return DEFAULT_ALLOWED_HOSTS;
  return raw
    .split(",")
    .map((host) => host.trim().toLowerCase())
    .filter((host) => host.length > 0);
};

/** https only, no userinfo, exact hostname match against the allowlist. */
export const isAllowedApiUrl = (value: unknown): boolean => {
  if (typeof value !== "string") return false;
  let url: URL;
  try {
    url = new URL(value.trim());
  } catch (_error) {
    return false;
  }
  if (url.protocol !== "https:") return false;
  if (url.username || url.password) return false;
  return getAllowedHosts().includes(url.hostname.toLowerCase());
};

export class UrlNotAllowedError extends Error {
  status = 400;
  constructor() {
    super("URL not allowed");
  }
}

/** Removes the query/fragment so a URL can be logged safely. */
export const redactUrl = (value: string) => {
  try {
    const url = new URL(value);
    return `${url.protocol}//${url.host}${url.pathname}`;
  } catch (_error) {
    return "(unparseable)";
  }
};

/** Empty means "use the default"; anything else must pass [isAllowedApiUrl]. */
export const assertAllowedUrl = (value: unknown) => {
  if (value === undefined || value === null) return;
  if (typeof value !== "string") throw new UrlNotAllowedError();
  const trimmed = value.trim();
  if (trimmed !== "" && !isAllowedApiUrl(trimmed)) throw new UrlNotAllowedError();
};

const envUrlOrDefault = (name: string, fallback: string) => {
  const value = process.env[name];
  if (!value) return fallback;
  if (isAllowedApiUrl(value)) return value.trim();
  console.warn(`${name} ignored: host not allowed (${redactUrl(value)}); using the default`);
  return fallback;
};

let apiSettings: ApiSettings = {
  baseUrl: envUrlOrDefault("OPENSKY_BASE_URL", DEFAULT_BASE_URL),
  authUrl: envUrlOrDefault("OPENSKY_AUTH_URL", DEFAULT_AUTH_URL),
  username: process.env.OPENSKY_USERNAME || "",
  password: process.env.OPENSKY_PASSWORD || "",
  clientId: process.env.OPENSKY_CLIENT_ID || "",
  clientSecret: process.env.OPENSKY_CLIENT_SECRET || ""
};

export const getApiSettings = () => ({ ...apiSettings });

export const setApiSettings = (input: Partial<ApiSettings>) => {
  const update: Partial<ApiSettings> = {};

  const applyIfString = <K extends keyof ApiSettings>(key: K) => {
    const value = input[key];
    if (typeof value === "string") {
      update[key] = value.trim() as ApiSettings[K];
    }
  };

  // Validate everything first so a rejected URL leaves all settings untouched.
  assertAllowedUrl(input.baseUrl);
  assertAllowedUrl(input.authUrl);

  const applyIfHttpUrl = (key: "baseUrl" | "authUrl") => {
    const value = input[key];
    if (typeof value === "string" && value.trim() !== "") {
      update[key] = value.trim() as ApiSettings[typeof key];
    }
  };

  applyIfHttpUrl("baseUrl");
  applyIfHttpUrl("authUrl");
  applyIfString("username");
  applyIfString("password");
  applyIfString("clientId");
  applyIfString("clientSecret");

  apiSettings = {
    ...apiSettings,
    ...update
  };

  return getApiSettings();
};
