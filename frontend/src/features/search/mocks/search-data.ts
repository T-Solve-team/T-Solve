import type { TicketSearchResult, TicketSource } from '@/shared/api';

/**
 * A mock Published ticket: the API result plus its source and the rest of its Solution text, which only the
 * filters read (spec AC-004.3).
 */
export type MockSearchResult = TicketSearchResult & { source: TicketSource; solution: string };

/**
 * Projects and Departments of the fixture users, with the same UUIDs as src/mocks/session.ts: feature code may not
 * import src/mocks, so keep these in step with it (design "Data Model").
 */
const IT = { departmentId: '00000000-0000-4000-8000-0000000000d1', name: 'IT' };
const HR = { departmentId: '00000000-0000-4000-8000-0000000000d2', name: 'HR' };
const PROJECTS = {
  ITSUP: {
    project: { projectId: '00000000-0000-4000-8000-000000000101', name: 'ITSUP' },
    department: IT,
  },
  ITNET: {
    project: { projectId: '00000000-0000-4000-8000-000000000102', name: 'ITNET' },
    department: IT,
  },
  HRHELP: {
    project: { projectId: '00000000-0000-4000-8000-000000000201', name: 'HRHELP' },
    department: HR,
  },
} as const;

type Row = [
  title: string,
  project: keyof typeof PROJECTS,
  source: TicketSource,
  // Between 00:00 and 16:59 UTC, so the day is the same in UTC and in Vietnam time (the Resolved date filter).
  resolvedAt: string,
  helpfulRate: number | null,
  excerpt: string,
  solution: string,
];

/** The 14 Published tickets, most relevant first; two have no Feedback yet and one has 0% (design edge case #8). */
const ROWS: Row[] = [
  [
    'VPN disconnects every 10 minutes on home Wi-Fi',
    'ITSUP',
    'SOLUTION_FORM',
    '2026-10-02T04:24:00Z',
    92,
    'The VPN client drops the tunnel every 10 minutes on home Wi-Fi. Renew the VPN client certificate and turn off Wi-Fi power saving.',
    "Root cause: the expired client certificate forces a renegotiation that the router's power saving cuts.",
  ],
  [
    'VPN cannot connect after the Windows update',
    'ITSUP',
    'JIRA_IMPORT',
    '2026-09-29T03:10:00Z',
    null,
    'The VPN client shows error 809 after the October Windows update. Reinstall the VPN client and allow UDP 500 and 4500 in the firewall.',
    'Root cause: the update reset the firewall rules of the VPN client.',
  ],
  [
    'Remote desktop over VPN is very slow',
    'ITNET',
    'JIRA_IMPORT',
    '2026-09-15T02:00:00Z',
    0,
    'Remote desktop sessions lag when connected through the VPN. Lower the MTU of the VPN adapter to 1400.',
    'Root cause: packet fragmentation on the VPN tunnel.',
  ],
  [
    'Switch port flapping on floor 4 access switch',
    'ITNET',
    'SOLUTION_FORM',
    '2026-10-01T03:12:00Z',
    75,
    'Users on floor 4 lose the network for a few seconds many times a day. Replace the faulty patch cable and enable port error-disable recovery.',
    'Root cause: a damaged patch cable between the desk and the access switch.',
  ],
  [
    'Shared mailbox not visible in Outlook',
    'ITSUP',
    'JIRA_IMPORT',
    '2026-09-28T01:00:00Z',
    80,
    'A shared mailbox no longer shows in Outlook after a permission change. Grant Full Access again and wait for automapping.',
    'Root cause: removing and adding the permission turned automapping off.',
  ],
  [
    'Teams notifications not showing on Windows',
    'ITSUP',
    'MANUAL',
    '2026-09-27T01:00:00Z',
    67,
    'Teams shows no pop-up for new messages. Turn on Teams in the Windows notification settings.',
    'Root cause: Focus assist was set to Alarms only.',
  ],
  [
    'OneDrive sync stuck at processing changes',
    'ITSUP',
    'CSV',
    '2026-09-26T01:00:00Z',
    50,
    'OneDrive shows "Processing changes" for hours. Reset the OneDrive client and unlink the account.',
    'Root cause: a corrupted sync database.',
  ],
  [
    'Guest Wi-Fi portal does not load',
    'ITNET',
    'CSV',
    '2026-09-23T01:00:00Z',
    null,
    'Guests see a certificate warning and the login page never loads. Renew the captive portal TLS certificate.',
    'Root cause: the portal certificate expired on 22/09.',
  ],
  [
    'Leave balance not updated after approval',
    'HRHELP',
    'JIRA_IMPORT',
    '2026-09-18T01:00:00Z',
    88,
    'An approved leave still shows in the old balance. Trigger the nightly leave sync manually.',
    'Root cause: the nightly sync job failed on a timeout.',
  ],
  [
    'New employee cannot see onboarding checklist',
    'HRHELP',
    'CSV',
    '2026-09-17T01:00:00Z',
    100,
    'A new employee sees an empty home page in the HR portal. Assign the onboarding role in the HR portal.',
    'Root cause: the role is not given when the account is created by import.',
  ],
  [
    'Cannot download tax statement',
    'HRHELP',
    'MANUAL',
    '2026-09-12T01:00:00Z',
    60,
    'Clicking Download tax statement does nothing. Allow pop-ups for the HR portal.',
    'Root cause: the browser blocks the pop-up that opens the PDF.',
  ],
  [
    'Bank account change not saved',
    'HRHELP',
    'SOLUTION_FORM',
    '2026-09-11T01:00:00Z',
    71,
    'The new bank account disappears after saving. Verify the change with the code sent by SMS.',
    'Root cause: the change waits for the SMS verification step.',
  ],
  [
    'Overtime hours missing from the timesheet',
    'HRHELP',
    'JIRA_IMPORT',
    '2026-08-29T02:00:00Z',
    83,
    'Approved overtime does not show in the monthly timesheet. Submit the overtime request again after the manager approves the shift.',
    'Root cause: the overtime was approved before the shift was published.',
  ],
  [
    'Docking station shows no external monitor',
    'ITSUP',
    'MANUAL',
    '2026-08-20T02:00:00Z',
    45,
    'External monitors stay black on the docking station. Update the dock firmware and the DisplayLink driver.',
    'Root cause: an old dock firmware that does not support the new laptops.',
  ],
];

/** A fixed UUID per row, so links and tests stay stable. */
function mockTicketId(index: number): string {
  return `5ea70000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`;
}

/** The 14 mock Published tickets, in the order the search ranks them. */
export const MOCK_SEARCH_RESULTS: readonly MockSearchResult[] = ROWS.map(
  ([title, project, source, resolvedAt, helpfulRate, excerpt, solution], index) => ({
    ticketId: mockTicketId(index),
    title,
    ...PROJECTS[project],
    excerpt,
    helpfulRate,
    resolvedAt,
    source,
    solution,
  }),
);
