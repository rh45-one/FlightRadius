import { describe, expect, it, vi } from "vitest";
import { createDistanceScheduler } from "./distanceScheduler";

describe("createDistanceScheduler", () => {
  it("debounces triggers and runs on interval", () => {
    vi.useFakeTimers();
    const onTick = vi.fn();

    const scheduler = createDistanceScheduler(
      { intervalMs: 15000, debounceMs: 500 },
      onTick
    );

    scheduler.start();
    scheduler.trigger();
    vi.advanceTimersByTime(400);
    expect(onTick).not.toHaveBeenCalled();

    vi.advanceTimersByTime(200);
    expect(onTick).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(15000);
    expect(onTick).toHaveBeenCalledTimes(2);

    scheduler.stop();
    vi.useRealTimers();
  });
});
