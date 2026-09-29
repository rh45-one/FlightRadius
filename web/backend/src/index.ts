import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import aircraftRoutes from "./routes/aircraft";
import locationRoutes from "./routes/location";
import settingsRoutes from "./routes/settings";
import appStateRoutes from "./routes/appState";
import distanceRoutes from "./routes/distance";
import { getCacheSize } from "./services/cache";
import { getCreditsRemaining, pingOpenSky } from "./services/opensky";
import { errorHandler } from "./middleware/http";
import {
  getLastLocationTimestamp,
  getLocationIngestStatus
} from "./services/locationStore";
import { getAppState } from "./services/appStateStore";
import { setApiSettings } from "./services/settings";

dotenv.config();

const app = express();
const port = Number(process.env.PORT) || 3000;

const corsOrigins = (process.env.CORS_ORIGIN || "")
  .split(",")
  .map((origin) => origin.trim())
  .filter((origin) => origin.length > 0);

if (corsOrigins.length > 0) {
  app.use(cors({ origin: corsOrigins }));
}
app.use(express.json({ limit: "1mb" }));

app.get("/api/health", async (_req, res) => {
  const openskyStatus = await pingOpenSky();

  res.json({
    status: "ok",
    uptime: process.uptime(),
    opensky_status: openskyStatus,
    cache_entries: getCacheSize(),
    location_ingest_status: getLocationIngestStatus(),
    last_location_timestamp: getLastLocationTimestamp(),
    opensky_credits_remaining: getCreditsRemaining()
  });
});

app.use("/api/aircraft", aircraftRoutes);
app.use("/api", locationRoutes);
app.use("/api", appStateRoutes);
app.use("/api/distance", distanceRoutes);
app.use("/api/settings", settingsRoutes);

app.use(errorHandler);

app.listen(port, () => {
  console.log(`Backend listening on port ${port}`);
});

getAppState()
  .then((state) => {
    const s = state.settings;
    setApiSettings({
      ...(s.apiBaseUrl ? { baseUrl: s.apiBaseUrl } : {}),
      ...(s.apiAuthUrl ? { authUrl: s.apiAuthUrl } : {}),
      ...(s.apiUsername ? { username: s.apiUsername } : {}),
      ...(s.apiPassword ? { password: s.apiPassword } : {}),
      ...(s.apiClientId ? { clientId: s.apiClientId } : {}),
      ...(s.apiClientSecret ? { clientSecret: s.apiClientSecret } : {})
    });
  })
  .catch(() => undefined);
