import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type { DepartmentRef, UserAccount, UserAccountPage } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { getDevUser } from '@/shared/auth';
import { parseSort } from '@/shared/lib';
import { DEPARTMENTS, seedUsers, WORKSPACE_ID } from './data';

let users = seedUsers();

export function resetMockData(): void {
  users = seedUsers();
}

function accessError() {
  const actor = getDevUser();
  if (!actor) return problem(401, 'MSG07');
  if (actor !== 'admin' && actor !== 'admin-tr') return problem(403, 'MSG08');
  return null;
}

const SORT_VALUE: Record<string, (user: UserAccount) => string> = {
  fullName: (user) => user.fullName ?? '',
  email: (user) => user.email,
  lastSignInAt: (user) => user.lastSignInAt ?? '',
};

function nonNegativeInteger(value: string | null, fallback: number): number {
  const parsed = Number(value);
  return value !== null && Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : fallback;
}

export const handlers: HttpHandler[] = [
  http.get('*/api/v1/users/departments', async () => {
    await delay(MOCK_DELAY_MS);
    return accessError() ?? HttpResponse.json<DepartmentRef[]>(DEPARTMENTS);
  }),
  http.get('*/api/v1/users', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const denied = accessError();
    if (denied) return denied;
    const params = new URL(request.url).searchParams;
    const q = (params.get('q') ?? '').trim().toLocaleLowerCase();
    if (q.length > 100)
      return problem(400, 'MSG02', { params: { field_name: 'Search', max_length: 100 } });
    const role = params.get('role');
    const status = params.get('status');
    const departmentId = params.get('departmentId');
    const sort = parseSort(params.get('sort')) ?? { field: 'fullName', order: 'asc' };
    const value = Object.hasOwn(SORT_VALUE, sort.field)
      ? SORT_VALUE[sort.field]
      : SORT_VALUE.fullName;
    const direction = sort.order === 'asc' ? 1 : -1;
    const matching = users
      .filter(
        (user) =>
          user.workspaceId === WORKSPACE_ID &&
          (!q ||
            [user.fullName, user.email].some((text) => text?.toLocaleLowerCase().includes(q))) &&
          (!role ||
            (role === 'EMPLOYEE'
              ? user.roles.length === 1 && user.roles[0] === 'EMPLOYEE'
              : user.roles.some((held) => held === role))) &&
          (!status || user.status === status) &&
          (!departmentId || String(user.department?.departmentId) === departmentId),
      )
      .sort(
        (a, b) =>
          (value ? value(a).localeCompare(value(b), 'vi') : 0) * direction ||
          a.id.localeCompare(b.id),
      );
    const size = Math.min(100, Math.max(1, nonNegativeInteger(params.get('size'), 20)));
    const page = nonNegativeInteger(params.get('page'), 0);
    return HttpResponse.json<UserAccountPage>({
      content: matching
        .slice(page * size, (page + 1) * size)
        .map(({ workspaceId: _workspaceId, ...user }) => user),
      page: {
        number: page,
        size,
        totalElements: matching.length,
        totalPages: Math.ceil(matching.length / size),
      },
    });
  }),
];
