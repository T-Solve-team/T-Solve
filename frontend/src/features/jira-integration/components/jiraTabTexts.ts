// Web app labels, not SRS messages (AC-011.8 to AC-011.10 — user, 2026-11-18).
export const JIRA_TAB_TEXTS = {
  notAvailable: 'Suggestions from Jira are not available for this Project.',
  groups: (names: readonly string[]) =>
    `This role also contains ${names.join(', ')}; their members are not listed.`,
  reasons: {
    NOT_LINKED: 'Not linked to T-Solve',
    NOT_TICKET_RESOLVER: 'Not a Ticket Resolver',
    NOT_ACTIVE: 'Account not active',
    ALREADY_MEMBER: 'Already a member',
  },
} as const;
