/**
 * Figma "Web/Status Tag": the only place status colours and labels are defined (guideline 07 §3).
 * Keys are the API values; one key per Figma variant (Deferred, Running and Succeeded are retired).
 */
export const STATUS_TAGS = {
  // Ticket (Report3 GBR-07: six statuses)
  PENDING_REVIEW: { label: 'Pending review', color: 'blue' },
  CHANGES_REQUESTED: { label: 'Changes requested', color: 'orange' },
  PUBLISHED: { label: 'Published', color: 'green' },
  REJECTED: { label: 'Rejected', color: 'red' },
  UNPUBLISHED: { label: 'Unpublished', color: 'default' },
  EXPIRED: { label: 'Expired', color: 'purple' },
  // User account / Project
  ACTIVE: { label: 'Active', color: 'green' },
  DEACTIVATED: { label: 'Deactivated', color: 'default' },
  REMOVED: { label: 'Removed', color: 'default' },
  // Jira connection
  CONNECTED: { label: 'Connected', color: 'green' },
  NOT_CONNECTED: { label: 'Not connected', color: 'red' },
  // Import run / CSV row
  COMPLETED: { label: 'Completed', color: 'green' },
  FAILED: { label: 'Failed', color: 'red' },
  VALID: { label: 'Valid', color: 'green' },
  ERROR: { label: 'Error', color: 'red' },
  // AI Ticket Triage class (GBR-35). Colours are placeholders until the Figma kit sets them (design).
  SIMPLE: { label: 'Simple', color: 'green' },
  COMPLEX: { label: 'Complex', color: 'orange' },
} as const;

export type StatusTagStatus = keyof typeof STATUS_TAGS;
