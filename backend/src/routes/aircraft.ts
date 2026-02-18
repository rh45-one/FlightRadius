import { Router } from "express";
import { ApiError, getAircraftTelemetry, isValidIcao24 } from "../services/opensky";

const router = Router();

router.get("/:icao24", async (req, res) => {
  const { icao24 } = req.params;

  if (!isValidIcao24(icao24)) {
    res.status(400).json({ error: "Invalid ICAO24", status: 400 });
    return;
  }

  try {
    const telemetry = await getAircraftTelemetry(icao24);
    res.json(telemetry);
  } catch (error) {
    if (error instanceof ApiError) {
      res.status(error.status).json({ error: error.message, status: error.status });
      return;
    }

    console.error("Telemetry error", error);
    res.status(500).json({ error: "OpenSky unavailable", status: 500 });
  }
});

export default router;
