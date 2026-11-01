import { delay, http, HttpResponse } from 'msw';
import { getMe, isApiError, type ReviewHistoryItem } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { historyDateRange, isReviewDecision } from '../hooks/history-filters';
import { MOCK_PROJECTS, REVIEW_WORKSPACE_ID } from './data';
import { reviewStore } from './state';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const historyHandlers = [
  http.get('*/api/v1/review-history', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    let user;
    try {
      user = await getMe();
    } catch (error) {
      return isApiError(error) ? problem(error.status, error.code) : problem(401, 'MSG07');
    }
    if (!user.roles.includes('PROJECT_MANAGER')) return problem(403, 'MSG08');
    const params = new URL(request.url).searchParams;
    const range = historyDateRange(params.get('dateFrom'), params.get('dateTo'));
    if (!range.valid) return problem(400, 'MSG39');
    const departmentId = params.get('departmentId');
    const decision = params.get('decision');
    const sort = params.get('sort') ?? 'timestamp,desc';
    if (
      (departmentId !== null && !UUID.test(departmentId)) ||
      (decision !== null && !isReviewDecision(decision)) ||
      !['timestamp,asc', 'timestamp,desc'].includes(sort)
    )
      return problem(400, 'MSG06');
    const rawPage = params.get('page');
    const rawSize = params.get('size');
    const page = rawPage === null ? 0 : Number(rawPage);
    const size = rawSize === null ? 20 : Number(rawSize);
    if (
      !Number.isSafeInteger(page) ||
      page < 0 ||
      !Number.isSafeInteger(size) ||
      size < 1 ||
      size > 100
    )
      return problem(400, 'MSG06');
    const rows: ReviewHistoryItem[] = [];
    for (const entry of reviewStore.history) {
      const ticket = reviewStore.tickets.find(
        (t) => t.ticketId === entry.ticketId && t.workspaceId === REVIEW_WORKSPACE_ID,
      );
      if (
        !ticket ||
        !user.projects.some(
          (p) => p.projectId === ticket.project.projectId && p.roleInProject === 'PROJECT_MANAGER',
        )
      )
        continue;
      if (
        departmentId &&
        MOCK_PROJECTS.find((p) => p.projectId === ticket.project.projectId)?.departmentId !==
          departmentId
      )
        continue;
      if (decision && entry.action !== decision) continue;
      const time = Date.parse(entry.timestamp);
      if (
        (range.start !== undefined && time < range.start) ||
        (range.end !== undefined && time >= range.end)
      )
        continue;
      rows.push({
        decisionId: entry.decisionId,
        ticketId: ticket.ticketId,
        sourceTicketId: ticket.sourceTicketId,
        title: ticket.title,
        project: ticket.project,
        decision: entry.action,
        actorId: entry.actorId,
        actorName: entry.actorName,
        authorName: ticket.authorName,
        timestamp: entry.timestamp,
        reason: entry.reason,
        comment: entry.comment,
      });
    }
    const direction = sort.endsWith(',asc') ? 1 : -1;
    rows.sort(
      (a, b) =>
        (Date.parse(a.timestamp) - Date.parse(b.timestamp)) * direction ||
        a.decisionId.localeCompare(b.decisionId),
    );
    return HttpResponse.json({
      content: rows.slice(page * size, (page + 1) * size),
      page: {
        number: page,
        size,
        totalElements: rows.length,
        totalPages: Math.ceil(rows.length / size),
      },
    });
  }),
];
