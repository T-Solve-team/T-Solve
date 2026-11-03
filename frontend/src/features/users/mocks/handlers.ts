import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import {
  Role,
  UserAccountStatus,
  type UserListDepartmentRef,
  type UserAccount,
  type UserAccountPage,
  type UserCreationOptions,
} from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { isRole } from '@/shared/auth';
import { parseSort } from '@/shared/lib';
import type { MsgCode, MsgParams } from '@/shared/messages';
import { COMPANY_DOMAIN, DEPARTMENTS, SPACES, WORKSPACE_ID } from './data';
import { accountAccessError as accessError, resetUserState, store } from './state';
import { statusHandlers } from './status-handlers';
import { rolesHandler } from './roles-handler';
import { isRecord } from './handover';
import { listDepartmentId, listUserId } from './list-ids';

export function resetMockData(): void {
  resetUserState();
}

const SORT_VALUE: Record<string, (user: UserAccount) => string> = {
  fullName: (user) => user.fullName ?? '',
  email: (user) => user.email,
};

function nonNegativeInteger(value: string | null, fallback: number): number {
  if (value === null) return fallback;
  const parsed = Number(value);
  return /^[0-9]+$/.test(value) && Number.isSafeInteger(parsed) && parsed <= 2147483647
    ? parsed
    : NaN;
}

function fieldError(field: string, code: MsgCode, params?: MsgParams, status = 400) {
  return problem(status, code, { params, fieldErrors: [{ field, code, params }] });
}

function ids(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((id) => typeof id === 'string');
}

export const handlers: HttpHandler[] = [
  http.get('*/api/v1/users/creation-options', async () => {
    await delay(MOCK_DELAY_MS);
    return (
      accessError() ??
      HttpResponse.json<UserCreationOptions>({
        companyDomain: COMPANY_DOMAIN,
        spaces: SPACES,
        departments: DEPARTMENTS,
      })
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
    if (!isRecord(input)) return problem(400, 'MSG03');
    if (typeof input.email !== 'string' || !input.email.trim())
      return fieldError('email', 'MSG01', { field_name: 'Email' });
    const email = input.email.trim().toLowerCase();
    if (input.email.length > 100)
      return fieldError('email', 'MSG02', { field_name: 'Email', max_length: 100 });
    // MSG03 is the catalog's email validation message, including Workspace-domain mismatches.
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.split('@')[1] !== COMPANY_DOMAIN)
      return fieldError('email', 'MSG03');
    const duplicate = store.users.find(
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
        store.users.some(
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
    store.users.push({ ...user, workspaceId: WORKSPACE_ID, spaceIds: [...new Set(spaceIds)] });
    return HttpResponse.json<UserAccount>(user, { status: 201 });
  }),
  http.get('*/api/v1/users/departments', async () => {
    await delay(MOCK_DELAY_MS);
    return (
      accessError() ??
      HttpResponse.json<UserListDepartmentRef[]>(
        DEPARTMENTS.map((department) => ({
          ...department,
          departmentId: listDepartmentId(department.departmentId),
        })).sort((a, b) => a.name.localeCompare(b.name, 'vi')),
      )
    );
  }),
  http.get('*/api/v1/users', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const denied = accessError();
    if (denied) return denied;
    const params = new URL(request.url).searchParams;
    const q = (params.get('q') ?? '').trim().toLocaleLowerCase();
    if (q.length > 100) return fieldError('q', 'MSG02', { field_name: 'Search', max_length: 100 });
    const role = params.get('role');
    const status = params.get('status');
    const departmentId = params.get('departmentId');
    const expressions = params.getAll('sort');
    const sorts = (expressions.length ? expressions : ['fullName,asc']).map(parseSort);
    const page = nonNegativeInteger(params.get('page'), 0);
    const size = nonNegativeInteger(params.get('size'), 20);
    if (
      ['q', 'role', 'status', 'departmentId', 'page', 'size'].some(
        (key) => params.getAll(key).length > 1,
      ) ||
      expressions.some((expression) => !/^(fullName|email),(asc|desc)$/.test(expression)) ||
      sorts.some((sort) => !sort || !Object.hasOwn(SORT_VALUE, sort.field)) ||
      (role !== null && !isRole(role)) ||
      (status !== null && status !== 'ACTIVE' && status !== 'DEACTIVATED') ||
      (departmentId !== null &&
        (!/^[1-9][0-9]{0,18}$/.test(departmentId) ||
          BigInt(departmentId) > 9223372036854775807n)) ||
      !Number.isInteger(page) ||
      !Number.isInteger(size) ||
      size < 1 ||
      size > 100
    )
      return problem(400, 'MSG06');
    const matching = store.users
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
          (!departmentId ||
            (user.department && listDepartmentId(user.department.departmentId) === departmentId)),
      )
      .sort((a, b) => {
        for (const sort of sorts) {
          if (!sort) continue;
          const value = SORT_VALUE[sort.field];
          if (!value) continue;
          const comparison =
            value(a).localeCompare(value(b), 'vi') * (sort.order === 'asc' ? 1 : -1);
          if (comparison) return comparison;
        }
        const aId = BigInt(listUserId(a.id));
        const bId = BigInt(listUserId(b.id));
        return aId < bId ? -1 : aId > bId ? 1 : 0;
      });
    return HttpResponse.json<UserAccountPage>({
      content: matching.slice(page * size, (page + 1) * size).map((user) => ({
        id: listUserId(user.id),
        fullName: user.fullName,
        email: user.email,
        roles: user.roles,
        department: user.department
          ? { ...user.department, departmentId: listDepartmentId(user.department.departmentId) }
          : null,
        status: user.status,
      })),
      page: {
        number: page,
        size,
        totalElements: matching.length,
        totalPages: Math.ceil(matching.length / size),
      },
    });
  }),
  ...statusHandlers,
  rolesHandler,
];
