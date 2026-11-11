import dayjs from 'dayjs';
import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type { DashboardOptions, KnowledgeDashboard } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { getDevUser } from '@/shared/auth';
import { dashboardRange, presetRange } from '../hooks/useDashboardFilters';

const IT = '00000000-0000-4000-8000-0000000000d1';
const HR = '00000000-0000-4000-8000-0000000000d2';
const OPTIONS: DashboardOptions = {
  departments: [
    { departmentId: IT, name: 'IT' },
    { departmentId: HR, name: 'HR' },
  ],
  projects: [
    { projectId: '00000000-0000-4000-8000-000000000101', departmentId: IT, name: 'ITSUP' },
    { projectId: '00000000-0000-4000-8000-000000000102', departmentId: IT, name: 'ITNET' },
    { projectId: '00000000-0000-4000-8000-000000000201', departmentId: HR, name: 'HRHELP' },
  ],
};

export const handlers: HttpHandler[] = [
  http.get('*/api/v1/dashboard', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const user = getDevUser();
    if (!user) return problem(401, 'MSG07');
    const isAdmin = user === 'admin' || user === 'admin-tr';
    if (!isAdmin && user !== 'pm' && user !== 'pm-km') return problem(403, 'MSG08');
    const search = new URL(request.url).searchParams;
    const period =
      search.has('from') || search.has('to')
        ? { from: search.get('from'), to: search.get('to') }
        : presetRange('30d');
    if (!dashboardRange(period.from, period.to) || !period.from || !period.to)
      return problem(400, 'MSG39');
    const ownDepartment = user === 'pm-km' ? HR : IT;
    const departmentId = search.get('departmentId') || (isAdmin ? undefined : ownDepartment);
    const projectId = search.get('projectId');
    if (!isAdmin && departmentId !== ownDepartment) return problem(403, 'MSG08');
    const options = {
      departments: OPTIONS.departments.filter(
        (item) => isAdmin || item.departmentId === ownDepartment,
      ),
      projects: OPTIONS.projects.filter((item) => isAdmin || item.departmentId === ownDepartment),
    };
    const project = OPTIONS.projects.find((item) => item.projectId === projectId);
    if (projectId && !project) return problem(404, 'MSG04');
    if (project && !isAdmin && project.departmentId !== ownDepartment) return problem(403, 'MSG08');
    if (project && departmentId && project.departmentId !== departmentId)
      return problem(400, 'MSG06');
    const projects = options.projects.filter(
      (item) =>
        (!departmentId || item.departmentId === departmentId) &&
        (!projectId || item.projectId === projectId),
    );
    const days = dayjs(period.to).diff(dayjs(period.from), 'day') + 1;
    const perDay = Array.from({ length: days }, (_, index) => ({
      date: dayjs(period.from).add(index, 'day').format('YYYY-MM-DD'),
      approved: projects.length * (index % 3),
      rejected: projects.length * (index % 2),
      changesRequested: projects.length,
    }));
    const totals = perDay.reduce(
      (sum, day) => ({
        approved: sum.approved + day.approved,
        rejected: sum.rejected + day.rejected,
        changesRequested: sum.changesRequested + day.changesRequested,
      }),
      { approved: 0, rejected: 0, changesRequested: 0 },
    );
    const decisions = totals.approved + totals.rejected + totals.changesRequested;
    const perDepartment = options.departments
      .filter(
        (item) =>
          (!departmentId || item.departmentId === departmentId) &&
          (!project || item.departmentId === project.departmentId),
      )
      .map((item) => {
        const count = projects.filter((entry) => entry.departmentId === item.departmentId).length;
        return {
          ...item,
          publishedTickets: count * 24,
          allTickets: count * 40,
          publishedShare: count ? 0.6 : null,
        };
      });
    const count = projects.length;
    return HttpResponse.json<KnowledgeDashboard>({
      period: { from: period.from, to: period.to },
      options,
      outcomes: {
        ...totals,
        approvalRate: decisions ? totals.approved / decisions : null,
        pendingReview: count * 6,
        expired: count * 2,
        perDay,
        perProject: projects.map((item) => ({
          ...item,
          approved: totals.approved / count,
          rejected: totals.rejected / count,
          changesRequested: totals.changesRequested / count,
        })),
      },
      coverage: {
        publishedTickets: count * 24,
        allTickets: count * 40,
        publishedShare: count ? 0.6 : null,
        perDepartment,
      },
      feedback: {
        helpful: count * 9,
        notHelpful: count * 3,
        helpfulRate: count ? 0.75 : null,
        topRated: count
          ? [
              {
                ticketId: '00000000-0000-4000-8000-000000000501',
                title: 'Restore access to the shared drive',
                helpful: 7,
                notHelpful: 0,
                helpfulRate: 1,
              },
            ]
          : [],
        lowestRated: count
          ? [
              {
                ticketId: '00000000-0000-4000-8000-000000000502',
                title: 'Reset an expired VPN certificate',
                helpful: 2,
                notHelpful: 3,
                helpfulRate: 0.4,
              },
            ]
          : [],
      },
      // Gaps use the Department scope even when a Project is selected.
      gaps: {
        noResultSearches: [{ query: 'VPN certificate renewal', count: departmentId ? 3 : 6 }],
        chatbotNoSource: departmentId ? 4 : 8,
        chatbotQuestions: [
          { question: 'How do I renew my VPN certificate?', count: departmentId ? 4 : 8 },
        ],
      },
      suggestions: { posted: count * 10, solved: count * 7, solvedShare: count ? 0.7 : null },
    });
  }),
];

/** Responses are rebuilt per request; there is no mutable mock data to restore. */
export function resetMockData(): void {}
