import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import aircraftRoutes from "./routes/aircraft";
import settingsRoutes from "./routes/settings";
import { getCacheSize } from "./services/cache";
import { pingOpenSky } from "./services/opensky";

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
    cache_entries: getCacheSize()
  });
});

app.use("/api/aircraft", aircraftRoutes);
app.use("/api/settings", settingsRoutes);

app.listen(port, () => {
  console.log(`Backend listening on port ${port}`);
});
