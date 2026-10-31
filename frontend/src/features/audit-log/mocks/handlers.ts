import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type { AuditEntryPage } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { getDevSite, getDevUser } from '@/shared/auth';
import { parseSort } from '@/shared/lib';
import { dateRange, isAuditAction } from '../hooks/audit-filters';
import { AUDIT_ENTRIES, AUDIT_WORKSPACE } from './data';

/**
 * MSW mock handlers of the "audit-log" feature (guideline 10 §5). Hand-written handlers with realistic data go here;
 * endpoints without one fall back to orval's generated fake-data handlers (src/mocks/handlers.ts).
 */
export const handlers: HttpHandler[] = [
  http.get('*/api/v1/audit-events', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const site = getDevSite();
    if (site) return site === 'unknown-site' ? problem(404, 'MSG119') : problem(403, 'MSG120');
    const user = getDevUser();
    if (!user) return problem(401, 'MSG07');
    if (user !== 'admin' && user !== 'admin-tr') return problem(403, 'MSG08');
    const params = new URL(request.url).searchParams;
    const range = dateRange(params.get('from'), params.get('to'));
    if (!range.valid) return problem(400, 'MSG39');
    const actor = params.get('actor')?.trim().toLowerCase();
    const action = params.get('action');
    if (action && !isAuditAction(action)) return problem(400, 'MSG06');
    const sort = parseSort(params.get('sort')) ?? { field: 'createdAt', order: 'desc' };
    const direction = sort.order === 'asc' ? 1 : -1;
    const filtered = AUDIT_ENTRIES.filter((entry) => {
      const timestamp = Date.parse(entry.createdAt);
      return (
        entry.workspaceId === AUDIT_WORKSPACE &&
        (!actor || entry.actor.toLowerCase().includes(actor)) &&
        (!action || entry.action === action) &&
        (range.start === undefined || timestamp >= range.start) &&
        (range.end === undefined || timestamp < range.end)
      );
    }).sort(
      (a, b) => a.createdAt.localeCompare(b.createdAt) * direction || a.id.localeCompare(b.id),
    );
    const integer = (value: string | null, fallback: number) => {
      const number = Number(value);
      return value !== null && Number.isSafeInteger(number) && number >= 0 ? number : fallback;
    };
    const page = integer(params.get('page'), 0);
    const size = Math.min(100, Math.max(1, integer(params.get('size'), 20)));
    return HttpResponse.json<AuditEntryPage>({
      content: filtered
        .slice(page * size, (page + 1) * size)
        .map(({ workspaceId: _workspace, ...entry }) => entry),
      page: {
        size,
        number: page,
        totalElements: filtered.length,
        totalPages: Math.ceil(filtered.length / size),
      },
    });
  }),
];

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  // No mock data yet. See features/review/mocks for an example.
}
