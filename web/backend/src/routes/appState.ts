import { Router } from "express";
import { asyncHandler } from "../middleware/http";
import { getAppState, maskSecrets, saveAppState } from "../services/appStateStore";
import { assertAllowedUrl, setApiSettings, UrlNotAllowedError } from "../services/settings";

const router = Router();

router.get("/app/state", asyncHandler(async (_req, res) => {
  const state = await getAppState();
  res.json(maskSecrets(state));
}));

router.post("/app/state", asyncHandler(async (req, res) => {
  const incoming = req.body || {};
  try {
    assertAllowedUrl(incoming.settings?.apiBaseUrl);
    assertAllowedUrl(incoming.settings?.apiAuthUrl);
  } catch (error) {
    if (error instanceof UrlNotAllowedError) {
      res.status(400).json({ error: "URL not allowed", status: 400 });
      return;
    }
    throw error;
  }
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

  res.json(maskSecrets(updated));
}));

export default router;
