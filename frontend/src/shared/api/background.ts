/**
 * Requests the user did not start: a refetch (interval, window focus, reconnect, invalidation) of a query that
 * already holds data. `http()` sends them with `X-TSolve-Background: 1`, so the server does not count them as
 * activity and the session can still expire after 60 minutes idle (AC-012.6, AC-012.7).
 */
const marked = new WeakSet<AbortSignal>();

/** The header that tells the server a request is not user activity. */
export const BACKGROUND_HEADER = 'X-TSolve-Background';

/**
 * Marks the request that uses this signal as a background request. The generated hooks do it through
 * `query-options.ts`; a hand-written query calls it with the `signal` of its query function when it refetches.
 */
export function markBackground(signal: AbortSignal): void {
  marked.add(signal);
}

export function isBackground(signal: AbortSignal | null | undefined): boolean {
  return signal !== null && signal !== undefined && marked.has(signal);
}
