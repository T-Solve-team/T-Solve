import type { CurrentUser } from '@/shared/api';
import { ROLE_LABELS, ROLES, toSession } from '@/shared/auth';

const fromServer: CurrentUser = {
  user: { id: 1, fullName: 'Nguyễn Văn An', email: 'an.nguyen@company.example' },
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
});
