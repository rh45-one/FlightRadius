import { describe, expect, it } from "vitest";
import { formatDistance } from "./distance";

describe("formatDistance", () => {
  it("formats kilometers", () => {
    expect(formatDistance(120.5, "km")).toBe("120.50 km");
  });

  it("formats miles", () => {
    expect(formatDistance(160.93, "mi")).toBe("100.00 mi");
  });
});
