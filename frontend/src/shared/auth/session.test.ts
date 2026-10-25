import type { CurrentUser } from '@/shared/api';
import { ROLE_LABELS, ROLES, toSession } from '@/shared/auth';

const USER_ID = '00000000-0000-4000-8000-000000000001';
const DEPARTMENT_ID = '00000000-0000-4000-8000-0000000000d1';
const OTHER_DEPARTMENT_ID = '00000000-0000-4000-8000-0000000000d2';
const PROJECT_ID = '00000000-0000-4000-8000-000000000101';
const SPACE_ID = '00000000-0000-4000-8000-0000000000a1';

const fromServer: CurrentUser = {
  user: { id: USER_ID, fullName: 'Nguyễn Văn An', email: 'an.nguyen@company.example' },
  roles: ['ADMIN'],
  department: null,
  departments: [],
  projects: [],
  managedSpaces: [],
  workspaceReady: true,
};

describe('session roles', () => {
  it('AC-001.1 knows exactly the five Report3 roles, with their labels', () => {
    expect(ROLES).toEqual([
      'EMPLOYEE',
      'TICKET_RESOLVER',
      'PROJECT_MANAGER',
      'KNOWLEDGE_MANAGER',
      'ADMIN',
    ]);
    expect(ROLES.map((role) => ROLE_LABELS[role])).toEqual([
      'Employee',
      'Ticket Resolver',
      'Project Manager',
      'Knowledge Manager',
      'Admin',
    ]);
  });

  it('AC-001.3 adds Employee when /me leaves it out', () => {
    expect(toSession(fromServer).roles).toEqual(['EMPLOYEE', 'ADMIN']);
  });

  it('AC-001.3 does not add Employee twice', () => {
    expect(toSession({ ...fromServer, roles: ['EMPLOYEE', 'ADMIN'] }).roles).toEqual([
      'EMPLOYEE',
      'ADMIN',
    ]);
  });

  it('AC-001.7 leaves out a role the web app does not know and keeps the others', () => {
    // A newer backend could send a role this build does not know yet.
    const withUnknownRole = { ...fromServer, roles: ['EMPLOYEE', 'AUDITOR', 'ADMIN'] };
    expect(toSession(withUnknownRole).roles).toEqual(['EMPLOYEE', 'ADMIN']);
  });

  it('AC-001.8 keeps every /me id as the UUID string the response gives', () => {
    const session = toSession({
      ...fromServer,
      department: { departmentId: DEPARTMENT_ID, name: 'IT' },
      departments: [
        { departmentId: DEPARTMENT_ID, name: 'IT' },
        { departmentId: OTHER_DEPARTMENT_ID, name: 'HR' },
      ],
      projects: [
        {
          projectId: PROJECT_ID,
          jiraProjectKey: 'ITSUP',
          departmentId: DEPARTMENT_ID,
          departmentName: 'IT',
          roleInProject: 'MEMBER',
        },
      ],
      managedSpaces: [{ spaceId: SPACE_ID, name: 'IT Space' }],
    });

    expect(session.user.id).toBe(USER_ID);
    expect(session.department?.departmentId).toBe(DEPARTMENT_ID);
    expect(session.departments.map((d) => d.departmentId)).toEqual([
      DEPARTMENT_ID,
      OTHER_DEPARTMENT_ID,
    ]);
    expect(session.projects[0]?.projectId).toBe(PROJECT_ID);
    expect(session.projects[0]?.departmentId).toBe(DEPARTMENT_ID);
    expect(session.managedSpaces[0]?.spaceId).toBe(SPACE_ID);
  });
});
