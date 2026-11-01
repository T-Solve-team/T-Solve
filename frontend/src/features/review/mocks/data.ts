import type { ReviewQueueItem, ReviewTicketDetail } from '@/shared/api';

/** Department ids equal the ones of the mock session (src/mocks/session.ts). */
const IT_DEPARTMENT_ID = '00000000-0000-4000-8000-0000000000d1';
const HR_DEPARTMENT_ID = '00000000-0000-4000-8000-0000000000d2';

/**
 * Projects in the review queue mock and their Departments. The mock Project Manager (src/mocks/session.ts) manages
 * ITSUP and ITNET; the separate HRHELP fixture verifies unmanaged Project access.
 */
export const MOCK_PROJECTS = [
  {
    projectId: '00000000-0000-4000-8000-000000000101',
    name: 'IT Support',
    key: 'ITSUP',
    departmentId: IT_DEPARTMENT_ID,
    reviewRangeDays: 3,
  },
  {
    projectId: '00000000-0000-4000-8000-000000000102',
    name: 'IT Network',
    key: 'ITNET',
    departmentId: IT_DEPARTMENT_ID,
    reviewRangeDays: 5,
  },
  {
    projectId: '00000000-0000-4000-8000-000000000201',
    name: 'HR Helpdesk',
    key: 'HRHELP',
    departmentId: HR_DEPARTMENT_ID,
    reviewRangeDays: 3,
  },
] as const;

/** The eight tickets of the Figma frame "7.1 Review Queue", then generated ones up to 58 (Figma total). */
const FIGMA_TICKETS = [
  [
    'ITSUP-1031',
    'Outlook keeps asking for password after M365 migration',
    101,
    'Nguyễn Thị Thu Hà',
    '2026-10-01T11:35:00Z',
  ],
  [
    'ITSUP-1102',
    'VPN keeps dropping during Teams calls',
    101,
    'Phạm Quốc Bảo',
    '2026-10-02T09:02:00Z',
  ],
  [
    'ITNET-0415',
    'Meeting room 7A display does not detect laptops',
    102,
    'Lê Văn Tùng',
    '2026-10-02T10:40:00Z',
  ],
  [
    'HRHELP-0318',
    'Payslip PDF shows wrong overtime hours',
    201,
    'Trần Thị Mai',
    '2026-10-03T01:15:00Z',
  ],
  [
    'ITSUP-1110',
    'Laptop battery drains overnight in sleep mode',
    101,
    'Đặng Minh Tuấn',
    '2026-10-03T03:22:00Z',
  ],
  [
    'ITNET-0418',
    'Guest Wi-Fi portal does not load on iPhone',
    102,
    'Võ Hoàng Long',
    '2026-10-03T07:05:00Z',
  ],
  [
    'HRHELP-0320',
    'Leave request stuck at "Waiting for manager"',
    201,
    'Bùi Thị Ngọc',
    '2026-10-04T02:12:00Z',
  ],
  [
    'ITSUP-1115',
    'Shared mailbox not visible in Outlook',
    101,
    'Hoàng Đức Anh',
    '2026-10-04T04:30:00Z',
  ],
] as const;

const MORE_TITLES = [
  'Printer on floor 3 prints blank pages',
  'Cannot install the VPN client on macOS 15',
  'Teams meeting recordings are missing',
  'Password reset link has expired',
  'New starter has no access to the HR portal',
  'Wi-Fi drops in the training room',
  'Excel macro blocked by security policy',
  'Timesheet total does not include public holidays',
  'Monitor flickers when docked',
  'OneDrive sync stuck at "Processing changes"',
];
const MORE_AUTHORS = [
  'Nguyễn Văn Hùng',
  'Trần Minh Châu',
  'Lê Thị Hồng',
  'Phan Quang Huy',
  'Vũ Thị Lan',
  null,
];

const REVIEW_RANGE_MS = 3 * 24 * 60 * 60 * 1000; // Review due date = entered queue + 3 days (review range)

function projectRef(projectId: string) {
  const project = MOCK_PROJECTS.find((p) => p.projectId === projectId) ?? MOCK_PROJECTS[0];
  return { projectId: project.projectId, name: project.name };
}

function item(
  ticketId: string,
  sourceTicketId: string,
  title: string,
  projectId: string,
  authorName: string | null,
  enteredQueueAt: string,
): ReviewQueueItem {
  return {
    ticketId,
    sourceTicketId,
    title,
    project: projectRef(projectId),
    source: 'SOLUTION_FORM', // only Solution Form tickets enter the queue (SRS 7.1)
    authorName,
    enteredQueueAt,
    reviewDueDate: new Date(Date.parse(enteredQueueAt) + REVIEW_RANGE_MS).toISOString(),
  };
}

