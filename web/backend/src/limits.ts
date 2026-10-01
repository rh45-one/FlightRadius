/**
 * Request size caps. Every 100 icao24s become a serialized, rate-limited
 * OpenSky request, so unbounded batches could block the queue and burn credits.
 */
export const MAX_IDENTIFIERS = 500;
export const MAX_GROUPS = 50;

export const TOO_MANY_IDENTIFIERS = `Too many identifiers (max ${MAX_IDENTIFIERS})`;
export const TOO_MANY_GROUPS = `Too many groups (max ${MAX_GROUPS})`;

const length = (value: unknown) => (Array.isArray(value) ? value.length : 0);

/** Combined raw length of the given identifier lists (before de-duplication). */
export const identifierCount = (...lists: unknown[]) =>
  lists.reduce<number>((sum, list) => sum + length(list), 0);
