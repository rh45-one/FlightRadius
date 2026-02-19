import { Router } from "express";
import {
  ApiError,
  getAircraftTelemetry,
  getAircraftTelemetryByCallsign,
  isValidCallsign,
  isValidIcao24
} from "../services/opensky";
import { validateMockCallsigns } from "../services/mockAircraft";

const router = Router();

router.get("/callsign/:callsign", async (req, res) => {
  const { callsign } = req.params;

  if (!isValidCallsign(callsign)) {
    res.status(400).json({ error: "Invalid callsign", status: 400 });
    return;
  }

  try {
    const telemetry = await getAircraftTelemetryByCallsign(callsign);
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

router.post("/validate-callsigns", async (req, res) => {
  const { callsigns } = req.body || {};

  if (!Array.isArray(callsigns)) {
    res.status(400).json({ error: "Invalid payload", status: 400 });
    return;
  }

  const cleaned = callsigns
    .filter((item) => typeof item === "string")
    .map((item) => item.trim())
    .filter((item) => item.length > 0);

  const invalid = cleaned.filter((item) => !isValidCallsign(item));
  if (invalid.length > 0) {
    res.status(400).json({
      error: "Invalid callsign format",
      status: 400,
      invalid
    });
    return;
  }

  try {
    const result = await validateMockCallsigns(cleaned);
    res.json({ status: "ok", results: result });
  } catch (error) {
    console.error("Callsign validation error", error);
    res.status(500).json({ error: "Mock dataset unavailable", status: 500 });
  }
});

export default router;
