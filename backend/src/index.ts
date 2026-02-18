import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import aircraftRoutes from "./routes/aircraft";
import locationRoutes from "./routes/location";
import settingsRoutes from "./routes/settings";
import { getCacheSize } from "./services/cache";
import { pingOpenSky } from "./services/opensky";
import {
  getLastLocationTimestamp,
  getLocationIngestStatus
} from "./services/locationStore";

dotenv.config();

const app = express();
const port = Number(process.env.PORT) || 3000;

app.use(cors());
app.use(express.json());

app.get("/api/health", async (_req, res) => {
  const openskyStatus = await pingOpenSky();

  res.json({
    status: "ok",
    uptime: process.uptime(),
    opensky_status: openskyStatus,
    cache_entries: getCacheSize(),
    location_ingest_status: getLocationIngestStatus(),
    last_location_timestamp: getLastLocationTimestamp()
  });
});

app.use("/api/aircraft", aircraftRoutes);
app.use("/api", locationRoutes);
app.use("/api/settings", settingsRoutes);

app.listen(port, () => {
  console.log(`Backend listening on port ${port}`);
});
