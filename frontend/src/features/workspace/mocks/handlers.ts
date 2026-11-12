import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type { DepartmentRef } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import type { MsgCode, MsgParams } from '@/shared/messages';

let created: DepartmentRef[] = [];

function fieldError(code: MsgCode, params?: MsgParams, status = 400) {
  return problem(status, code, { params, fieldErrors: [{ field: 'name', code, params }] });
}

// The users mock serves GET /api/v1/users/departments from a static list, so Departments
// created here do not appear in the table in browser mock mode (spec Interim limit 4).
export const handlers: HttpHandler[] = [
  http.post('*/api/v1/departments', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const input: unknown = await request.json().catch(() => null);
    const name =
      typeof input === 'object' && input !== null && 'name' in input ? input.name : undefined;
    if (typeof name !== 'string' || !name.trim())
      return fieldError('MSG01', { field_name: 'name' });
    if (name.length > 50) return fieldError('MSG02', { field_name: 'name', max_length: 50 });
    const strippedName = name.trim();
    if (created.some((department) => department.name.toLowerCase() === strippedName.toLowerCase()))
      return fieldError('MSG43', undefined, 409);
    const department: DepartmentRef = {
      departmentId: crypto.randomUUID(),
      name: strippedName,
    };
    created.push(department);
    return HttpResponse.json<DepartmentRef>(department, { status: 201 });
  }),
];

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  created = [];
}
