import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { getDevSite, getDevUser } from '@/shared/auth';
import { TicketStatus } from '@/shared/api';
import { MY_SOLUTIONS, VIEWABLE_SPACES } from './data';

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
  // The signed-in user's Solutions (AC-003.6): newest first, filters of AC-003.19, wrong parameters give 400 per parameter.
  http.get('*/api/v1/me/solutions', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const user = getDevUser();
    if (!user) return problem(401, 'MSG07');
    const all = MY_SOLUTIONS[user];
    if (!all) return problem(403, 'MSG08');

    const query = new URL(request.url).searchParams;
    const wrong: string[] = [];
    const int = (name: string, fallback: number, min: number, max: number) => {
      const raw = query.get(name);
      if (raw === null) return fallback;
      const value = Number(raw);
      if (raw.trim() === '' || !Number.isInteger(value) || value < min || value > max) {
        wrong.push(name);
        return fallback;
      }
      return value;
    };
    const day = (name: string) => {
      const raw = query.get(name);
      if (raw === null) return undefined;
      const valid =
        /^\d{4}-\d{2}-\d{2}$/.test(raw) &&
        !Number.isNaN(Date.parse(raw)) &&
        new Date(raw).toISOString().slice(0, 10) === raw;
      if (!valid) wrong.push(name);
      return valid ? raw : undefined;
    };
    const page = int('page', 0, 0, Number.MAX_SAFE_INTEGER);
    const size = int('size', 20, 1, 100);
    const status = query.get('status');
    if (status !== null && !STATUS_VALUES.includes(status)) wrong.push('status');
    const projectId = query.get('projectId');
    if (projectId !== null && !UUID.test(projectId)) wrong.push('projectId');
    const from = day('from');
    const to = day('to');
    if (from && to && to < from) wrong.push('to');
    if (wrong.length) {
      return problem(400, 'MSG06', {
        fieldErrors: wrong.map((field) => ({ field, code: 'MSG06' as const })),
      });
    }

    const dayOf = (iso: string) =>
      new Date(Date.parse(iso) + 7 * 3_600_000).toISOString().slice(0, 10);
    const rows = all
      .filter(
        (r) =>
          (status === null || r.status === status) &&
          (projectId === null || r.projectId === projectId) &&
          (!from || dayOf(r.updatedAt) >= from) &&
          (!to || dayOf(r.updatedAt) <= to),
      )
      // Ties on updatedAt: ticket id ascending (mock-only rule).
      .sort(
        (a, b) => b.updatedAt.localeCompare(a.updatedAt) || a.ticketId.localeCompare(b.ticketId),
      );
    return HttpResponse.json({
      content: rows.slice(page * size, (page + 1) * size),
      page: {
        size,
        number: page,
        totalElements: rows.length,
        totalPages: Math.ceil(rows.length / size),
      },
    });
  }),
];

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const STATUS_VALUES: string[] = Object.values(TicketStatus);

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  // The fixtures are read only.
}
