import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { getDevSite, getDevUser } from '@/shared/auth';
import { VIEWABLE_SPACES } from './data';

/**
 * MSW mock handlers of the "my-profile" feature (guideline 10 §5). Hand-written handlers with realistic data go here;
 * endpoints without one fall back to orval's generated fake-data handlers (src/mocks/handlers.ts).
 */
export const handlers: HttpHandler[] = [
  // The Spaces of the signed-in user only; userId / workspaceId query parameters are ignored (AC-003.4).
  http.get('*/api/v1/me/spaces', async () => {
    await delay(MOCK_DELAY_MS);
    const site = getDevSite();
    if (site) return site === 'unknown-site' ? problem(404, 'MSG119') : problem(403, 'MSG120');
    const user = getDevUser();
    if (!user) return problem(401, 'MSG07');
    return HttpResponse.json(
      [...VIEWABLE_SPACES[user]].sort((a, b) => a.name.localeCompare(b.name, 'vi')),
    );
  }),
];

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  // The fixtures are read only.
}
