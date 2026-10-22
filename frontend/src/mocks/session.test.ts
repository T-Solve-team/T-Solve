import { getMe, isApiError } from '@/shared/api';
import { DEV_USERS, getDevUser, setDevUser } from '@/shared/auth';
import { MOCK_USERS } from './session';

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
});
