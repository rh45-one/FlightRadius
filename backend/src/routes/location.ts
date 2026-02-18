import { Router } from "express";
import { setLastLocation } from "../services/locationStore";

const router = Router();

const isNumber = (value: unknown): value is number =>
  typeof value === "number" && Number.isFinite(value);

router.post("/user/location", (req, res) => {
  const { latitude, longitude, accuracy_m, timestamp, source } = req.body || {};

  if (
    !isNumber(latitude) ||
    !isNumber(longitude) ||
    !isNumber(accuracy_m) ||
    !isNumber(timestamp) ||
    (source !== "gps" && source !== "manual")
  ) {
    res.status(400).json({ error: "Invalid location payload", status: 400 });
    return;
  }

  if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
    res.status(400).json({ error: "Invalid coordinates", status: 400 });
    return;
  }

  setLastLocation({
    latitude,
    longitude,
    accuracy_m,
    timestamp,
    source
  });

  res.json({ status: "ok" });
});

export default router;
