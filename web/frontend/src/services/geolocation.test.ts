import { describe, expect, it, vi } from "vitest";
import { requestLocation } from "./geolocation";

describe("requestLocation", () => {
  it("maps permission denied errors", async () => {
    const getCurrentPosition = vi.fn((_success, error) =>
      error({
        code: 1,
        PERMISSION_DENIED: 1,
        POSITION_UNAVAILABLE: 2,
        TIMEOUT: 3
      })
    );

    Object.defineProperty(globalThis.navigator, "geolocation", {
      value: { getCurrentPosition },
      configurable: true
    });

    await expect(requestLocation()).rejects.toMatchObject({
      permissionStatus: "denied"
    });
  });
});
