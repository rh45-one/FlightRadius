type SchedulerOptions = {
  intervalMs: number;
  debounceMs: number;
};

export const createDistanceScheduler = (
  options: SchedulerOptions,
  onTick: () => void
) => {
  let intervalId: number | null = null;
  let timeoutId: number | null = null;

  const trigger = () => {
    if (timeoutId !== null) {
      window.clearTimeout(timeoutId);
    }

    timeoutId = window.setTimeout(() => {
      onTick();
    }, options.debounceMs);
  };

  const start = () => {
    if (intervalId !== null) {
      return;
    }

    intervalId = window.setInterval(() => {
      onTick();
    }, options.intervalMs);
  };

  const stop = () => {
    if (intervalId !== null) {
      window.clearInterval(intervalId);
      intervalId = null;
    }
    if (timeoutId !== null) {
      window.clearTimeout(timeoutId);
      timeoutId = null;
    }
  };

  return { start, stop, trigger };
};
