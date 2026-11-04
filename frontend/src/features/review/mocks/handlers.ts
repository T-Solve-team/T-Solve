import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import {
  getMe,
  isApiError,
  type CurrentUser,
  type ReviewQueueItem,
  type ReviewTicketDetail,
} from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { parseSort } from '@/shared/lib';
import { MAX_REVIEW_SELECTION } from '../review-limits';
import { departmentOf, REVIEW_WORKSPACE_ID, type MockReviewTicket } from './data';
import { resetReviewState, reviewStore, type ReviewHistoryEntry } from './state';
import { expiredHandlers } from './expired-handlers';
import { historyHandlers } from './history-handlers';

export const resetMockData = resetReviewState;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** The existing /me seam applies live mock role, status and assignment changes. */
async function actor(): Promise<CurrentUser | ReturnType<typeof problem>> {
  try {
    return await getMe();
  } catch (error) {
    return isApiError(error) ? problem(error.status, error.code) : problem(401, 'MSG07');
  }
}

function manages(user: CurrentUser, ticket: MockReviewTicket): boolean {
  return (
    user.roles.includes('PROJECT_MANAGER') &&
    user.projects.some(
      (project) =>
        project.projectId === ticket.project.projectId &&
        project.roleInProject === 'PROJECT_MANAGER',
    )
  );
}

function pendingQueue(user: CurrentUser, url: URL): ReviewQueueItem[] {
  const departmentId = url.searchParams.get('departmentId');
  return reviewStore.tickets
    .filter(
      (ticket) =>
        ticket.workspaceId === REVIEW_WORKSPACE_ID &&
        manages(user, ticket) &&
        ticket.status === 'PENDING_REVIEW' &&
        ticket.source === 'SOLUTION_FORM' &&
        (!departmentId ||
          departmentOf({ ...ticket, reviewDueDate: ticket.reviewDueDate ?? '' }) === departmentId),
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
    }));
}

const SORT_VALUE: Record<string, (t: ReviewQueueItem) => string> = {
  sourceTicketId: (t) => t.sourceTicketId,
  title: (t) => t.title,
  project: (t) => t.project.name,
  authorName: (t) => t.authorName ?? '',
  enteredQueueAt: (t) => t.enteredQueueAt,
  reviewDueDate: (t) => t.reviewDueDate,
};

function sorted(tickets: ReviewQueueItem[], url: URL): ReviewQueueItem[] {
  const sort = parseSort(url.searchParams.get('sort')) ?? { field: 'reviewDueDate', order: 'asc' };
  const value = SORT_VALUE[sort.field] ?? SORT_VALUE.reviewDueDate;
  const direction = sort.order === 'asc' ? 1 : -1;
  return [...tickets].sort(
    (a, b) =>
      (value ? value(a).localeCompare(value(b)) : 0) * direction ||
      a.ticketId.localeCompare(b.ticketId),
  );
}

function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function validIds(value: unknown): value is string[] {
  return (
    Array.isArray(value) &&
    value.length > 0 &&
    value.length <= MAX_REVIEW_SELECTION &&
    value.every((id) => typeof id === 'string' && UUID.test(id)) &&
    new Set(value).size === value.length
  );
}

