import { Router } from "express";
import { OpenSkyProvider } from "../providers/openSkyProvider";
import { buildDistanceResults, buildGroupProximity } from "../services/distanceEngine";

const router = Router();

const isNumber = (value: unknown): value is number =>
  typeof value === "number" && Number.isFinite(value);

const normalizeCallsigns = (input: unknown) => {
  if (!Array.isArray(input)) {
    return [] as string[];
  }

  return Array.from(
    new Set(
      input
        .filter((item) => typeof item === "string")
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
        .map((item) => item.toUpperCase())
    )
  );
};

const normalizeIcao24s = (input: unknown) => {
  if (!Array.isArray(input)) {
    return [] as string[];
  }

  return Array.from(
    new Set(
      input
        .filter((item) => typeof item === "string")
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
        .map((item) => item.toLowerCase())
    )
  );
};

const isValidCoordinates = (lat: number, lon: number) =>
  lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;

router.post("/aircraft", async (req, res) => {
  const { lat, lon, callsigns, icao24s } = req.body || {};

  if (!isNumber(lat) || !isNumber(lon)) {
    res.status(400).json({ error: "Invalid coordinates", status: 400 });
    return;
  }

  if (!isValidCoordinates(lat, lon)) {
    res.status(400).json({ error: "Invalid coordinates", status: 400 });
    return;
  }

  if (callsigns !== undefined && !Array.isArray(callsigns)) {
    res.status(400).json({ error: "Invalid callsigns list", status: 400 });
    return;
  }

  if (icao24s !== undefined && !Array.isArray(icao24s)) {
    res.status(400).json({ error: "Invalid icao24 list", status: 400 });
    return;
  }

  const normalizedCallsigns = normalizeCallsigns(callsigns);
  const normalizedIcao24s = normalizeIcao24s(icao24s);
  const positions = await OpenSkyProvider.getPositions({
    callsigns: normalizedCallsigns,
    icao24s: normalizedIcao24s
  });
  const identifiers = [...normalizedCallsigns, ...normalizedIcao24s];
  const summary = buildDistanceResults({ lat, lon }, positions, identifiers);

  console.log("[API RESPONSE]", summary.results);
  res.json({
    results: summary.results
  });
});

router.post("/fleets", async (req, res) => {
  const { lat, lon, fleets } = req.body || {};

  if (!isNumber(lat) || !isNumber(lon)) {
    res.status(400).json({ error: "Invalid coordinates", status: 400 });
    return;
  }

  if (!isValidCoordinates(lat, lon)) {
    res.status(400).json({ error: "Invalid coordinates", status: 400 });
    return;
  }

  if (!Array.isArray(fleets)) {
    res.status(400).json({ error: "Invalid fleets payload", status: 400 });
    return;
  }

  const normalizedFleets = fleets
    .filter((fleet) => fleet && typeof fleet.name === "string")
    .map((fleet) => ({
      name: fleet.name,
      callsigns: normalizeCallsigns(fleet.callsigns)
    }));

  const uniqueCallsigns = Array.from(
    new Set(normalizedFleets.flatMap((fleet) => fleet.callsigns))
  );
  const positions = await OpenSkyProvider.getPositions({
    callsigns: uniqueCallsigns,
    icao24s: []
  });

  const results = buildGroupProximity({ lat, lon }, positions, normalizedFleets);

  res.json({ fleets: results });
});

router.post("/compute", async (req, res) => {
  const { user_location, callsigns, groups, icao24s } = req.body || {};

  if (
    !user_location ||
    !isNumber(user_location.lat) ||
    !isNumber(user_location.lon)
  ) {
    res.status(400).json({ error: "Invalid user_location", status: 400 });
    return;
  }

  if (!isValidCoordinates(user_location.lat, user_location.lon)) {
    res.status(400).json({ error: "Invalid coordinates", status: 400 });
    return;
  }

  if (callsigns !== undefined && !Array.isArray(callsigns)) {
    res.status(400).json({ error: "Invalid callsigns list", status: 400 });
    return;
  }

  if (icao24s !== undefined && !Array.isArray(icao24s)) {
    res.status(400).json({ error: "Invalid icao24 list", status: 400 });
    return;
  }

  if (groups !== undefined && !Array.isArray(groups)) {
    res.status(400).json({ error: "Invalid groups payload", status: 400 });
    return;
  }

  const normalizedCallsigns = normalizeCallsigns(callsigns);
  const normalizedIcao24s = normalizeIcao24s(icao24s);
  const positions = await OpenSkyProvider.getPositions({
    callsigns: normalizedCallsigns,
    icao24s: normalizedIcao24s
  });
  const identifiers = [...normalizedCallsigns, ...normalizedIcao24s];
  const overall = buildDistanceResults(
    { lat: user_location.lat, lon: user_location.lon },
    positions,
    identifiers
  );

  const groupResults = Array.isArray(groups)
    ? buildGroupProximity(
        { lat: user_location.lat, lon: user_location.lon },
        positions,
        groups
          .filter((group) => group && typeof group.name === "string")
          .map((group) => ({
            name: group.name,
            callsigns: normalizeCallsigns(group.callsigns)
          }))
      )
    : [];

  res.json({
    results: overall.results,
    closest: overall.closest,
    missing: overall.missing,
    groups: groupResults
  });
});

export default router;
