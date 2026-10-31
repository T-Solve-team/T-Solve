import { getMe, isApiError } from '@/shared/api';
import { DEV_USERS, getDevUser, setDevUser } from '@/shared/auth';
import { MOCK_USERS } from './session';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('mock users', () => {
  it('AC-009.1 offers a user per role and the three role combinations', () => {
    const roles = Object.fromEntries(
      Object.entries(MOCK_USERS).map(([id, user]) => [id, user.roles]),
    );
    expect(roles).toEqual({
      employee: ['EMPLOYEE'],
      tr: ['EMPLOYEE', 'TICKET_RESOLVER'],
      'tr-two-departments': ['EMPLOYEE', 'TICKET_RESOLVER'],
      pm: ['EMPLOYEE', 'PROJECT_MANAGER'],
      km: ['EMPLOYEE', 'KNOWLEDGE_MANAGER'],
      admin: ['EMPLOYEE', 'ADMIN'],
      'pm-km': ['EMPLOYEE', 'PROJECT_MANAGER', 'KNOWLEDGE_MANAGER'],
      'admin-tr': ['EMPLOYEE', 'ADMIN', 'TICKET_RESOLVER'],
    });
    expect(Object.keys(DEV_USERS)).toEqual(Object.keys(MOCK_USERS));
  });

  it('AC-009.1 gives every Project Manager exactly one Department and nobody else a Department', () => {
    for (const user of Object.values(MOCK_USERS)) {
      if (user.roles.includes('PROJECT_MANAGER')) {
        expect(user.department).not.toBeNull();
        expect(user.departments).toEqual([user.department]);
      } else {
        expect(user.department).toBeNull();
      }
    }
  });

  it('AC-009.1 has a Ticket Resolver in Projects of two Departments', () => {
    const { departments, projects } = MOCK_USERS['tr-two-departments'];
    expect(departments.map((d) => d.name)).toEqual(['IT', 'HR']);
    expect(new Set(projects.map((p) => p.departmentId)).size).toBe(2);
  });

  it('AC-009.2 answers /me with the chosen user, and 401 when signed out', async () => {
    setDevUser('km');
    await expect(getMe()).resolves.toEqual(MOCK_USERS.km);
    const body = await getMe();
    expect(body.user.id).toBe(MOCK_USERS.km.user.id);
    expect(body.user.id).toMatch(UUID);

    setDevUser(null);
    const error: unknown = await getMe().catch((e: unknown) => e);
    expect(isApiError(error) && error.status).toBe(401);
  });

  it('AC-009.5 answers the default Project Manager when the stored user is unknown', async () => {
    window.localStorage.setItem('tsolve.dev.user', 'nobody');
    expect(getDevUser()).toBe('pm');
    await expect(getMe()).resolves.toEqual(MOCK_USERS.pm);
  });

  it('AC-009.5 answers the default Project Manager when storage is blocked', () => {
    const blocked = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(getDevUser()).toBe('pm');
    blocked.mockRestore();
  });

  it('AC-009.7 gives every fixture id as a UUID string', () => {
    for (const { user, department, departments, projects, managedSpaces } of Object.values(
      MOCK_USERS,
    )) {
      const ids = [
        user.id,
        ...(department ? [department.departmentId] : []),
        ...departments.map((d) => d.departmentId),
        ...projects.flatMap((p) => [p.projectId, p.departmentId]),
        ...managedSpaces.map((s) => s.spaceId),
      ];
      for (const id of ids) {
        expect(typeof id).toBe('string');
        expect(id).toMatch(UUID);
      }
    }
  });

  it('AC-009.7 uses the same id wherever the same record appears', () => {
    const departments = new Map<string, string>();
    const projects = new Map<string, string>();
    const spaces = new Map<string, string>();
    const note = (map: Map<string, string>, id: string, what: string) => {
      expect(map.get(id) ?? what).toBe(what);
      map.set(id, what);
    };
    for (const user of Object.values(MOCK_USERS)) {
      if (user.department) note(departments, user.department.departmentId, user.department.name);
      for (const d of user.departments) note(departments, d.departmentId, d.name);
      for (const p of user.projects) {
        note(departments, p.departmentId, p.departmentName);
        note(projects, p.projectId, `${p.jiraProjectKey}/${p.departmentId}`);
      }
      for (const s of user.managedSpaces) note(spaces, s.spaceId, s.name);
    }

    // One id per name: IT is one UUID in department, departments and the Projects' departmentId.
    expect(new Set(departments.values()).size).toBe(departments.size);
    expect([...departments.values()].sort()).toEqual(['HR', 'IT']);
    expect(new Set(projects.values()).size).toBe(projects.size);
    expect(new Set(spaces.values()).size).toBe(spaces.size);
  });

  it('AC-009.7 gives each fixture user a different id', () => {
    const ids = Object.values(MOCK_USERS).map((u) => u.user.id);

    expect(ids).toHaveLength(8);
    expect(new Set(ids).size).toBe(8);
  });
});
