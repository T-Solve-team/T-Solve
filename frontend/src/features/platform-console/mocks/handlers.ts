import type { HttpHandler } from 'msw';

/**
 * MSW mock handlers of the "platform-console" feature (guideline 10 §5). None yet; endpoints without one fall back
 * to orval's generated fake-data handlers (src/mocks/handlers.ts).
 */
export const handlers: HttpHandler[] = [];

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  // No mock data yet.
}
