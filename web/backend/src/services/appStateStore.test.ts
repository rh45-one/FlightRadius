import { promises as fs } from "fs";
import os from "os";
import path from "path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { startTestServer } from "../testServer";

let dir: string;
let file: string;

beforeEach(async () => {
  dir = await fs.mkdtemp(path.join(os.tmpdir(), "fr-state-"));
  file = path.join(dir, "app-state.json");
  process.env.APP_STATE_PATH = file;
  delete process.env.OPENSKY_ALLOWED_HOSTS;
  vi.resetModules();
});

afterEach(async () => {
  delete process.env.APP_STATE_PATH;
  vi.restoreAllMocks();
  await fs.rm(dir, { recursive: true, force: true });
});

const load = () => import("./appStateStore");

describe("saveAppState", () => {
  it("two rapid saves leave the second state on disk", async () => {
    const { saveAppState } = await load();
    await Promise.all([
      saveAppState({ ui: { theme: "light", cardDensity: "compact", timeFormat: "12h" } }),
      saveAppState({ ui: { theme: "dark", cardDensity: "comfortable", timeFormat: "24h" } })
    ]);
    const onDisk = JSON.parse(await fs.readFile(file, "utf8"));
    expect(onDisk.ui.theme).toBe("dark");
    expect(onDisk.ui.timeFormat).toBe("24h");
  });

  it("writes atomically (no temp file left behind)", async () => {
    const { saveAppState } = await load();
    await saveAppState({ aircraft: [{ id: "1", callsign: "IBE1", createdAt: "x" }] });
    expect((await fs.readdir(dir)).sort()).toEqual(["app-state.json"]);
  });

  it("an unwritable target does not poison later saves", async () => {
    const { saveAppState } = await load();
    const real = fs.rename.bind(fs);
    const spy = vi.spyOn(fs, "rename").mockRejectedValueOnce(new Error("disk full"));
    await expect(saveAppState({ aircraft: [] })).rejects.toThrow("disk full");
    spy.mockImplementation(real);
    await saveAppState({ aircraft: [{ id: "2", callsign: "X", createdAt: "y" }] });
    expect(JSON.parse(await fs.readFile(file, "utf8")).aircraft[0].id).toBe("2");
  });
});

describe("corrupt state file", () => {
  it("is moved aside and defaults are used; the next save does not lose the evidence", async () => {
    await fs.writeFile(file, "{ this is not json", "utf8");
    vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const { getAppState, saveAppState } = await load();
    const state = await getAppState();
    expect(state.settings.refreshIntervalSec).toBe(12);
    const files = await fs.readdir(dir);
    const corrupt = files.find((f) => f.startsWith("app-state.json.corrupt-"));
    expect(corrupt).toBeTruthy();
    expect(await fs.readFile(path.join(dir, corrupt!), "utf8")).toBe("{ this is not json");

    await saveAppState({ aircraft: [] });
    expect((await fs.readdir(dir)).some((f) => f.startsWith("app-state.json.corrupt-"))).toBe(true);
  });

  it("a missing file simply means defaults", async () => {
    const { getAppState } = await load();
    expect((await getAppState()).aircraft).toEqual([]);
    expect((await fs.readdir(dir)).length).toBe(0);
  });

  it("other read errors are not treated as 'no data'", async () => {
    await fs.mkdir(file); // a directory where the file should be -> EISDIR
    const { getAppState } = await load();
    await expect(getAppState()).rejects.toThrow();
  });
});

describe("persisted URLs", () => {
  it("a disallowed persisted API URL falls back to the default without logging the query", async () => {
    await fs.writeFile(
      file,
      JSON.stringify({ settings: { apiAuthUrl: "https://evil.com/token?client=1", apiBaseUrl: "http://x" } })
    );
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    const { getAppState } = await load();
    const s = (await getAppState()).settings;
    expect(s.apiAuthUrl).toContain("auth.opensky-network.org");
    expect(s.apiBaseUrl).toBe("https://opensky-network.org/api");
    expect(warn.mock.calls.flat().join(" ")).not.toContain("client=1");
  });
});

describe("POST /api/app/state", () => {
  it("rejects a disallowed API URL with 400 and changes nothing", async () => {
    const router = (await import("../routes/appState")).default;
    const store = await load();
    const server = await startTestServer([["/api", router]]);
    try {
      const res = await server.post("/api/app/state", {
        settings: { apiAuthUrl: "https://opensky-network.org@evil.com/t", apiClientSecret: "s" }
      });
      expect(res.status).toBe(400);
      expect(res.body).toEqual({ error: "URL not allowed", status: 400 });
      expect((await store.getAppState()).settings.apiClientSecret).toBe("");
      await expect(fs.stat(file)).rejects.toThrow(); // nothing persisted

      const ok = await server.post("/api/app/state", { settings: { apiBaseUrl: "" } });
      expect(ok.status).toBe(200);
    } finally {
      await server.close();
    }
  });
});
