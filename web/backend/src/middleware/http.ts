import { NextFunction, Request, Response } from "express";
import { ApiError, getCreditsRemaining } from "../services/opensky";

export const CREDITS_HEADER = "X-OpenSky-Credits-Remaining";

export const asyncHandler =
  (handler: (req: Request, res: Response) => Promise<void>) =>
  (req: Request, res: Response, next: NextFunction) => {
    handler(req, res).catch(next);
  };

/** Exposes the last known OpenSky credit balance to clients. */
export const setCreditsHeader = (res: Response) => {
  const remaining = getCreditsRemaining();
  if (remaining !== null) {
    res.set(CREDITS_HEADER, String(remaining));
  }
};

/**
 * Central error handler: malformed JSON -> 400, OpenSky ApiErrors keep their
 * status (429 carries Retry-After), anything else -> 500.
 */
export const errorHandler = (
  err: unknown,
  _req: Request,
  res: Response,
  _next: NextFunction
) => {
  if (err instanceof SyntaxError) {
    res.status(400).json({ error: "Malformed JSON", status: 400 });
    return;
  }

  if (err instanceof ApiError) {
    setCreditsHeader(res);
    if (err.retryAfterSec !== undefined) {
      res.set("Retry-After", String(Math.ceil(err.retryAfterSec)));
    }
    res.status(err.status).json({ error: err.message, status: err.status });
    return;
  }

  console.error("Unhandled request error", err);
  res.status(500).json({ error: "Internal server error", status: 500 });
};
