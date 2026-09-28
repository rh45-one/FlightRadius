import { Router } from "express";
import { getApiSettings, setApiSettings } from "../services/settings";

const router = Router();

router.get("/api", (_req, res) => {
  const current = getApiSettings();
  const hasClient = Boolean(current.clientId && current.clientSecret);
  const hasBasic = Boolean(current.username && current.password);
  const authMode = hasClient ? "oauth2" : hasBasic ? "basic" : "anonymous";

  res.json({
    status: "ok",
    api: {
      baseUrl: current.baseUrl,
      authUrl: current.authUrl,
      authMode,
      clientConfigured: hasClient,
      basicConfigured: hasBasic
    }
  });
});

router.post("/api", (req, res) => {
  const { baseUrl, username, password, authUrl, clientId, clientSecret } =
    req.body || {};

  const updated = setApiSettings({
    baseUrl,
    username,
    password,
    authUrl,
    clientId,
    clientSecret
  });

  console.log("API settings updated", {
    baseUrl: updated.baseUrl,
    authUrl: updated.authUrl,
    clientConfigured: Boolean(updated.clientId && updated.clientSecret),
    basicConfigured: Boolean(updated.username && updated.password)
  });

  res.json({
    status: "ok",
    api: {
      baseUrl: updated.baseUrl,
      authUrl: updated.authUrl,
      username: updated.username ? "configured" : "",
      password: updated.password ? "configured" : "",
      clientId: updated.clientId ? "configured" : "",
      clientSecret: updated.clientSecret ? "configured" : ""
    }
  });
});

export default router;
