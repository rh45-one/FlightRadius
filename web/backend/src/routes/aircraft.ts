import { Router } from "express";
import { asyncHandler, setCreditsHeader } from "../middleware/http";
import {
  getAircraftTelemetry,
  getAircraftTelemetryByCallsign,
  isValidCallsign,
  isValidIcao24,
  validateCallsigns
} from "../services/opensky";

const router = Router();

router.get("/callsign/:callsign", asyncHandler(async (req, res) => {
  const { callsign } = req.params;

  if (!isValidCallsign(callsign)) {
    res.status(400).json({ error: "Invalid callsign", status: 400 });
    return;
  }

  const telemetry = await getAircraftTelemetryByCallsign(callsign);
  setCreditsHeader(res);
  res.json(telemetry);
}));

router.get("/:icao24", asyncHandler(async (req, res) => {
  const { icao24 } = req.params;

  if (!isValidIcao24(icao24)) {
    res.status(400).json({ error: "Invalid ICAO24", status: 400 });
    return;
  }

  const telemetry = await getAircraftTelemetry(icao24);
  setCreditsHeader(res);
  res.json(telemetry);
}));

router.post("/validate-callsigns", asyncHandler(async (req, res) => {
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

  const result = await validateCallsigns(cleaned);
  setCreditsHeader(res);
  res.json({ status: "ok", results: result });
}));

export default router;
