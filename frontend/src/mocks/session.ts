import { http, HttpResponse } from 'msw';
import { resolveMockSession } from '@/features/users/mocks/state';
import type { CurrentUser, ProjectMembership } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { getDevSite, getDevUser, setDevUser, type DevUserId } from '@/shared/auth';

/** Fixed UUIDs, one per record, reused wherever the record appears (guideline: ids are UUID strings). */
const IT_ID = '00000000-0000-4000-8000-0000000000d1';
const HR_ID = '00000000-0000-4000-8000-0000000000d2';
const ITSUP_ID = '00000000-0000-4000-8000-000000000101';
const ITNET_ID = '00000000-0000-4000-8000-000000000102';
const HRHELP_ID = '00000000-0000-4000-8000-000000000201';
const IT_SPACE_ID = '00000000-0000-4000-8000-0000000000a1';
const HR_SPACE_ID = '00000000-0000-4000-8000-0000000000a2';

const IT = { departmentId: IT_ID, name: 'IT' };
const HR = { departmentId: HR_ID, name: 'HR' };

const ITSUP = {
  projectId: ITSUP_ID,
  jiraProjectKey: 'ITSUP',
  departmentId: IT_ID,
  departmentName: 'IT',
};
const ITNET = {
  projectId: ITNET_ID,
  jiraProjectKey: 'ITNET',
  departmentId: IT_ID,
  departmentName: 'IT',
};
const HRHELP = {
  projectId: HRHELP_ID,
  jiraProjectKey: 'HRHELP',
  departmentId: HR_ID,
  departmentName: 'HR',
};

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
    user: {
      id: '00000000-0000-4000-8000-000000000002',
      fullName: 'Trần Thị Lan',
      email: 'lan.tran@company.example',
    },
    roles: ['EMPLOYEE'],
    ...NOBODY_ELSE,
    workspaceReady: true,
  },
  tr: {
    user: {
      id: '00000000-0000-4000-8000-000000000004',
      fullName: 'Phạm Quốc Bảo',
      email: 'bao.pham@company.example',
    },
    roles: ['EMPLOYEE', 'TICKET_RESOLVER'],
    ...NOBODY_ELSE,
    departments: [IT],
    projects: [member(ITSUP)],
    workspaceReady: true,
  },
  'tr-two-departments': {
    user: {
      id: '00000000-0000-4000-8000-000000000005',
      fullName: 'Ngô Thị Mai',
      email: 'mai.ngo@company.example',
    },
    roles: ['EMPLOYEE', 'TICKET_RESOLVER'],
    ...NOBODY_ELSE,
    departments: [IT, HR],
    projects: [member(ITSUP), member(HRHELP)],
    workspaceReady: true,
  },
  pm: {
    user: {
      id: '00000000-0000-4000-8000-000000000003',
      fullName: 'Đỗ Minh Quân',
      email: 'quan.do@company.example',
    },
    roles: ['EMPLOYEE', 'PROJECT_MANAGER'],
    ...NOBODY_ELSE,
    department: IT,
    departments: [IT],
    projects: [managed(ITSUP), managed(ITNET)],
    workspaceReady: true,
  },
  km: {
    user: {
      id: '00000000-0000-4000-8000-000000000006',
      fullName: 'Lê Thu Hương',
      email: 'huong.le@company.example',
    },
    roles: ['EMPLOYEE', 'KNOWLEDGE_MANAGER'],
    ...NOBODY_ELSE,
    managedSpaces: [{ spaceId: IT_SPACE_ID, name: 'IT Space' }],
    workspaceReady: true,
  },
  admin: {
    user: {
      id: '00000000-0000-4000-8000-000000000001',
      fullName: 'Nguyễn Văn An',
      email: 'an.nguyen@company.example',
    },
    roles: ['EMPLOYEE', 'ADMIN'],
    ...NOBODY_ELSE,
    workspaceReady: true,
  },
  'pm-km': {
    user: {
      id: '00000000-0000-4000-8000-000000000007',
      fullName: 'Võ Thanh Tâm',
      email: 'tam.vo@company.example',
    },
    roles: ['EMPLOYEE', 'PROJECT_MANAGER', 'KNOWLEDGE_MANAGER'],
    ...NOBODY_ELSE,
    department: HR,
    departments: [HR],
    projects: [managed(HRHELP)],
    managedSpaces: [{ spaceId: HR_SPACE_ID, name: 'HR Space' }],
    workspaceReady: true,
  },
  'admin-tr': {
    user: {
      id: '00000000-0000-4000-8000-000000000008',
      fullName: 'Hoàng Minh Đức',
      email: 'duc.hoang@company.example',
    },
    roles: ['EMPLOYEE', 'ADMIN', 'TICKET_RESOLVER'],
    ...NOBODY_ELSE,
    departments: [IT],
    projects: [member(ITSUP)],
    workspaceReady: true,
  },
};

/**
 * "Unknown site" / "Suspended Workspace" (dev user switcher, test option `{ site }`): every API request answers
 * like the tenant resolver does, whatever the endpoint — first in the list, so no other mock is reached.
 */
const siteHandler = http.all('*/api/v1/*', () => {
  const site = getDevSite();
  if (site === 'unknown-site') return problem(404, 'MSG119');
  if (site === 'suspended-workspace') return problem(403, 'MSG120');
  return undefined; // a normal site: the next handler answers
});

/** `/me` and logout, driven by the dev user (shared/auth/dev-user.ts). */
export const sessionHandlers = [
  siteHandler,
  http.get('*/api/v1/me', () => {
    const user = getDevUser();
    const session = user ? resolveMockSession(user, MOCK_USERS[user]) : null;
    return session ? HttpResponse.json(session) : problem(401);
  }),
  http.post('*/api/v1/auth/logout', () => {
    setDevUser(null);
    return new HttpResponse(null, { status: 204 });
  }),
];
