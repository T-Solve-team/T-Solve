import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type {
  DepartmentDetail,
  DepartmentProjectOption,
  DepartmentRef,
  WorkspaceInfo,
} from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { getDevUser } from '@/shared/auth';
import type { MsgCode, MsgParams } from '@/shared/messages';

let workspace: WorkspaceInfo | undefined;

const FIELDS = [
  ['companyName', 'Company name', 100],
  ['jiraSiteUrl', 'Jira site address', 200],
] as const;

function accessError() {
  const user = getDevUser();
  if (!user) return problem(401, 'MSG07');
  if (user !== 'admin' && user !== 'admin-tr') return problem(403, 'MSG08');
  return undefined;
}

function fieldError(field: string, code: MsgCode, params?: MsgParams) {
  return problem(400, code, { params, fieldErrors: [{ field, code, params }] });
}

function isRecord(input: unknown): input is Record<string, unknown> {
  return typeof input === 'object' && input !== null && !Array.isArray(input);
}

function validate(input: Record<string, unknown>, isSetup: boolean) {
  for (const [field, label, max] of FIELDS) {
    if (!isSetup && field === 'jiraSiteUrl') continue;
    const value = input[field];
    if (typeof value !== 'string' || !value.trim())
      return fieldError(field, 'MSG01', { field_name: label });
    if (value.length > max)
      return fieldError(field, 'MSG02', { field_name: label, max_length: max });
  }
  if (
    isSetup &&
    (typeof input.jiraSiteUrl !== 'string' ||
      !/^https:\/\/[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.atlassian\.net\/?$/i.test(
        input.jiraSiteUrl.trim(),
      ))
  )
    return fieldError('jiraSiteUrl', 'MSG61');
  return undefined;
}

type MockDepartment = DepartmentRef & {
  defaultProjectId: string | null;
  projects: DepartmentProjectOption[];
};

// Copies of the users mock's IT and HR (users/mocks/data.ts), so 2.3 opens from 2.2 in mock mode (K121-D18).
// IT has two Active Projects to choose from; HR has none.
const seed = (): MockDepartment[] => [
  {
    departmentId: '00000000-0000-4000-8000-0000000000d1',
    name: 'IT',
    defaultProjectId: null,
    projects: [
      { projectId: '00000000-0000-4000-8000-0000000000e1', jiraProjectKey: 'OPS' },
      { projectId: '00000000-0000-4000-8000-0000000000e2', jiraProjectKey: 'SUP' },
    ],
  },
  {
    departmentId: '00000000-0000-4000-8000-0000000000d2',
    name: 'HR',
    defaultProjectId: null,
    projects: [],
  },
];
let departments: MockDepartment[] = seed();

const detail = ({
  departmentId,
  name,
  defaultProjectId,
  projects,
}: MockDepartment): DepartmentDetail => ({
  departmentId,
  name,
  defaultProject: projects.find((p) => p.projectId === defaultProjectId) ?? null,
  projects,
});

function departmentNameError(code: MsgCode, params?: MsgParams, status = 400) {
  return problem(status, code, { params, fieldErrors: [{ field: 'name', code, params }] });
}

function nameOf(input: unknown): unknown {
  return typeof input === 'object' && input !== null && 'name' in input ? input.name : undefined;
}

/** The rules of the POST: MSG01, MSG02 as received, MSG43 ignoring case against every other entry. */
function checkName(name: unknown, ownId?: string) {
  if (typeof name !== 'string' || !name.trim())
    return departmentNameError('MSG01', { field_name: 'name' });
  if (name.length > 50) return departmentNameError('MSG02', { field_name: 'name', max_length: 50 });
  const stripped = name.trim().toLowerCase();
  if (departments.some((d) => d.departmentId !== ownId && d.name.toLowerCase() === stripped))
    return departmentNameError('MSG43', undefined, 409);
  return null;
}

// The users mock serves GET /api/v1/users/departments from a static list, so Departments
// created or renamed here do not show in the 2.2 table in browser mock mode (spec Interim limits 4 and 6).
export const handlers: HttpHandler[] = [
  http.post('*/api/v1/workspace/setup', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const denied = accessError();
    if (denied) return denied;
    const input: unknown = await request.json().catch(() => null);
    if (!isRecord(input)) return problem(400);
    const invalid = validate(input, true);
    if (invalid) return invalid;
    if (workspace)
      return HttpResponse.json({ status: 409, code: 'WORKSPACE_ALREADY_SET_UP' }, { status: 409 });
    // Narrow the JSON boundary before constructing the generated response type.
    if (typeof input.companyName !== 'string' || typeof input.jiraSiteUrl !== 'string')
      return problem(400);
    workspace = {
      companyName: input.companyName.trim(),
      jiraSiteUrl: input.jiraSiteUrl.trim().toLowerCase().replace(/\/$/, ''),
      // The Jira App sends its token after it is installed (KAN-349); a new Workspace starts not connected.
      jiraAppConnected: false,
      jiraAppTokenReceivedAt: null,
    };
    return HttpResponse.json<WorkspaceInfo>(workspace, { status: 201 });
  }),
  http.get('*/api/v1/workspace', async () => {
    await delay(MOCK_DELAY_MS);
    return (
      accessError() ??
      (workspace ? HttpResponse.json<WorkspaceInfo>(workspace) : problem(404, 'MSG60'))
    );
  }),
  http.put('*/api/v1/workspace', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const denied = accessError();
    if (denied) return denied;
    if (!workspace) return problem(404, 'MSG60');
    const input: unknown = await request.json().catch(() => null);
    if (!isRecord(input)) return problem(400);
    const invalid = validate(input, false);
    if (invalid) return invalid;
    if (typeof input.companyName !== 'string') return problem(400);
    workspace = { ...workspace, companyName: input.companyName.trim() };
    return HttpResponse.json<WorkspaceInfo>(workspace);
  }),
  http.post('*/api/v1/departments', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const name = nameOf(await request.json().catch(() => null));
    const invalid = checkName(name);
    if (invalid) return invalid;
    const department: DepartmentRef = {
      departmentId: crypto.randomUUID(),
      name: String(name).trim(),
    };
    departments.push({ ...department, defaultProjectId: null, projects: [] });
    return HttpResponse.json<DepartmentRef>(department, { status: 201 });
  }),
  http.get('*/api/v1/departments/:departmentId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const found = departments.find((d) => d.departmentId === params.departmentId);
    return found ? HttpResponse.json<DepartmentDetail>(detail(found)) : problem(404, 'MSG04');
  }),
  http.put('*/api/v1/departments/:departmentId', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const found = departments.find((d) => d.departmentId === params.departmentId);
    if (!found) return problem(404, 'MSG04');
    const input: unknown = await request.json().catch(() => null);
    const name = nameOf(input);
    const invalid = checkName(name, found.departmentId);
    if (invalid) return invalid;
    // Absent or null clears the default Project (K121-D29); MSG156 and MSG157 are driven in tests with server.use.
    const projectId = isRecord(input) ? (input.defaultProjectId ?? null) : null;
    if (projectId !== null && !found.projects.some((p) => p.projectId === projectId))
      return fieldError('defaultProjectId', 'MSG04');
    found.name = String(name).trim();
    found.defaultProjectId = typeof projectId === 'string' ? projectId : null;
    return HttpResponse.json<DepartmentDetail>(detail(found));
  }),
];

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  workspace = undefined;
  departments = seed();
}
