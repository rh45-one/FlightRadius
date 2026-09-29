import { describe, expect, it, vi } from "vitest";
import type { NextFunction, Request, Response } from "express";
import { errorHandler } from "./http";
import { ApiError } from "../services/opensky";

const fakeResponse = () => {
  const res = {
    headers: {} as Record<string, string>,
    statusCode: 0,
    body: undefined as unknown,
    set(name: string, value: string) {
      this.headers[name] = value;
      return this;
    },
    status(code: number) {
      this.statusCode = code;
      return this;
    },
    json(body: unknown) {
      this.body = body;
      return this;
    }
  };
  return res;
};

const run = (err: unknown) => {
  const res = fakeResponse();
  errorHandler(err, {} as Request, res as unknown as Response, vi.fn() as NextFunction);
  return res;
};

describe("errorHandler", () => {
  it("passes OpenSky 429 through with Retry-After", () => {
    const res = run(new ApiError("OpenSky credits exhausted", 429, 90.2));
    expect(res.statusCode).toBe(429);
    expect(res.headers["Retry-After"]).toBe("91");
    expect(res.body).toEqual({ error: "OpenSky credits exhausted", status: 429 });
  });

  it("keeps other OpenSky statuses", () => {
    expect(run(new ApiError("OpenSky timeout", 504)).statusCode).toBe(504);
    expect(run(new ApiError("Aircraft not found", 404)).statusCode).toBe(404);
  });

  it("maps malformed JSON to 400 and unknown errors to 500", () => {
    expect(run(new SyntaxError("bad")).statusCode).toBe(400);
    const logged = vi.spyOn(console, "error").mockImplementation(() => undefined);
    const res = run(new Error("boom"));
    expect(logged).toHaveBeenCalledOnce();
    logged.mockRestore();
    expect(res.statusCode).toBe(500);
    expect(res.body).toEqual({ error: "Internal server error", status: 500 });
  });
});
