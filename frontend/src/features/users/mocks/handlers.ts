import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import {
  Role,
  UserAccountStatus,
  type DepartmentRef,
  type UserAccount,
  type UserAccountPage,
  type UserCreationOptions,
} from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { getDevUser, isRole } from '@/shared/auth';
import { parseSort } from '@/shared/lib';
import type { MsgCode, MsgParams } from '@/shared/messages';
import { COMPANY_DOMAIN, DEPARTMENTS, seedUsers, SPACES, WORKSPACE_ID } from './data';

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

function fieldError(field: string, code: MsgCode, params?: MsgParams, status = 400) {
  return problem(status, code, { params, fieldErrors: [{ field, code, params }] });
}

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function ids(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((id) => typeof id === 'string');
}

export const handlers: HttpHandler[] = [
  http.get('*/api/v1/users/creation-options', async () => {
    await delay(MOCK_DELAY_MS);
    return (
      accessError() ??
      HttpResponse.json<UserCreationOptions>({ companyDomain: COMPANY_DOMAIN, spaces: SPACES })
    );
  }),
  http.post('*/api/v1/users', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const denied = accessError();
    if (denied) return denied;
    let input: unknown;
    try {
      input = await request.json();
    } catch {
      return problem(400, 'MSG03');
    }
    if (!record(input)) return problem(400, 'MSG03');
    if (typeof input.email !== 'string' || !input.email.trim())
      return fieldError('email', 'MSG01', { field_name: 'Email' });
    const email = input.email.trim().toLowerCase();
    if (input.email.length > 100)
      return fieldError('email', 'MSG02', { field_name: 'Email', max_length: 100 });
    // MSG03 is the catalog's email validation message, including Workspace-domain mismatches.
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.split('@')[1] !== COMPANY_DOMAIN)
      return fieldError('email', 'MSG03');
    const duplicate = users.find(
      (user) => user.workspaceId === WORKSPACE_ID && user.email.toLowerCase() === email,
    );
    if (duplicate)
      return fieldError(
        'email',
        duplicate.status === UserAccountStatus.DEACTIVATED ? 'MSG111' : 'MSG11',
        undefined,
        409,
      );
    if (
      !Array.isArray(input.roles) ||
      !input.roles.every((role: unknown) => typeof role === 'string' && isRole(role))
    )
      return fieldError('roles', 'MSG01', { field_name: 'Roles' });
    const roles = [...new Set([Role.EMPLOYEE, ...input.roles.filter(isRole)])];
    const department = DEPARTMENTS.find((item) => item.departmentId === input.departmentId) ?? null;
    if (
      (input.departmentId !== undefined && input.departmentId !== null && !department) ||
      (roles.includes(Role.PROJECT_MANAGER) && !department)
    )
      return fieldError('departmentId', 'MSG01', { field_name: 'Department' });
    const spaceIds: unknown = input.spaceIds ?? [];
    if (
      !ids(spaceIds) ||
      spaceIds.some((id) => !SPACES.some((space) => space.spaceId === id)) ||
      (roles.includes(Role.KNOWLEDGE_MANAGER) && spaceIds.length === 0) ||
      (!roles.includes(Role.KNOWLEDGE_MANAGER) && spaceIds.length > 0)
    )
      return fieldError('spaceIds', 'MSG01', { field_name: 'Spaces' });
    const occupied = SPACES.find(
      (space) =>
        spaceIds.includes(space.spaceId) &&
        users.some(
          (user) => user.workspaceId === WORKSPACE_ID && user.spaceIds.includes(space.spaceId),
        ),
    );
    if (occupied) return fieldError('spaceIds', 'MSG117', { space_name: occupied.name }, 409);
    const user: UserAccount = {
      id: crypto.randomUUID(),
      fullName: null,
      email,
      roles,
      department,
      status: UserAccountStatus.ACTIVE,
      lastSignInAt: null,
    };
    users.push({ ...user, workspaceId: WORKSPACE_ID, spaceIds: [...new Set(spaceIds)] });
    return HttpResponse.json<UserAccount>(user, { status: 201 });
  }),
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
            (role === Role.EMPLOYEE
              ? user.roles.length === 1 && user.roles[0] === Role.EMPLOYEE
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
        .map(({ workspaceId: _workspaceId, spaceIds: _spaceIds, ...user }) => user),
      page: {
        number: page,
        size,
        totalElements: matching.length,
        totalPages: Math.ceil(matching.length / size),
      },
    });
  }),
];
