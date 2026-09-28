import { NextFunction, Request, Response, Router } from "express";
import { getAppState, maskSecrets, saveAppState } from "../services/appStateStore";
import { setApiSettings } from "../services/settings";

const router = Router();

const asyncHandler =
  (handler: (req: Request, res: Response) => Promise<void>) =>
  (req: Request, res: Response, next: NextFunction) => {
    handler(req, res).catch(next);
  };

router.get("/app/state", asyncHandler(async (_req, res) => {
  const state = await getAppState();
  res.json(maskSecrets(state));
}));

router.post("/app/state", asyncHandler(async (req, res) => {
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

  res.json(maskSecrets(updated));
}));

export default router;
