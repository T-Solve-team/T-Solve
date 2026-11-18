import type {
  MemberCandidateResponse,
  ProjectMemberResponse,
  ProjectSummaryResponse,
} from '@/shared/api';

/** The Project ids of the session fixtures (src/mocks/session.ts): the Project Manager manages both. */
export const ITSUP_ID = '00000000-0000-4000-8000-000000000101';
export const ITNET_ID = '00000000-0000-4000-8000-000000000102';

export const PROJECTS: ProjectSummaryResponse[] = [
  {
    id: ITSUP_ID,
    jiraProjectKey: 'ITSUP',
    departmentName: 'IT',
    projectManagerName: 'Đỗ Minh Quân',
    status: 'ACTIVE',
  },
  {
    id: ITNET_ID,
    jiraProjectKey: 'ITNET',
    departmentName: 'IT',
    projectManagerName: 'Đỗ Minh Quân',
    status: 'REMOVED',
  },
];

const QUAN = {
  userId: '00000000-0000-4000-8000-000000000003',
  fullName: 'Đỗ Minh Quân',
  email: 'quan.do@company.example',
};
const LAN = {
  userId: '00000000-0000-4000-8000-000000000002',
  fullName: 'Trần Thị Lan',
  email: 'lan.tran@company.example',
};

/** Active Ticket Resolvers of the Workspace; the ones not in a Project are its candidates. */
export const TICKET_RESOLVERS: MemberCandidateResponse[] = [
  LAN,
  {
    userId: '00000000-0000-4000-8000-000000000301',
    fullName: 'Phạm Quốc Bảo',
    email: 'bao.pham@company.example',
  },
  {
    userId: '00000000-0000-4000-8000-000000000302',
    fullName: 'Ngô Thị Mai',
    email: 'mai.ngo@company.example',
  },
  {
    userId: '00000000-0000-4000-8000-000000000303',
    fullName: 'Bùi Văn Hùng',
    email: 'hung.bui@company.example',
  },
  {
    userId: '00000000-0000-4000-8000-000000000304',
    fullName: null,
    email: 'new.tr@company.example',
  },
];

export function initialMembers(): Record<string, ProjectMemberResponse[]> {
  return {
    [ITSUP_ID]: [
      { ...QUAN, role: 'PROJECT_MANAGER', dateAdded: '2026-09-15T02:00:00Z' },
      { ...LAN, role: 'MEMBER', dateAdded: '2026-10-01T18:30:00Z' },
    ],
    [ITNET_ID]: [{ ...QUAN, role: 'PROJECT_MANAGER', dateAdded: '2026-09-20T02:00:00Z' }],
  };
}
