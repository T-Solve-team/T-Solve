import { createUser, getUserCreationOptions, getUserDepartments, getUsers } from '@/shared/api';
import { setDevUser } from '@/shared/auth';

describe('Workspace user mock contracts', () => {
  beforeEach(() => setDevUser('admin'));

  it('AC-005.2 rejects unknown JSON, invalid roles and invalid Space identifiers without mutation', async () => {
    const invalid: unknown[] = [
      null,
      [],
      42,
      {},
      { email: 42, roles: [] },
      { email: 'new@company.example', roles: ['OWNER'] },
      { email: 'new@company.example', roles: 'ADMIN' },
      { email: 'new@company.example', roles: ['KNOWLEDGE_MANAGER'], spaceIds: ['3'] },
      { email: 'new@company.example', roles: ['KNOWLEDGE_MANAGER'], spaceIds: [-1] },
      { email: 'new@company.example', roles: ['KNOWLEDGE_MANAGER'], spaceIds: [1.5] },
    ];
    for (const body of invalid) {
      // Exercise untyped JavaScript callers at the JSON boundary without asserting a request type.
      await expect(Reflect.apply(createUser, undefined, [body])).rejects.toMatchObject({
        status: 400,
      });
    }
    expect((await getUsers()).page.totalElements).toBe(24);
  });

  it('AC-005.4 creates an Active account with Employee retained and a nullable full name (BUG-017)', async () => {
    const created = await createUser({ email: 'new@company.example', roles: ['ADMIN'] });
    expect(created.id).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );
    expect(created).toMatchObject({
      email: 'new@company.example',
      fullName: null,
      status: 'ACTIVE',
      roles: ['EMPLOYEE', 'ADMIN'],
      lastSignInAt: null,
    });
    expect((await getUsers({ q: created.email })).content).toEqual([created]);
  });

  it.each([
    ['', 'MSG01'],
    ['malformed', 'MSG03'],
    [`${'a'.repeat(90)}@company.example`, 'MSG02'],
    ['outside@example.org', 'MSG03'],
    ['user1@company.example', 'MSG11'],
    ['USER1@company.example', 'MSG11'],
  ])('AC-005.3 rejects email %s with %s atomically', async (email, code) => {
    await expect(createUser({ email, roles: [] })).rejects.toMatchObject({
      code,
      fieldErrors: [{ field: 'email', code }],
    });
    expect((await getUsers()).page.totalElements).toBe(24);
  });

  it('AC-005.5 rejects missing or foreign Department for Project Managers', async () => {
    for (const departmentId of [undefined, 999]) {
      await expect(
        createUser({ email: 'pm@company.example', roles: ['PROJECT_MANAGER'], departmentId }),
      ).rejects.toMatchObject({ code: 'MSG01', fieldErrors: [{ field: 'departmentId' }] });
    }
    expect((await getUsers()).page.totalElements).toBe(24);
  });

  it('AC-005.6 uses Workspace options and enforces one Knowledge Manager per Space atomically', async () => {
    const options = await getUserCreationOptions();
    expect(options.companyDomain).toBe('company.example');
    expect(options.spaces.map((space) => space.name)).toEqual([
      'IT Knowledge',
      'HR Knowledge',
      'Engineering Handbook',
    ]);
    await expect(
      createUser({ email: 'km@company.example', roles: ['KNOWLEDGE_MANAGER'], spaceIds: [] }),
    ).rejects.toMatchObject({ code: 'MSG01', fieldErrors: [{ field: 'spaceIds' }] });
    await expect(
      createUser({ email: 'km@company.example', roles: ['KNOWLEDGE_MANAGER'], spaceIds: [1] }),
    ).rejects.toMatchObject({ code: 'MSG117', params: { space_name: 'IT Knowledge' } });
    await expect(
      createUser({ email: 'km@company.example', roles: ['KNOWLEDGE_MANAGER'], spaceIds: [999] }),
    ).rejects.toMatchObject({ code: 'MSG01' });
    await createUser({ email: 'km@company.example', roles: ['KNOWLEDGE_MANAGER'], spaceIds: [3] });
    await expect(
      createUser({ email: 'km2@company.example', roles: ['KNOWLEDGE_MANAGER'], spaceIds: [3] }),
    ).rejects.toMatchObject({ code: 'MSG117' });
    expect((await getUsers()).page.totalElements).toBe(25);
  });

  it('AC-005.7 rejects duplicate pending submissions without creating two accounts', async () => {
    const results = await Promise.allSettled([
      createUser({ email: 'once@company.example', roles: [] }),
      createUser({ email: 'once@company.example', roles: [] }),
    ]);
    expect(results.filter((result) => result.status === 'fulfilled')).toHaveLength(1);
    expect((await getUsers({ q: 'once@company.example' })).page.totalElements).toBe(1);
  });

  it('AC-005.8 rejects Deactivated account email with MSG111', async () => {
    await expect(createUser({ email: 'user11@company.example', roles: [] })).rejects.toMatchObject({
      code: 'MSG111',
      fieldErrors: [{ field: 'email', code: 'MSG111' }],
    });
    expect((await getUsers()).page.totalElements).toBe(24);
  });

  it('AC-005.1 denies account creation and options to non-Admins', async () => {
    setDevUser('employee');
    await expect(createUser({ email: 'blocked@company.example', roles: [] })).rejects.toMatchObject(
      { status: 403 },
    );
    await expect(getUserCreationOptions()).rejects.toMatchObject({ status: 403 });
    setDevUser(null);
    await expect(createUser({ email: 'blocked@company.example', roles: [] })).rejects.toMatchObject(
      { status: 401 },
    );
    await expect(getUserCreationOptions()).rejects.toMatchObject({ status: 401 });
  });

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
      departmentId: 1,
    });
    expect(page.content.length).toBeGreaterThan(0);
    for (const user of page.content) {
      expect(user.roles).toContain('PROJECT_MANAGER');
      expect(user.department?.departmentId).toBe(1);
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
