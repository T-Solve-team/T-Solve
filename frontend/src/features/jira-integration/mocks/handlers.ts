import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type {
  MemberCandidateResponse,
  ProjectMemberResponse,
  ProjectSummaryResponse,
} from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { initialMembers, PROJECTS, TICKET_RESOLVERS } from './data';

let members = initialMembers();

/** Full name ascending ignoring case, without a full name last (spec AC-001.2, AC-002.1). */
function byName(a: { fullName: string | null }, b: { fullName: string | null }): number {
  if (a.fullName === null || b.fullName === null) return a.fullName === null ? 1 : -1;
  return a.fullName.localeCompare(b.fullName, 'en', { sensitivity: 'base' });
}

function sorted(list: ProjectMemberResponse[]): ProjectMemberResponse[] {
  const manager = list.filter((member) => member.role === 'PROJECT_MANAGER');
  return [...manager, ...list.filter((member) => member.role === 'MEMBER').sort(byName)];
}

/** Unknown Project → 404 MSG04, like the backend (AC-001.4). */
const notFound = () => problem(404, 'MSG04');

export const handlers: HttpHandler[] = [
  http.get<{ projectId: string }>('*/api/v1/projects/:projectId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const project = PROJECTS.find((item) => item.id === params.projectId);
    if (!project) return notFound();
    return HttpResponse.json<ProjectSummaryResponse>(project);
  }),
  http.get<{ projectId: string }>('*/api/v1/projects/:projectId/members', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const list = members[params.projectId];
    if (!list) return notFound();
    return HttpResponse.json<ProjectMemberResponse[]>(sorted(list));
  }),
  http.get<{ projectId: string }>(
    '*/api/v1/projects/:projectId/member-candidates',
    async ({ params, request }) => {
      await delay(MOCK_DELAY_MS);
      const list = members[params.projectId];
      if (!list) return notFound();
      const q = (new URL(request.url).searchParams.get('q') ?? '').trim().toLowerCase();
      if (q.length > 100) return problem(400, 'MSG06');
      const candidates = TICKET_RESOLVERS.filter(
        (account) =>
          !list.some((member) => member.userId === account.userId) &&
          [account.fullName ?? '', account.email].some((text) => text.toLowerCase().includes(q)),
      );
      return HttpResponse.json<MemberCandidateResponse[]>(candidates.sort(byName).slice(0, 20));
    },
  ),
  http.post<{ projectId: string }>(
    '*/api/v1/projects/:projectId/members',
    async ({ params, request }) => {
      await delay(MOCK_DELAY_MS);
      const list = members[params.projectId];
      const project = PROJECTS.find((item) => item.id === params.projectId);
      if (!list || !project) return notFound();
      const input: unknown = await request.json().catch(() => null);
      const userIds =
        typeof input === 'object' && input !== null && 'userIds' in input ? input.userIds : null;
      if (
        !Array.isArray(userIds) ||
        userIds.length < 1 ||
        userIds.length > 50 ||
        new Set(userIds).size !== userIds.length
      )
        return problem(400, 'MSG06');
      if (project.status === 'REMOVED') return problem(409, 'MSG06');
      const accounts = userIds.map((id) =>
        TICKET_RESOLVERS.find(
          (account) =>
            account.userId === id && !list.some((member) => member.userId === account.userId),
        ),
      );
      if (accounts.some((account) => account === undefined)) return problem(409, 'MSG06');
      const dateAdded = new Date().toISOString();
      const added = accounts.flatMap((account) =>
        account ? [{ ...account, role: 'MEMBER' as const, dateAdded }] : [],
      );
      const updated = sorted([...list, ...added]);
      members = { ...members, [params.projectId]: updated };
      return HttpResponse.json<ProjectMemberResponse[]>(updated, { status: 201 });
    },
  ),
];

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  members = initialMembers();
}
