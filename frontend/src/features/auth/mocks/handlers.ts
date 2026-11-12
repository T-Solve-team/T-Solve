import { http, HttpResponse, type HttpHandler } from 'msw';
import { problem } from '@/shared/api/mocks';

/** The one Jira link request the mock knows (AC-001.75): any other id is MSG141. */
export const MOCK_JIRA_LINK = {
  requestId: 'Ab-_9xYz0123456789abcdefghijKLMNOPQRSTuvwxy', // 43 characters: the shape the backend issues
  jiraUserName: 'Nguyen Van A',
  jiraSiteName: 'acme-corp',
} as const;

/** What the mock saw on `POST /api/v1/auth/jira-link/confirm`: the body and the CSRF header of each call. */
export type JiraLinkConfirmCall = { body: unknown; csrfHeader: string | null };
export const jiraLinkConfirmCalls: JiraLinkConfirmCall[] = [];

/**
 * MSW mock handlers of the "auth" feature (guideline 10 §5). Hand-written handlers with realistic data go here;
 * endpoints without one fall back to orval's generated fake-data handlers (src/mocks/handlers.ts).
 */
export const handlers: HttpHandler[] = [
  http.get('*/api/v1/auth/jira-link/:requestId', ({ params }) =>
    params.requestId === MOCK_JIRA_LINK.requestId
      ? HttpResponse.json({
          jiraUserName: MOCK_JIRA_LINK.jiraUserName,
          jiraSiteName: MOCK_JIRA_LINK.jiraSiteName,
        })
      : problem(400, 'MSG141'),
  ),
  http.post('*/api/v1/auth/jira-link/confirm', async ({ request }) => {
    jiraLinkConfirmCalls.push({
      body: await request.json(),
      csrfHeader: request.headers.get('X-XSRF-TOKEN'),
    });
    return HttpResponse.json({ code: 'MSG75' });
  }),
];

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  jiraLinkConfirmCalls.length = 0;
}
