/**
 * Every web screen path (guideline 08 §1): all 26 routed screens of the 34 — features never add paths elsewhere.
 * Detail builders return the route pattern when called without an id, e.g. `paths.ticketDetail()` →
 * '/tickets/:ticketId', and a real link with one: `paths.ticketDetail(42)` → '/tickets/42'.
 * Popups (1.3.3, 2.4, 5.3, 7.2, 7.3, 8.1, 10.2, 11.5) have no path.
 */
type Id = string | number;

export const paths = {
  home: '/',
  login: '/login', // 1.1.1
  profile: '/profile', // 1.2.1
  userList: '/users', // 1.3.1
  userNew: '/users/new', // 1.3.2 (add mode)
  userDetail: (userId: Id = ':userId') => `/users/${userId}`, // 1.3.2
  workspaceSetup: '/workspace-setup', // 2.1
  workspaceSettings: '/workspace-settings', // 2.2
  departmentDetail: (departmentId: Id = ':departmentId') =>
    `/workspace-settings/departments/${departmentId}`, // 2.3
  jiraIntegration: '/jira-integration', // 3.1
  projectDetail: (projectId: Id = ':projectId') => `/jira-integration/projects/${projectId}`, // 3.2
  importTickets: '/import', // 5.1 (?tab=jira|csv|manual)
  importPreview: '/import/preview', // 5.2
  importHistory: '/import/history', // 5.4
  ticketDetail: (ticketId: Id = ':ticketId') => `/tickets/${ticketId}`, // 5.5
  search: '/search', // 6.1
  ticketList: '/tickets', // 6.2
  reviewQueue: '/review-queue', // 7.1
  reviewHistory: '/review-history', // 7.4
  expiredTickets: '/expired-tickets', // 7.5
  knowledgeDashboard: '/dashboard', // 9.1
  auditLog: '/audit-log', // 9.2
  triageHistory: '/triage-history', // 10.1
  wikiHome: '/wiki', // 11.1 (?space=&folder=)
  wikiDocument: (documentId: Id = ':documentId') => `/wiki/documents/${documentId}`, // 11.2
  wikiUpload: '/wiki/upload', // 11.4
  spaceSettings: (spaceId: Id = ':spaceId') => `/wiki/spaces/${spaceId}/settings`, // 11.7
  chatbot: '/chatbot', // 12.1
} as const;
