import { getUserDepartments, getUsers } from '@/shared/api';
import { setDevUser } from '@/shared/auth';

describe('Workspace user mock contracts', () => {
  beforeEach(() => setDevUser('admin'));

  it('AC-004.1 excludes the foreign Workspace even when searched directly', async () => {
    const page = await getUsers({ q: 'foreign' });
    expect(page.content).toEqual([]);
    expect(page.page.totalElements).toBe(0);
    const departments = await getUserDepartments();
    expect(departments.map((department) => department.name)).toEqual(['IT', 'HR']);
  });

  it('AC-004.2 applies every filter before pagination', async () => {
    const page = await getUsers({
      q: 'USER',
      role: 'PROJECT_MANAGER',
      status: 'ACTIVE',
      departmentId: '00000000-0000-4000-8000-0000000000d1',
    });
    expect(page.content.length).toBeGreaterThan(0);
    for (const user of page.content) {
      expect(user.roles).toContain('PROJECT_MANAGER');
      expect(user.department?.departmentId).toBe('00000000-0000-4000-8000-0000000000d1');
      expect(user.status).toBe('ACTIVE');
    }
    const employees = await getUsers({ role: 'EMPLOYEE' });
    expect(employees.page.totalElements).toBe(6);
    for (const user of employees.content) expect(user.roles).toEqual(['EMPLOYEE']);
  });

  it('AC-004.2 rejects search over 100 characters with the catalog limit error', async () => {
    await expect(getUsers({ q: 'x'.repeat(101) })).rejects.toMatchObject({
      status: 400,
      code: 'MSG02',
      params: { field_name: 'Search', max_length: 100 },
    });
  });

  it('AC-004.3 provides stable pages, totals, descending sign-in sorting and an empty overflow page', async () => {
    const first = await getUsers();
    const last = await getUsers({ page: 1 });
    expect(first.content).toHaveLength(20);
    expect(last.content).toHaveLength(4);
    expect(new Set([...first.content, ...last.content].map((user) => user.id)).size).toBe(24);
    expect(first.page).toEqual({ size: 20, number: 0, totalElements: 24, totalPages: 2 });
    const signedIn = await getUsers({ sort: ['lastSignInAt,desc'] });
    expect(signedIn.content[0]?.id).toBe('10000000-0000-4000-8000-000000000024');
    expect((await getUsers({ page: 2 })).content).toEqual([]);
  });

  it('AC-004.7 rejects non-Admin and signed-out API requests', async () => {
    setDevUser('employee');
    await expect(getUsers()).rejects.toMatchObject({ status: 403, code: 'MSG08' });
    await expect(getUserDepartments()).rejects.toMatchObject({ status: 403, code: 'MSG08' });
    setDevUser(null);
    await expect(getUsers()).rejects.toMatchObject({ status: 401, code: 'MSG07' });
  });

  it('AC-004.3 falls back safely for inherited and unsupported sort keys (BUG-011)', async () => {
    const expected = await getUsers();
    for (const key of ['constructor', 'toString', '__proto__', 'unknown']) {
      expect((await getUsers({ sort: [`${key},asc`] })).content).toEqual(expected.content);
    }
  });
});
