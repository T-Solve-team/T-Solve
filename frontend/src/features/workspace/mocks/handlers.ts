import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type { DepartmentDetail, DepartmentRef, DepartmentSummary } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import type { MsgCode, MsgParams } from '@/shared/messages';

// Copies of the users mock's IT and HR (users/mocks/data.ts), so 2.3 opens from 2.2 in mock mode (K121-D18).
type MockDepartment = DepartmentRef & { status: 'ACTIVE' | 'DELETED'; projectCount: number };

const seed = (): MockDepartment[] => [
  {
    departmentId: '00000000-0000-4000-8000-0000000000d1',
    name: 'IT',
    status: 'ACTIVE',
    projectCount: 2,
  },
  {
    departmentId: '00000000-0000-4000-8000-0000000000d2',
    name: 'HR',
    status: 'ACTIVE',
    projectCount: 0,
  },
];
let departments: MockDepartment[] = seed();

const ref = ({ departmentId, name }: MockDepartment): DepartmentRef => ({ departmentId, name });
const activeOne = (id: unknown) =>
  departments.find((d) => d.departmentId === id && d.status === 'ACTIVE');

function fieldError(code: MsgCode, params?: MsgParams, status = 400) {
  return problem(status, code, { params, fieldErrors: [{ field: 'name', code, params }] });
}

function nameOf(input: unknown): unknown {
  return typeof input === 'object' && input !== null && 'name' in input ? input.name : undefined;
}

/** The rules of the POST: MSG01, MSG02 as received, MSG43 ignoring case against every other entry. */
function checkName(name: unknown, ownId?: string) {
  if (typeof name !== 'string' || !name.trim()) return fieldError('MSG01', { field_name: 'name' });
  if (name.length > 50) return fieldError('MSG02', { field_name: 'name', max_length: 50 });
  const stripped = name.trim().toLowerCase();
  if (departments.some((d) => d.departmentId !== ownId && d.name.toLowerCase() === stripped))
    return fieldError('MSG43', undefined, 409);
  return null;
}

// The 2.2 table reads GET /api/v1/departments here; the Add User form still reads the users mock
// static list of GET /api/v1/users/departments (spec Interim limit 4).
export const handlers: HttpHandler[] = [
  http.get('*/api/v1/departments', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const includeDeleted = new URL(request.url).searchParams.get('includeDeleted') === 'true';
    const rows = departments
      .filter((d) => includeDeleted || d.status === 'ACTIVE')
      .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()))
      .map(({ departmentId, name, status }) => ({ departmentId, name, status }));
    return HttpResponse.json<DepartmentSummary[]>(rows);
  }),
  http.post('*/api/v1/departments/:departmentId/restore', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const found = departments.find(
      (d) => d.departmentId === params.departmentId && d.status === 'DELETED',
    );
    if (!found) return problem(404, 'MSG04');
    found.status = 'ACTIVE';
    return HttpResponse.json<DepartmentRef>(ref(found));
  }),
  http.post('*/api/v1/departments', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const name = nameOf(await request.json().catch(() => null));
    const invalid = checkName(name);
    if (invalid) return invalid;
    const department: MockDepartment = {
      departmentId: crypto.randomUUID(),
      name: String(name).trim(),
      status: 'ACTIVE',
      projectCount: 0,
    };
    departments.push(department);
    return HttpResponse.json<DepartmentRef>(ref(department), { status: 201 });
  }),
  http.get('*/api/v1/departments/:departmentId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const found = activeOne(params.departmentId);
    if (!found) return problem(404, 'MSG04');
    return HttpResponse.json<DepartmentDetail>({ ...ref(found), projectCount: found.projectCount });
  }),
  http.put('*/api/v1/departments/:departmentId', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const found = activeOne(params.departmentId);
    if (!found) return problem(404, 'MSG04');
    const name = nameOf(await request.json().catch(() => null));
    const invalid = checkName(name, found.departmentId);
    if (invalid) return invalid;
    found.name = String(name).trim();
    return HttpResponse.json<DepartmentRef>(ref(found));
  }),
  http.delete('*/api/v1/departments/:departmentId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const found = activeOne(params.departmentId);
    if (!found) return problem(404, 'MSG04');
    found.status = 'DELETED';
    return new HttpResponse(null, { status: 204 });
  }),
];

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  departments = seed();
}
