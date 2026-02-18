type CacheEntry<T> = {
  value: T;
  expiresAt: number;
};

const cache = new Map<string, CacheEntry<unknown>>();
const DEFAULT_TTL_MS = 10_000;

export const getCacheEntry = <T>(
  key: string,
  options?: { allowStale?: boolean }
) => {
  const entry = cache.get(key) as CacheEntry<T> | undefined;
  if (!entry) {
    return null;
  }

  const now = Date.now();
  const isExpired = now > entry.expiresAt;

  if (isExpired && !options?.allowStale) {
    cache.delete(key);
    return null;
  }

  return {
    value: entry.value,
    isStale: isExpired
  };
};

export const setCacheEntry = <T>(key: string, value: T, ttlMs = DEFAULT_TTL_MS) => {
  cache.set(key, {
    value,
    expiresAt: Date.now() + ttlMs
  });
};

export const purgeExpiredEntries = () => {
  const now = Date.now();
  for (const [key, entry] of cache.entries()) {
    if (now > entry.expiresAt) {
      cache.delete(key);
    }
  }
};

export const getCacheSize = () => {
  purgeExpiredEntries();
  return cache.size;
};
