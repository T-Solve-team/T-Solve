import {
  ApiOutlined,
  CheckSquareOutlined,
  ClockCircleOutlined,
  DashboardOutlined,
  FileDoneOutlined,
  FileSearchOutlined,
  HistoryOutlined,
  ImportOutlined,
  SearchOutlined,
  SettingOutlined,
  TeamOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import type { ComponentType } from 'react';
import type { Role } from '@/shared/auth';
import { paths } from '@/shared/routing';

export type MenuEntry = { label: string; path: string; icon: ComponentType };

const REVIEW_QUEUE = { label: 'Review Queue', path: paths.reviewQueue, icon: CheckSquareOutlined };
const REVIEW_HISTORY = {
  label: 'Review History',
  path: paths.reviewHistory,
  icon: FileDoneOutlined,
};
const EXPIRED_TICKETS = {
  label: 'Expired Tickets',
  path: paths.expiredTickets,
  icon: ClockCircleOutlined,
};
const SEARCH = { label: 'Search', path: paths.search, icon: SearchOutlined };
const TICKET_LIST = { label: 'Ticket List', path: paths.ticketList, icon: UnorderedListOutlined };
const IMPORT_TICKETS = { label: 'Import Tickets', path: paths.importTickets, icon: ImportOutlined };
const IMPORT_HISTORY = {
  label: 'Import History',
  path: paths.importHistory,
  icon: HistoryOutlined,
};
const DASHBOARD = {
  label: 'Knowledge Dashboard',
  path: paths.knowledgeDashboard,
  icon: DashboardOutlined,
};
const JIRA_INTEGRATION = {
  label: 'Jira Integration',
  path: paths.jiraIntegration,
  icon: ApiOutlined,
};
const USER_LIST = { label: 'User List', path: paths.userList, icon: TeamOutlined };
const WORKSPACE_SETTINGS = {
  label: 'Workspace Settings',
  path: paths.workspaceSettings,
  icon: SettingOutlined,
};
const AUDIT_LOG = { label: 'Audit Log', path: paths.auditLog, icon: FileSearchOutlined };

/**
 * Menus per role — UI design decisions of 04/10 (Figma "Web/App Shell"), landing page first.
 * Pre-filled; changes need a UI-decision change first (guideline 07 §5).
 */
export const MENUS: Record<Role, readonly MenuEntry[]> = {
  // Employee and Knowledge Manager items (Wiki, Knowledge Chatbot) come with their routes (KAN-61).
  EMPLOYEE: [],
  TICKET_RESOLVER: [SEARCH, TICKET_LIST],
  PROJECT_MANAGER: [
    REVIEW_QUEUE,
    REVIEW_HISTORY,
    EXPIRED_TICKETS,
    SEARCH,
    TICKET_LIST,
    IMPORT_TICKETS,
    IMPORT_HISTORY,
    DASHBOARD,
    JIRA_INTEGRATION,
  ],
  KNOWLEDGE_MANAGER: [],
  ADMIN: [USER_LIST, WORKSPACE_SETTINGS, JIRA_INTEGRATION, AUDIT_LOG, DASHBOARD],
};

/**
 * The menu item to highlight: the longest menu path the current path starts with, so detail screens
 * highlight their list (Ticket Detail → Ticket List). Null when none matches (My Profile).
 */
export function selectedMenuPath(entries: readonly MenuEntry[], pathname: string): string | null {
  const matches = entries
    .map((entry) => entry.path)
    .filter((path) => pathname === path || pathname.startsWith(`${path}/`));
  return matches.sort((a, b) => b.length - a.length)[0] ?? null;
}
