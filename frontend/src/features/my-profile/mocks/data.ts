import type { MySolution, SpaceRef, TicketStatus } from '@/shared/api';
import type { DevUserId } from '@/shared/auth';

const space = (n: number, name: string): SpaceRef => ({
  spaceId: `00000000-0000-4000-8000-${n.toString(16).padStart(12, '0')}`,
  name,
});

// Same ids as the managed Spaces of the fixture users (src/mocks/session.ts).
const IT_SPACE = space(0xa1, 'IT Space');
const HR_SPACE = space(0xa2, 'HR Space');
const LONG_NAME =
  'Regional support knowledge base for the EMEA and APAC service desks, escalation procedures, runbooks and onboarding gui…';

/** The Spaces each dev user can view (AC-003.3); the handler sorts them by name. */
export const VIEWABLE_SPACES: Record<DevUserId, SpaceRef[]> = {
  employee: [],
  tr: [IT_SPACE, space(0xb1, 'Onboarding'), space(0xb2, 'Network')],
  'tr-two-departments': [IT_SPACE, HR_SPACE, space(0xb1, 'Onboarding'), space(0xb3, 'Policies')],
  pm: [IT_SPACE, space(0xb4, 'Projects')],
  km: [IT_SPACE, space(0xb5, 'Runbooks'), space(0xb6, 'Archive')],
  admin: [space(0xb7, 'Company wiki')],
  'pm-km': [HR_SPACE, space(0xb4, 'Projects')],
  'admin-tr': [
    IT_SPACE,
    HR_SPACE,
    space(0xb8, 'Finance'),
    space(0xb9, 'Legal'),
    space(0xba, 'Operations'),
    space(0xbb, LONG_NAME),
  ],
};

const pick = <T>(list: T[], i: number): T => list[i % list.length] as T;
const uuid = (n: number) => `00000000-0000-4000-8000-${n.toString(16).padStart(12, '0')}`;
const STATUSES: TicketStatus[] = [
  'PENDING_REVIEW',
  'CHANGES_REQUESTED',
  'PUBLISHED',
  'REJECTED',
  'UNPUBLISHED',
  'EXPIRED',
];
const PROJECTS = [
  { projectId: uuid(0xc1), projectName: 'Helpdesk' },
  { projectId: uuid(0xc2), projectName: 'Network operations' },
  { projectId: uuid(0xc3), projectName: 'Dự án Hạ tầng' },
  { projectId: uuid(0xc4), projectName: 'Onboarding' },
];

// Index 0 and 1 sit on both sides of the UTC+7 day boundary; 2 and 3 share an updatedAt (tie-break by id).
const updatedAt = (i: number) =>
  i === 0
    ? '2026-10-30T17:00:00Z'
    : i === 1
      ? '2026-10-30T16:59:59Z'
      : i <= 3
        ? '2026-10-30T10:00:00Z'
        : new Date(Date.UTC(2026, 9, 29, 12) - i * 5 * 3_600_000)
            .toISOString()
            .replace('.000Z', 'Z');

const row = (i: number, firstId: number, canOpen: (i: number) => boolean): MySolution => ({
  ticketId: uuid(firstId + i),
  ticketKey: `SUP-${100 + i}`,
  title:
    i === 5
      ? 'A'.repeat(200)
      : i % 7 === 0
        ? `Khắc phục lỗi đăng nhập VPN số ${i}`
        : `Reset the mail profile of user ${i}`,
  ...pick(PROJECTS, i),
  status: pick(STATUSES, i),
  updatedAt: updatedAt(i),
  canOpen: canOpen(i),
  latestDecision:
    i % 3 === 0
      ? null
      : {
          action: i % 3 === 1 ? 'REQUEST_CHANGES' : 'APPROVE',
          comment: i % 3 === 1 ? 'Please add the steps you tried first.' : null,
          decidedAt: updatedAt(i),
        },
});

const PM_ROWS = Array.from({ length: 8 }, (_, i) => row(i, 0x2000, () => true));

/** The Solutions of each dev user (AC-003.6); no entry = no Ticket Resolver or Project Manager role (403). */
export const MY_SOLUTIONS: Partial<Record<DevUserId, MySolution[]>> = {
  tr: Array.from({ length: 45 }, (_, i) => row(i, 0x1000, (n) => n % 5 !== 3)),
  pm: PM_ROWS,
  'pm-km': PM_ROWS,
  'tr-two-departments': [],
  'admin-tr': [],
};
