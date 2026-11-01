import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type { TicketFeedbackRequest } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';

const COMMENT_MAX = 500;

/**
 * The Published tickets that accept Feedback: the 14 ids of features/search/mocks/search-data.ts, copied because
 * a feature may not import another feature's mocks (design "Data Model"). Keep them in step with it.
 */
const PUBLISHED_TICKET_IDS = new Set(
  Array.from(
    { length: 14 },
    (_, index) => `5ea70000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
  ),
);

/** The signed-in user's Feedback per ticket; a new one replaces the old one (UC-28). */
let feedbackByTicket = new Map<string, TicketFeedbackRequest>();

/** Forgets every stored Feedback — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  feedbackByTicket = new Map();
}

export const handlers: HttpHandler[] = [
  http.put('*/api/v1/tickets/:ticketId/feedback', async ({ request, params }) => {
    await delay(MOCK_DELAY_MS);
    const ticketId = String(params.ticketId);
    if (!PUBLISHED_TICKET_IDS.has(ticketId)) return problem(404);
    const body = (await request.json()) as Partial<TicketFeedbackRequest>;
    if (typeof body.isHelpful !== 'boolean') {
      return problem(400, 'MSG01', {
        fieldErrors: [{ field: 'isHelpful', code: 'MSG01', params: { field_name: 'Helpful?' } }],
      });
    }
    if ((body.comment?.length ?? 0) > COMMENT_MAX) {
      return problem(400, 'MSG02', {
        fieldErrors: [
          {
            field: 'comment',
            code: 'MSG02',
            params: { field_name: 'Comment', max_length: COMMENT_MAX },
          },
        ],
      });
    }
    feedbackByTicket.set(ticketId, { isHelpful: body.isHelpful, comment: body.comment ?? null });
    return new HttpResponse(null, { status: 204 });
  }),
];
