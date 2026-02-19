import { Router } from "express";
import { getMockAircraftPositions } from "../services/mockAircraft";
import { buildDistanceResults } from "../services/distanceEngine";

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

router.post("/compute", async (req, res) => {
  const { user_location, callsigns, groups } = req.body || {};

  if (
    !user_location ||
    !isNumber(user_location.lat) ||
    !isNumber(user_location.lon)
  ) {
    res.status(400).json({ error: "Invalid user_location", status: 400 });
    return;
  }

  if (
    user_location.lat < -90 ||
    user_location.lat > 90 ||
    user_location.lon < -180 ||
    user_location.lon > 180
  ) {
    res.status(400).json({ error: "Invalid coordinates", status: 400 });
    return;
  }

  if (callsigns !== undefined && !Array.isArray(callsigns)) {
    res.status(400).json({ error: "Invalid callsigns list", status: 400 });
    return;
  }

  if (groups !== undefined && !Array.isArray(groups)) {
    res.status(400).json({ error: "Invalid groups payload", status: 400 });
    return;
  }

  const positions = await getMockAircraftPositions();
  const normalizedCallsigns = normalizeCallsigns(callsigns);
  const overall = buildDistanceResults(
    { lat: user_location.lat, lon: user_location.lon },
    positions,
    normalizedCallsigns
  );

  const groupResults = Array.isArray(groups)
    ? groups
        .filter((group) => group && typeof group.name === "string")
        .map((group) => ({
          name: group.name,
          ...buildDistanceResults(
            { lat: user_location.lat, lon: user_location.lon },
            positions,
            normalizeCallsigns(group.callsigns)
          )
        }))
    : [];

  res.json({
    results: overall.results,
    closest: overall.closest,
    missing: overall.missing,
    groups: groupResults
  });
});

export default router;
