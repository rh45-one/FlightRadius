import { Router } from "express";

const router = Router();

router.get("/:icao24", (req, res) => {
  const { icao24 } = req.params;

  res.json({
    icao24,
    latitude: null,
    longitude: null,
    distance_km: null,
    status: "unimplemented"
  });
});

export default router;