/** A fresh copy of the 58 pending tickets. Deterministic: same data on every reload and in every test. */
export function ticketUuid(n: number): string {
  return `20000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
}

export function seedReviewQueue(): ReviewQueueItem[] {
  const tickets = FIGMA_TICKETS.map(([key, title, projectId, author, entered], index) =>
    item(
      ticketUuid(index + 1),
      key.replace('HRHELP', 'ITSUP'),
      title,
      MOCK_PROJECTS[projectId === 102 ? 1 : 0].projectId,
      author,
      entered,
    ),
  );
  const start = Date.parse('2026-10-04T05:00:00Z');
  for (let n = 0; n < 50; n++) {
    const project = MOCK_PROJECTS[n % 2] ?? MOCK_PROJECTS[0];
    const key = `${project.key}-${String(1200 + n).padStart(4, '0')}`;
    const title = MORE_TITLES[n % MORE_TITLES.length] ?? 'Untitled';
    const author = MORE_AUTHORS[n % MORE_AUTHORS.length] ?? null;
    const entered = new Date(start + n * 97 * 60 * 1000).toISOString();
    tickets.push(item(ticketUuid(9 + n), key, title, project.projectId, author, entered));
  }
  return tickets;
}

export const REVIEW_WORKSPACE_ID = '00000000-0000-4000-8000-0000000000c1';
export type MockReviewTicket = ReviewTicketDetail & {
  workspaceId: string;
  enteredQueueAt: string;
  jiraStatus: string;
  expiredAt?: string;
  previousStatus?: 'PENDING_REVIEW' | 'CHANGES_REQUESTED';
};

/** Extra records never inflate the 58 pending tickets of the default Project Manager. */
export const DETAIL_TICKET_IDS = {
  published: ticketUuid(101),
  imported: ticketUuid(102),
  rejected: ticketUuid(103),
  authored: ticketUuid(104),
  unmanaged: ticketUuid(105),
  foreign: ticketUuid(106),
  selfAuthored: ticketUuid(107),
} as const;

export function seedReviewTickets(): MockReviewTicket[] {
  const tickets: MockReviewTicket[] = seedReviewQueue().map((ticket, index) => ({
    ...ticket,
    authorName: ticket.authorName ?? null,
    authorId:
      index === 1 ? '00000000-0000-4000-8000-000000000004' : '00000000-0000-4000-8000-000000000005',
    description: `Reported issue: ${ticket.title}`,
    status: 'PENDING_REVIEW',
    workspaceId: REVIEW_WORKSPACE_ID,
    jiraStatus: 'Resolved',
    lastReviewComment: null,
    solution: {
      symptoms: ticket.title,
      rootCause: 'The client configuration was out of date.',
      resolutionSteps: 'Update the configuration, restart the client and verify access.',
      workaround: null,
      scope: 'Affected company workstations',
    },
    evidence: [
      {
        id: ticketUuid(1000 + index),
        filename: 'resolution-check.txt',
        sizeBytes: 128,
        url: '/api/v1/evidence/resolution-check.txt',
      },
    ],
  }));
  const first = tickets[0];
  if (!first) return tickets;
  const extra = (ticketId: string, changes: Partial<MockReviewTicket>): MockReviewTicket => ({
    ...structuredClone(first),
    ticketId,
    sourceTicketId: `ITSUP-${ticketId.slice(-3)}`,
    ...changes,
  });
  tickets.push(
    extra(DETAIL_TICKET_IDS.published, { status: 'PUBLISHED', reviewDueDate: null }),
    extra(DETAIL_TICKET_IDS.imported, {
      status: 'PUBLISHED',
      source: 'CSV',
      reviewDueDate: null,
      authorId: null,
      authorName: null,
    }),
    extra(DETAIL_TICKET_IDS.rejected, { status: 'REJECTED' }),
    extra(DETAIL_TICKET_IDS.authored, {
      status: 'CHANGES_REQUESTED',
      authorId: '00000000-0000-4000-8000-000000000004',
      authorName: 'Phạm Quốc Bảo',
      lastReviewComment: 'Please clarify the validation steps.',
    }),
    extra(DETAIL_TICKET_IDS.unmanaged, {
      project: projectRef(MOCK_PROJECTS[2].projectId),
      sourceTicketId: 'HRHELP-0318',
    }),
    extra(DETAIL_TICKET_IDS.foreign, { workspaceId: 'ffffffff-ffff-4fff-8fff-ffffffffffff' }),
    extra(DETAIL_TICKET_IDS.selfAuthored, {
      status: 'REJECTED',
      authorId: '00000000-0000-4000-8000-000000000003',
    }),
  );
  for (let n = 0; n < 24; n++) {
    const original = tickets[n];
    if (original)
      tickets.push(
        extra(ticketUuid(300 + n), {
          ...structuredClone(original),
          ticketId: ticketUuid(300 + n),
          sourceTicketId: `EXP-${300 + n}`,
          status: 'EXPIRED',
          expiredAt: new Date(Date.UTC(2026, 9, 31, n)).toISOString(),
          previousStatus: n % 2 ? 'CHANGES_REQUESTED' : 'PENDING_REVIEW',
        }),
      );
  }
  return tickets;
}

/** Department of a ticket, through its Project (the API filters the queue by Department). */
export function departmentOf(ticket: ReviewQueueItem): string | undefined {
  return MOCK_PROJECTS.find((p) => p.projectId === ticket.project.projectId)?.departmentId;
}
