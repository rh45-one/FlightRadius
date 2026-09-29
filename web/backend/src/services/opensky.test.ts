import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type FetchCall = { url: string; init?: RequestInit };

const statesBody = (states: unknown[][]) => JSON.stringify({ time: 1, states });

const state = (icao24: string, callsign: string, lastContact = 1_700_000_000) => [
  icao24, `${callsign}  `, "Spain", lastContact, lastContact, -3.7, 40.4, 10_000,
  false, 230, 90, 0, null, 10_100, null, false, 0
];

const response = (body: string, status = 200, headers: Record<string, string> = {}) =>
  new Response(body, { status, headers });

let calls: FetchCall[];
let queue: Response[];

/** Fresh module graph per test: opensky.ts keeps rate-limit/token state. */
const loadModules = async () => {
  vi.resetModules();
  const opensky = await import("./opensky");
  const settings = await import("./settings");
  return { ...opensky, ...settings };
};

beforeEach(() => {
  calls = [];
  queue = [];
  vi.stubGlobal("fetch", vi.fn(async (url: string, init?: RequestInit) => {
    calls.push({ url: String(url), init });
    const next = queue.shift();
    if (!next) throw new Error(`unexpected fetch ${url}`);
    return next;
  }));
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("opensky service", () => {
  it("batches icao24 lookups into one filtered request", async () => {
    const { getAircraftTelemetryByIcao24s } = await loadModules();
    queue.push(response(statesBody([state("abc123", "IBE1"), state("def456", "BAW2")])));

    const result = await getAircraftTelemetryByIcao24s(["ABC123", "def456"]);

    expect(calls).toHaveLength(1);
    const url = new URL(calls[0].url);
    expect(url.pathname).toMatch(/\/states\/all$/);
    expect(url.searchParams.getAll("icao24")).toEqual(["abc123", "def456"]);
    expect(url.searchParams.has("lamin")).toBe(false);
    expect(result.map((t) => t.callsign)).toEqual(["IBE1", "BAW2"]);
  });

  it("maps HTTP 429 to an ApiError carrying the retry-after hint", async () => {
    const { getAircraftTelemetryByIcao24s, ApiError, getCreditsRemaining } = await loadModules();
    queue.push(response("", 429, { "X-Rate-Limit-Retry-After-Seconds": "120" }));

    const error = await getAircraftTelemetryByIcao24s(["abc123"]).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(429);
    expect(error.retryAfterSec).toBe(120);
    expect(getCreditsRemaining()).toBe(0);
  });

  it("records the remaining-credits header", async () => {
    const { getAircraftTelemetryByIcao24s, getCreditsRemaining } = await loadModules();
    queue.push(response(statesBody([]), 200, { "X-Rate-Limit-Remaining": "3990" }));

    await getAircraftTelemetryByIcao24s(["abc123"]);

    expect(getCreditsRemaining()).toBe(3990);
  });

  it("reuses one global snapshot for back-to-back callsign lookups", async () => {
    const { getAircraftTelemetryByCallsigns, validateCallsigns } = await loadModules();
    queue.push(response(statesBody([state("abc123", "IBE1")])));

    const telemetry = await getAircraftTelemetryByCallsigns(["ibe1"]);
    const validation = await validateCallsigns(["IBE1", "NOPE1"]);

    expect(calls).toHaveLength(1);
    expect(new URL(calls[0].url).search).toBe("");
    expect(telemetry[0].icao24).toBe("abc123");
    expect(validation.map((v) => v.status)).toEqual(["valid", "no-data"]);
  });

  it("never sends basic credentials (OpenSky only accepts OAuth2)", async () => {
    const { getAircraftTelemetryByIcao24s, setApiSettings } = await loadModules();
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    setApiSettings({ username: "user", password: "pass", clientId: "", clientSecret: "" });
    queue.push(response(statesBody([])));

    await getAircraftTelemetryByIcao24s(["abc123"]);

    expect(calls[0].init?.headers).toBeUndefined();
    expect(warn).toHaveBeenCalledOnce();
    warn.mockRestore();
  });

  it("uses an OAuth2 bearer token when a client is configured", async () => {
    const { getAircraftTelemetryByIcao24s, setApiSettings } = await loadModules();
    setApiSettings({ clientId: "id", clientSecret: "secret" });
    queue.push(response(JSON.stringify({ access_token: "tok", expires_in: 1800 })));
    queue.push(response(statesBody([])));

    await getAircraftTelemetryByIcao24s(["abc123"]);

    expect(calls[0].init?.method).toBe("POST");
    expect(String(calls[0].init?.body)).toContain("grant_type=client_credentials");
    expect(calls[1].init?.headers).toEqual({ Authorization: "Bearer tok" });
  });

  it("serves fresh cache entries without another upstream call", async () => {
    const { getAircraftTelemetryByIcao24s, getAircraftTelemetry } = await loadModules();
    queue.push(response(statesBody([state("abc123", "IBE1")])));

    await getAircraftTelemetryByIcao24s(["abc123"]);
    const cached = await getAircraftTelemetry("ABC123");

    expect(calls).toHaveLength(1);
    expect(cached.callsign).toBe("IBE1");
  });
});