async function decide(
  request: Request,
  action: Extract<ReviewHistoryEntry['action'], 'APPROVE' | 'REJECT' | 'REQUEST_CHANGES'>,
) {
  await delay(MOCK_DELAY_MS);
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return problem(400);
  }
  if (!record(body) || !validIds(body.ticketIds)) return problem(400);
  const field = action === 'REJECT' ? 'reason' : action === 'REQUEST_CHANGES' ? 'comment' : null;
  if (field) {
    const text = body[field];
    const params = { field_name: field === 'reason' ? 'Reason' : 'Comment', max_length: 1000 };
    if (typeof text !== 'string' || text.trim() === '') {
      return problem(400, 'MSG01', { fieldErrors: [{ field, code: 'MSG01', params }] });
    }
    if (text.length > 1000)
      return problem(400, 'MSG02', { fieldErrors: [{ field, code: 'MSG02', params }] });
  }
  const user = await actor();
  if (user instanceof HttpResponse) return user;
  if (!user.roles.includes('PROJECT_MANAGER')) return problem(403, 'MSG08');
  const tickets: MockReviewTicket[] = [];
  for (const id of body.ticketIds) {
    const ticket = reviewStore.tickets.find(
      (item) => item.ticketId === id && item.workspaceId === REVIEW_WORKSPACE_ID,
    );
    if (!ticket) return problem(404);
    if (!manages(user, ticket)) return problem(403, 'MSG08');
    tickets.push(ticket);
  }
  if (action === 'REQUEST_CHANGES' && tickets.some((ticket) => ticket.source !== 'SOLUTION_FORM'))
    return problem(400, 'MSG18');
  if (tickets.some((ticket) => ticket.status !== 'PENDING_REVIEW')) return problem(409, 'MSG06');
  const afterStatus =
    action === 'APPROVE' ? 'PUBLISHED' : action === 'REJECT' ? 'REJECTED' : 'CHANGES_REQUESTED';
  const timestamp = new Date().toISOString();
  for (const ticket of tickets) {
    reviewStore.history.push({
      decisionId: crypto.randomUUID(),
      ticketId: ticket.ticketId,
      actorId: user.user.id,
      actorName: user.user.fullName || user.user.email,
      action,
      timestamp,
      beforeStatus: ticket.status,
      afterStatus,
      reason: typeof body.reason === 'string' && action === 'REJECT' ? body.reason : null,
      comment:
        typeof body.comment === 'string' && action === 'REQUEST_CHANGES' ? body.comment : null,
    });
    ticket.status = afterStatus;
    if (action === 'REQUEST_CHANGES' && typeof body.comment === 'string')
      ticket.lastReviewComment = body.comment;
  }
  return HttpResponse.json({ count: tickets.length });
}

export const handlers: HttpHandler[] = [
  ...historyHandlers,
  ...expiredHandlers,
  http.get('*/api/v1/review-queue', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const user = await actor();
    if (user instanceof HttpResponse) return user;
    if (!user.roles.includes('PROJECT_MANAGER')) return problem(403, 'MSG08');
    const url = new URL(request.url);
    const size = Math.max(1, Number(url.searchParams.get('size')) || 20);
    const page = Math.max(0, Number(url.searchParams.get('page')) || 0);
    const all = sorted(pendingQueue(user, url), url);
    return HttpResponse.json({
      content: all.slice(page * size, page * size + size),
      page: {
        size,
        number: page,
        totalElements: all.length,
        totalPages: Math.ceil(all.length / size),
      },
    });
  }),
  http.get('*/api/v1/review-queue/ticket-ids', async ({ request }) => {
    const user = await actor();
    if (user instanceof HttpResponse) return user;
    if (!user.roles.includes('PROJECT_MANAGER')) return problem(403, 'MSG08');
    const url = new URL(request.url);
    return HttpResponse.json({
      ticketIds: sorted(pendingQueue(user, url), url).map((ticket) => ticket.ticketId),
    });
  }),
  http.get('*/api/v1/tickets/:ticketId', async ({ params }) => {
    const id = params.ticketId;
    if (typeof id !== 'string' || !UUID.test(id)) return problem(400);
    const user = await actor();
    if (user instanceof HttpResponse) return user;
    const ticket = reviewStore.tickets.find(
      (item) => item.ticketId === id && item.workspaceId === REVIEW_WORKSPACE_ID,
    );
    if (!ticket) return problem(404, 'MSG04');
    if (ticket.status !== 'PUBLISHED' && ticket.authorId !== user.user.id && !manages(user, ticket))
      return problem(404, 'MSG04');
    const {
      workspaceId: _workspaceId,
      enteredQueueAt: _enteredQueueAt,
      jiraStatus: _jiraStatus,
      ...detail
    } = ticket;
    return HttpResponse.json<ReviewTicketDetail>(detail);
  }),
  http.post('*/api/v1/reviews/approve', ({ request }) => decide(request, 'APPROVE')),
  http.post('*/api/v1/reviews/reject', ({ request }) => decide(request, 'REJECT')),
  http.post('*/api/v1/reviews/request-changes', ({ request }) =>
    decide(request, 'REQUEST_CHANGES'),
  ),
];
