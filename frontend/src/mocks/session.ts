import { http, HttpResponse } from 'msw';
import type { CurrentUser, ProjectMembership } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { getDevUser, setDevUser, type DevUserId } from '@/shared/auth';

const IT = { departmentId: 1, name: 'IT' };
const HR = { departmentId: 2, name: 'HR' };

const ITSUP = { projectId: 101, jiraProjectKey: 'ITSUP', departmentId: 1, departmentName: 'IT' };
const ITNET = { projectId: 102, jiraProjectKey: 'ITNET', departmentId: 1, departmentName: 'IT' };
const HRHELP = { projectId: 201, jiraProjectKey: 'HRHELP', departmentId: 2, departmentName: 'HR' };

const managed = (project: typeof ITSUP): ProjectMembership => ({
  ...project,
  roleInProject: 'PROJECT_MANAGER',
});
const member = (project: typeof ITSUP): ProjectMembership => ({
  ...project,
  roleInProject: 'MEMBER',
});

const NOBODY_ELSE = { department: null, departments: [], projects: [], managedSpaces: [] };

/**
 * Fixture users (gap report A7): one per role and the role combinations the screens must handle. Every
 * Project Manager has exactly one Department (GBR-16). The dev user switcher / test helpers pick which one
 * `/me` returns.
 */
export const MOCK_USERS: Record<DevUserId, CurrentUser> = {
  employee: {
    user: { id: 2, fullName: 'Trần Thị Lan', email: 'lan.tran@company.example' },
    roles: ['EMPLOYEE'],
    ...NOBODY_ELSE,
    workspaceReady: true,
  },
  tr: {
    user: { id: 4, fullName: 'Phạm Quốc Bảo', email: 'bao.pham@company.example' },
    roles: ['EMPLOYEE', 'TICKET_RESOLVER'],
    ...NOBODY_ELSE,
    departments: [IT],
    projects: [member(ITSUP)],
    workspaceReady: true,
  },
  'tr-two-departments': {
    user: { id: 5, fullName: 'Ngô Thị Mai', email: 'mai.ngo@company.example' },
    roles: ['EMPLOYEE', 'TICKET_RESOLVER'],
    ...NOBODY_ELSE,
    departments: [IT, HR],
    projects: [member(ITSUP), member(HRHELP)],
    workspaceReady: true,
  },
  pm: {
    user: { id: 3, fullName: 'Đỗ Minh Quân', email: 'quan.do@company.example' },
    roles: ['EMPLOYEE', 'PROJECT_MANAGER'],
    ...NOBODY_ELSE,
    department: IT,
    departments: [IT],
    projects: [managed(ITSUP), managed(ITNET)],
    workspaceReady: true,
  },
  km: {
    user: { id: 6, fullName: 'Lê Thu Hương', email: 'huong.le@company.example' },
    roles: ['EMPLOYEE', 'KNOWLEDGE_MANAGER'],
    ...NOBODY_ELSE,
    managedSpaces: [{ spaceId: 1, name: 'IT Space' }],
    workspaceReady: true,
  },
  admin: {
    user: { id: 1, fullName: 'Nguyễn Văn An', email: 'an.nguyen@company.example' },
    roles: ['EMPLOYEE', 'ADMIN'],
    ...NOBODY_ELSE,
    workspaceReady: true,
  },
  'pm-km': {
    user: { id: 7, fullName: 'Võ Thanh Tâm', email: 'tam.vo@company.example' },
    roles: ['EMPLOYEE', 'PROJECT_MANAGER', 'KNOWLEDGE_MANAGER'],
    ...NOBODY_ELSE,
    department: HR,
    departments: [HR],
    projects: [managed(HRHELP)],
    managedSpaces: [{ spaceId: 2, name: 'HR Space' }],
    workspaceReady: true,
  },
  'admin-tr': {
    user: { id: 8, fullName: 'Hoàng Minh Đức', email: 'duc.hoang@company.example' },
    roles: ['EMPLOYEE', 'ADMIN', 'TICKET_RESOLVER'],
    ...NOBODY_ELSE,
    departments: [IT],
    projects: [member(ITSUP)],
    workspaceReady: true,
  },
};

/** `/me` and logout, driven by the dev user (shared/auth/dev-user.ts). */
export const sessionHandlers = [
  http.get('*/api/v1/me', () => {
    const user = getDevUser();
    return user ? HttpResponse.json(MOCK_USERS[user]) : problem(401);
  }),
  http.post('*/api/v1/auth/logout', () => {
    setDevUser(null);
    return new HttpResponse(null, { status: 204 });
  }),
];
