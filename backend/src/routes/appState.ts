import { Router } from "express";
import { getAppState, saveAppState } from "../services/appStateStore";
import { setApiSettings } from "../services/settings";

const router = Router();

router.get("/app/state", async (_req, res) => {
  const state = await getAppState();
  res.json(state);
});

router.post("/app/state", async (req, res) => {
  const incoming = req.body || {};
  const updated = await saveAppState(incoming);

  if (incoming.settings) {
    setApiSettings({
      baseUrl: updated.settings.apiBaseUrl,
      authUrl: updated.settings.apiAuthUrl,
      username: updated.settings.apiUsername,
      password: updated.settings.apiPassword,
      clientId: updated.settings.apiClientId,
      clientSecret: updated.settings.apiClientSecret
    });
  }

  res.json(updated);
});

export default router;
