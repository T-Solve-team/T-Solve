import { delay, http, HttpResponse } from 'msw';
import { getMe, isApiError, type CurrentUser, type ExpiredTicketItem } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { parseSort } from '@/shared/lib';
import { MAX_REVIEW_SELECTION } from '../review-limits';
import { MOCK_PROJECTS, REVIEW_WORKSPACE_ID, type MockReviewTicket } from './data';
import { reviewStore } from './state';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
async function actor() {
  try {
    const user = await getMe();
    return user.roles.includes('PROJECT_MANAGER') ? user : problem(403, 'MSG08');
  } catch (error) {
    return isApiError(error) ? problem(error.status, error.code) : problem(401, 'MSG07');
  }
}
function manages(user: CurrentUser, ticket: MockReviewTicket) {
  return (
    ticket.workspaceId === REVIEW_WORKSPACE_ID &&
    user.projects.some(
      (project) =>
        project.projectId === ticket.project.projectId &&
        project.roleInProject === 'PROJECT_MANAGER',
    )
  );
}
function expired(user: CurrentUser, url: URL): ExpiredTicketItem[] {
  const departmentId = url.searchParams.get('departmentId');
  return reviewStore.tickets
    .filter(
      (ticket) =>
        ticket.status === 'EXPIRED' &&
        ticket.source === 'SOLUTION_FORM' &&
        manages(user, ticket) &&
        (!departmentId ||
          MOCK_PROJECTS.find((p) => p.projectId === ticket.project.projectId)?.departmentId ===
            departmentId),
    )
    .map((ticket) => ({
      ticketId: ticket.ticketId,
      sourceTicketId: ticket.sourceTicketId,
      title: ticket.title,
      project: ticket.project,
      source: ticket.source,
      authorName: ticket.authorName,
      enteredQueueAt: ticket.enteredQueueAt,
      reviewDueDate: ticket.reviewDueDate ?? '',
      expiredAt: ticket.expiredAt ?? '',
      previousStatus: ticket.previousStatus ?? 'PENDING_REVIEW',
    }));
}
const values: Record<string, (ticket: ExpiredTicketItem) => string> = {
  expiredAt: (t) => t.expiredAt,
  sourceTicketId: (t) => t.sourceTicketId,
  title: (t) => t.title,
  project: (t) => t.project.name,
};
function list(user: CurrentUser, url: URL) {
  const sort = parseSort(url.searchParams.get('sort')) ?? { field: 'expiredAt', order: 'desc' };
  const value = values[sort.field] ?? values.expiredAt;
  return expired(user, url).sort(
    (a, b) =>
      (value ? value(a).localeCompare(value(b)) : 0) * (sort.order === 'asc' ? 1 : -1) ||
      a.ticketId.localeCompare(b.ticketId),
  );
}
function integer(value: string | null, fallback: number) {
  const n = Number(value);
  return value !== null && Number.isSafeInteger(n) && n >= 0 ? n : fallback;
}

/** GBR-10: send-back calendar date in Vietnam plus current Project range, not the old expiry. */
function dueDate(now: Date, days: number) {
  const local = new Date(now.getTime() + 7 * 60 * 60 * 1000);
  return new Date(
    Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate() + days) -
      7 * 60 * 60 * 1000,
  ).toISOString();
}
export const expiredHandlers = [
  http.get('*/api/v1/expired-tickets', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const user = await actor();
    if (user instanceof HttpResponse) return user;
    const url = new URL(request.url);
    const all = list(user, url);
    const page = integer(url.searchParams.get('page'), 0);
    const size = Math.min(100, Math.max(1, integer(url.searchParams.get('size'), 20)));
    return HttpResponse.json({
      content: all.slice(page * size, (page + 1) * size),
      page: {
        size,
        number: page,
        totalElements: all.length,
        totalPages: Math.ceil(all.length / size),
      },
    });
  }),
  http.get('*/api/v1/expired-tickets/ticket-ids', async ({ request }) => {
    const user = await actor();
    if (user instanceof HttpResponse) return user;
    return HttpResponse.json({
      ticketIds: list(user, new URL(request.url)).map((t) => t.ticketId),
    });
  }),
  http.post('*/api/v1/reviews/send-back', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const user = await actor();
    if (user instanceof HttpResponse) return user;
    let body: unknown;
    try {
      body = await request.json();
    } catch {
      return problem(400);
    }
    const ids = body && typeof body === 'object' && 'ticketIds' in body ? body.ticketIds : null;
    if (
      !Array.isArray(ids) ||
      !ids.length ||
      ids.length > MAX_REVIEW_SELECTION ||
      ids.some((id: unknown) => typeof id !== 'string' || !UUID.test(id)) ||
      new Set(ids).size !== ids.length
    )
      return problem(400);
    const tickets: MockReviewTicket[] = [];
    for (const id of ids) {
      const ticket = reviewStore.tickets.find(
        (t) => t.ticketId === id && t.workspaceId === REVIEW_WORKSPACE_ID,
      );
      if (!ticket) return problem(404);
      if (!manages(user, ticket)) return problem(403, 'MSG08');
      if (ticket.status !== 'EXPIRED' || ticket.source !== 'SOLUTION_FORM') return problem(409);
      tickets.push(ticket);
    }
    const now = new Date();
    const timestamp = now.toISOString();
    for (const ticket of tickets) {
      const range =
        MOCK_PROJECTS.find((p) => p.projectId === ticket.project.projectId)?.reviewRangeDays ?? 3;
      reviewStore.history.push({
        ticketId: ticket.ticketId,
        actorId: user.user.id,
        action: 'SEND_BACK',
        timestamp,
        beforeStatus: 'EXPIRED',
        afterStatus: 'PENDING_REVIEW',
        reason: null,
        comment: null,
      });
      ticket.status = 'PENDING_REVIEW';
      ticket.enteredQueueAt = timestamp;
      ticket.reviewDueDate = dueDate(now, range);
    }
    return HttpResponse.json({ count: tickets.length });
  }),
];
