import { describe, expect, it } from "vitest";
import { MAX_GROUPS, MAX_IDENTIFIERS } from "../limits";
import { startTestServer } from "../testServer";
import aircraftRoutes from "./aircraft";
import distanceRoutes from "./distance";

const ids = (n: number, prefix = "AB") => Array.from({ length: n }, (_, i) => `${prefix}${i}`);

describe("batch size caps", async () => {
  const server = await startTestServer([
    ["/api/aircraft", aircraftRoutes],
    ["/api/distance", distanceRoutes]
  ]);
  const loc = { lat: 40, lon: -3 };
  const tooMany = { error: `Too many identifiers (max ${MAX_IDENTIFIERS})`, status: 400 };

  it("constants", () => {
    expect(MAX_IDENTIFIERS).toBe(500);
    expect(MAX_GROUPS).toBe(50);
  });

  it("/distance/aircraft counts callsigns and icao24s together", async () => {
    const r = await server.post("/api/distance/aircraft", {
      ...loc, callsigns: ids(300), icao24s: ids(201)
    });
    expect(r.status).toBe(400);
    expect(r.body).toEqual(tooMany);
  });

  it("/distance/compute rejects too many identifiers and too many groups", async () => {
    const a = await server.post("/api/distance/compute", {
      user_location: loc, callsigns: ids(501)
    });
    expect(a.status).toBe(400);
    expect(a.body).toEqual(tooMany);
    const b = await server.post("/api/distance/compute", {
      user_location: loc, groups: ids(51).map((n) => ({ name: n, callsigns: [] }))
    });
    expect(b.status).toBe(400);
    expect(b.body.error).toBe(`Too many groups (max ${MAX_GROUPS})`);
  });

  it("/distance/fleets rejects too many fleets and too many callsigns", async () => {
    const a = await server.post("/api/distance/fleets", {
      ...loc, fleets: ids(51).map((n) => ({ name: n, callsigns: [] }))
    });
    expect(a.status).toBe(400);
    expect(a.body.error).toBe(`Too many groups (max ${MAX_GROUPS})`);
    const b = await server.post("/api/distance/fleets", {
      ...loc, fleets: [{ name: "x", callsigns: ids(501) }]
    });
    expect(b.status).toBe(400);
    expect(b.body).toEqual(tooMany);
  });

  it("validate-callsigns caps at 500", async () => {
    const r = await server.post("/api/aircraft/validate-callsigns", { callsigns: ids(501) });
    expect(r.status).toBe(400);
    expect(r.body).toEqual(tooMany);
  });
});
