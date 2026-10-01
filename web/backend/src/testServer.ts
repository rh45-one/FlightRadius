import express from "express";
import { AddressInfo } from "net";
import { Server } from "http";
import { errorHandler } from "./middleware/http";

/** Test helper: mounts routers on an ephemeral port and returns a tiny client. */
export const startTestServer = async (mounts: Array<[string, express.Router]>) => {
  const app = express();
  app.use(express.json({ limit: "1mb" }));
  for (const [path, router] of mounts) app.use(path, router);
  app.use(errorHandler);
  const server: Server = await new Promise((resolve) => {
    const s = app.listen(0, "127.0.0.1", () => resolve(s));
  });
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  return {
    post: async (path: string, body: unknown) => {
      const res = await fetch(base + path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
      return { status: res.status, body: (await res.json()) as any };
    },
    get: async (path: string) => {
      const res = await fetch(base + path);
      return { status: res.status, body: (await res.json()) as any };
    },
    close: () => new Promise<void>((resolve) => server.close(() => resolve()))
  };
};
