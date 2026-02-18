import { Router } from "express";
import { setApiSettings } from "../services/settings";

const router = Router();

router.post("/api", (req, res) => {
  const { baseUrl, username, password } = req.body || {};

  const updated = setApiSettings({
    baseUrl,
    username,
    password
  });

  res.json({
    status: "ok",
    api: {
      baseUrl: updated.baseUrl,
      username: updated.username ? "configured" : "",
      password: updated.password ? "configured" : ""
    }
  });
});

export default router;
