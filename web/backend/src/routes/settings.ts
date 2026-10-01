import { Router } from "express";
import {
  assertAllowedUrl,
  getApiSettings,
  setApiSettings,
  UrlNotAllowedError
} from "../services/settings";

const router = Router();

router.get("/api", (_req, res) => {
  const current = getApiSettings();
  const hasClient = Boolean(current.clientId && current.clientSecret);
  const hasBasic = Boolean(current.username && current.password);
  // OpenSky only accepts OAuth2 client credentials; stored basic credentials
  // are reported (basicConfigured) but no longer used.
  const authMode = hasClient ? "oauth2" : "anonymous";

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

  try {
    assertAllowedUrl(baseUrl);
    assertAllowedUrl(authUrl);
  } catch (error) {
    if (error instanceof UrlNotAllowedError) {
      return res.status(400).json({ error: "URL not allowed", status: 400 });
    }
    throw error;
  }

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
