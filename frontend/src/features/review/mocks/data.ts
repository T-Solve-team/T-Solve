import type { ReviewQueueItem } from '@/shared/api';

/**
 * Projects in the review queue mock and their Departments. The mock Project Manager (src/mocks/session.ts) manages
 * ITSUP and ITNET; HRHELP tickets stay in the data until ticket-review scopes the queue to the user's Projects.
 */
export const MOCK_PROJECTS = [
  { projectId: 101, name: 'IT Support', key: 'ITSUP', departmentId: 1 },
  { projectId: 102, name: 'IT Network', key: 'ITNET', departmentId: 1 },
  { projectId: 201, name: 'HR Helpdesk', key: 'HRHELP', departmentId: 2 },
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

function projectRef(projectId: number) {
  const project = MOCK_PROJECTS.find((p) => p.projectId === projectId) ?? MOCK_PROJECTS[0];
  return { projectId: project.projectId, name: project.name };
}

function item(
  ticketId: number,
  sourceTicketId: string,
  title: string,
  projectId: number,
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
export function seedReviewQueue(): ReviewQueueItem[] {
  const tickets = FIGMA_TICKETS.map(([key, title, projectId, author, entered], index) =>
    item(index + 1, key, title, projectId, author, entered),
  );
  const start = Date.parse('2026-10-04T05:00:00Z');
  for (let n = 0; n < 50; n++) {
    const project = MOCK_PROJECTS[n % MOCK_PROJECTS.length] ?? MOCK_PROJECTS[0];
    const key = `${project.key}-${String(1200 + n).padStart(4, '0')}`;
    const title = MORE_TITLES[n % MORE_TITLES.length] ?? 'Untitled';
    const author = MORE_AUTHORS[n % MORE_AUTHORS.length] ?? null;
    const entered = new Date(start + n * 97 * 60 * 1000).toISOString();
    tickets.push(item(9 + n, key, title, project.projectId, author, entered));
  }
  return tickets;
}

/** Department of a ticket, through its Project (the API filters the queue by Department). */
export function departmentOf(ticket: ReviewQueueItem): number | undefined {
  return MOCK_PROJECTS.find((p) => p.projectId === ticket.project.projectId)?.departmentId;
}
