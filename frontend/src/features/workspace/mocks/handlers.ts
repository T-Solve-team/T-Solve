import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type { DepartmentRef } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import type { MsgCode, MsgParams } from '@/shared/messages';

// Copies of the users mock's IT and HR (users/mocks/data.ts), so 2.3 opens from 2.2 in mock mode (K121-D18).
const seed = (): DepartmentRef[] => [
  { departmentId: '00000000-0000-4000-8000-0000000000d1', name: 'IT' },
  { departmentId: '00000000-0000-4000-8000-0000000000d2', name: 'HR' },
];
let departments: DepartmentRef[] = seed();

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

// The users mock serves GET /api/v1/users/departments from a static list, so Departments
// created or renamed here do not show in the 2.2 table in browser mock mode (spec Interim limits 4 and 6).
export const handlers: HttpHandler[] = [
  http.post('*/api/v1/departments', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const name = nameOf(await request.json().catch(() => null));
    const invalid = checkName(name);
    if (invalid) return invalid;
    const department: DepartmentRef = {
      departmentId: crypto.randomUUID(),
      name: String(name).trim(),
    };
    departments.push(department);
    return HttpResponse.json<DepartmentRef>(department, { status: 201 });
  }),
  http.get('*/api/v1/departments/:departmentId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const found = departments.find((d) => d.departmentId === params.departmentId);
    return found ? HttpResponse.json<DepartmentRef>(found) : problem(404, 'MSG04');
  }),
  http.put('*/api/v1/departments/:departmentId', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const found = departments.find((d) => d.departmentId === params.departmentId);
    if (!found) return problem(404, 'MSG04');
    const name = nameOf(await request.json().catch(() => null));
    const invalid = checkName(name, found.departmentId);
    if (invalid) return invalid;
    found.name = String(name).trim();
    return HttpResponse.json<DepartmentRef>(found);
  }),
];

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  departments = seed();
}
