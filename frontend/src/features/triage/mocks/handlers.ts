import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import {
  getMe,
  isApiError,
  type CurrentUser,
  type SuggestionResultDetail,
  type SuggestionResultPage,
  type SuggestionResultSummary,
} from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { getDevUser } from '@/shared/auth';
import { historyDateRange } from '../hooks/history-filters';
import { MOCK_SUGGESTION_RESULTS, MOCK_WORKSPACE, type MockSuggestionResult } from './data';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ANSWERS = new Set(['YES', 'NO', 'NONE']);

/** Use the live /me seam so role and Project changes made in mock mode apply immediately. */
async function projectManager(): Promise<CurrentUser | ReturnType<typeof problem>> {
  if (!getDevUser()) return problem(401, 'MSG07');
  try {
    const user = await getMe();
    return user.roles.includes('PROJECT_MANAGER') ? user : problem(403, 'MSG08');
  } catch (error) {
    return isApiError(error) ? problem(error.status, error.code) : problem(401, 'MSG07');
  }
}

function isUser(value: CurrentUser | ReturnType<typeof problem>): value is CurrentUser {
  return 'projects' in value;
}

function managedIds(user: CurrentUser): Set<string> {
  return new Set(
    user.projects
      .filter((project) => project.roleInProject === 'PROJECT_MANAGER')
      .map((project) => project.projectId),
  );
}

function summary({
  workspaceId: _workspaceId,
  suggestedSolution: _suggestedSolution,
  wikiSources: _wikiSources,
  ...row
}: MockSuggestionResult): SuggestionResultSummary {
  return row;
}

function detail({
  workspaceId: _workspaceId,
  ...row
}: MockSuggestionResult): SuggestionResultDetail {
  return row;
}

function integer(
  value: string | null,
  fallback: number,
  minimum: number,
  maximum: number,
): number | null {
  if (value === null) return fallback;
  const parsed = Number(value);
  return value.trim() !== '' &&
    Number.isSafeInteger(parsed) &&
    parsed >= minimum &&
    parsed <= maximum
    ? parsed
    : null;
}

/** Read-only mock of the proposed history and detail endpoints. */
export const handlers: HttpHandler[] = [
  http.get('*/api/v1/suggestion-results', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const user = await projectManager();
    if (!isUser(user)) return user;
    const query = new URL(request.url).searchParams;
    const range = historyDateRange(query.get('from'), query.get('to'));
    if (!range.valid) return problem(400, 'MSG39');
    const projectId = query.get('projectId');
    if (projectId !== null && !UUID.test(projectId)) return problem(400, 'MSG06');
    const answer = query.get('answer');
    if (answer !== null && !ANSWERS.has(answer)) return problem(400, 'MSG06');
    const page = integer(query.get('page'), 0, 0, Number.MAX_SAFE_INTEGER);
    const size = integer(query.get('size'), 20, 1, 100);
    if (page === null || size === null) return problem(400, 'MSG06');
    const sort = query.get('sort');
    if (sort !== null && sort !== 'createdAt,desc') return problem(400, 'MSG06');
    const managed = managedIds(user);
    if (projectId !== null && !managed.has(projectId)) return problem(403, 'MSG08');
    const rows = MOCK_SUGGESTION_RESULTS.filter((row) => {
      const timestamp = Date.parse(row.createdAt);
      return (
        row.workspaceId === MOCK_WORKSPACE &&
        managed.has(row.project.projectId) &&
        (projectId === null || row.project.projectId === projectId) &&
        (answer === null ||
          (answer === 'NONE' ? row.reporterAnswer === null : row.reporterAnswer === answer)) &&
        (range.start === undefined || timestamp >= range.start) &&
        (range.end === undefined || timestamp < range.end)
      );
    }).sort(
      (a, b) => b.createdAt.localeCompare(a.createdAt) || a.issueKey.localeCompare(b.issueKey),
    );
    return HttpResponse.json<SuggestionResultPage>({
      content: rows.slice(page * size, (page + 1) * size).map(summary),
      page: {
        size,
        number: page,
        totalElements: rows.length,
        totalPages: Math.ceil(rows.length / size),
      },
    });
  }),
  http.get('*/api/v1/suggestion-results/:issueKey', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const user = await projectManager();
    if (!isUser(user)) return user;
    const issueKey = params.issueKey;
    const managed = managedIds(user);
    const row = MOCK_SUGGESTION_RESULTS.find(
      (entry) =>
        entry.workspaceId === MOCK_WORKSPACE &&
        entry.issueKey === issueKey &&
        managed.has(entry.project.projectId),
    );
    return row ? HttpResponse.json<SuggestionResultDetail>(detail(row)) : problem(404, 'MSG04');
  }),
];

/** This read-only mock has no mutable state to reset. */
export function resetMockData(): void {}
