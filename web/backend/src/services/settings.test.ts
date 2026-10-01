import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { startTestServer } from "../testServer";

const load = async () => {
  vi.resetModules();
  return import("./settings");
};

const OK_BASE = "https://opensky-network.org/api";
const OK_AUTH =
  "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token";

beforeEach(() => {
  delete process.env.OPENSKY_ALLOWED_HOSTS;
  delete process.env.OPENSKY_BASE_URL;
  delete process.env.OPENSKY_AUTH_URL;
});
afterEach(() => {
  vi.restoreAllMocks();
});

describe("isAllowedApiUrl", () => {
  it("accepts the default hosts over https", async () => {
    const { isAllowedApiUrl } = await load();
    expect(isAllowedApiUrl(OK_BASE)).toBe(true);
    expect(isAllowedApiUrl(OK_AUTH)).toBe(true);
  });

  it("rejects other hosts, http, userinfo and subdomain tricks", async () => {
    const { isAllowedApiUrl } = await load();
    expect(isAllowedApiUrl("https://evil.com/api")).toBe(false);
    expect(isAllowedApiUrl("http://opensky-network.org/api")).toBe(false);
    expect(isAllowedApiUrl("https://opensky-network.org@evil.com/api")).toBe(false);
    expect(isAllowedApiUrl("https://user:pw@opensky-network.org/api")).toBe(false);
    expect(isAllowedApiUrl("https://opensky-network.org.evil.com/api")).toBe(false);
    expect(isAllowedApiUrl("https://evilopensky-network.org/api")).toBe(false);
    expect(isAllowedApiUrl("not a url")).toBe(false);
  });

  it("OPENSKY_ALLOWED_HOSTS replaces the default list", async () => {
    process.env.OPENSKY_ALLOWED_HOSTS = "proxy.example.org, other.example.org";
    const { isAllowedApiUrl } = await load();
    expect(isAllowedApiUrl("https://proxy.example.org/api")).toBe(true);
    expect(isAllowedApiUrl(OK_BASE)).toBe(false);
  });
});

describe("setApiSettings", () => {
  it("applies an allowed URL and ignores empty ones", async () => {
    const { setApiSettings, getApiSettings } = await load();
    setApiSettings({ baseUrl: "https://opensky-network.org/api/v2" });
    expect(getApiSettings().baseUrl).toBe("https://opensky-network.org/api/v2");
    setApiSettings({ baseUrl: "" });
    expect(getApiSettings().baseUrl).toBe("https://opensky-network.org/api/v2");
  });

  it("throws and changes nothing for a disallowed URL", async () => {
    const { setApiSettings, getApiSettings, UrlNotAllowedError } = await load();
    expect(() =>
      setApiSettings({ baseUrl: OK_BASE, authUrl: "https://evil.com/token", clientId: "x" })
    ).toThrow(UrlNotAllowedError);
    expect(getApiSettings().authUrl).toBe(OK_AUTH);
    expect(getApiSettings().clientId).toBe("");
  });

  it("falls back to the default when the env URL is not allowed", async () => {
    process.env.OPENSKY_BASE_URL = "https://evil.com/api?secret=1";
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const { getApiSettings } = await load();
    expect(getApiSettings().baseUrl).toBe(OK_BASE);
    expect(warn.mock.calls.flat().join(" ")).not.toContain("secret=1");
  });
});

describe("settings routes", () => {
  it("POST /api/settings/api rejects a disallowed URL with 400 and changes nothing", async () => {
    vi.resetModules();
    const settings = await import("./settings");
    const router = (await import("../routes/settings")).default;
    const server = await startTestServer([["/api/settings", router]]);
    try {
      const bad = await server.post("/api/settings/api", {
        authUrl: "https://opensky-network.org.evil.com/token",
        clientId: "id",
        clientSecret: "secret"
      });
      expect(bad.status).toBe(400);
      expect(bad.body).toEqual({ error: "URL not allowed", status: 400 });
      expect(settings.getApiSettings().clientSecret).toBe("");

      const ok = await server.post("/api/settings/api", { authUrl: OK_AUTH, clientId: "id" });
      expect(ok.status).toBe(200);
      expect(settings.getApiSettings().clientId).toBe("id");

      const empty = await server.post("/api/settings/api", { baseUrl: "" });
      expect(empty.status).toBe(200);
    } finally {
      await server.close();
    }
  });

  it("rejects non-string URL values with 400 on both endpoints (no 500)", async () => {
    vi.resetModules();
    const settingsRouter = (await import("../routes/settings")).default;
    const appStateRouter = (await import("../routes/appState")).default;
    const server = await startTestServer([
      ["/api/settings", settingsRouter],
      ["/api", appStateRouter]
    ]);
    try {
      for (const bad of [123, { host: "evil.com" }, ["https://opensky-network.org"], true]) {
        const a = await server.post("/api/app/state", { settings: { apiBaseUrl: bad } });
        expect(a.status).toBe(400);
        expect(a.body).toEqual({ error: "URL not allowed", status: 400 });
        const b = await server.post("/api/app/state", { settings: { apiAuthUrl: bad } });
        expect(b.status).toBe(400);
        const c = await server.post("/api/settings/api", { baseUrl: bad });
        expect(c.status).toBe(400);
      }
      // null is treated like "not provided".
      const n = await server.post("/api/app/state", { settings: { apiBaseUrl: null } });
      expect(n.status).toBe(200);
    } finally {
      await server.close();
    }
  });

  it("isAllowedApiUrl is false for non-strings", async () => {
    const { isAllowedApiUrl } = await load();
    for (const v of [123, null, undefined, {}, [], true]) expect(isAllowedApiUrl(v)).toBe(false);
  });
});
