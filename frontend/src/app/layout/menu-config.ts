import {
  ApiOutlined,
  BranchesOutlined,
  CheckSquareOutlined,
  ClockCircleOutlined,
  DashboardOutlined,
  FileDoneOutlined,
  FileSearchOutlined,
  HistoryOutlined,
  ImportOutlined,
  MessageOutlined,
  ReadOutlined,
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
const TRIAGE_HISTORY = {
  label: 'Triage History',
  path: paths.triageHistory,
  icon: BranchesOutlined,
};
const WIKI = { label: 'Wiki', path: paths.wikiHome, icon: ReadOutlined };
const KNOWLEDGE_CHATBOT = {
  label: 'Knowledge Chatbot',
  path: paths.chatbot,
  icon: MessageOutlined,
};

/**
 * Menus per role — UI design v3 decisions (brief §3, Figma "Web/App Shell" kit v4), landing page first.
 * Pre-filled; changes need a UI-decision change first (guideline 07 §5).
 */
export const MENUS: Record<Role, readonly MenuEntry[]> = {
  EMPLOYEE: [KNOWLEDGE_CHATBOT, WIKI],
  TICKET_RESOLVER: [SEARCH, TICKET_LIST],
  PROJECT_MANAGER: [
    REVIEW_QUEUE,
    EXPIRED_TICKETS,
    REVIEW_HISTORY,
    SEARCH,
    TICKET_LIST,
    IMPORT_TICKETS,
    IMPORT_HISTORY,
    TRIAGE_HISTORY,
    DASHBOARD,
    JIRA_INTEGRATION,
  ],
  KNOWLEDGE_MANAGER: [WIKI, KNOWLEDGE_CHATBOT],
  ADMIN: [USER_LIST, WORKSPACE_SETTINGS, JIRA_INTEGRATION, DASHBOARD, AUDIT_LOG],
};

/** The order in which a user's role menus are joined (D4): the landing role first, Employee items last. */
const MENU_ORDER: readonly Role[] = [
  'ADMIN',
  'PROJECT_MANAGER',
  'KNOWLEDGE_MANAGER',
  'TICKET_RESOLVER',
  'EMPLOYEE',
];

/** One menu for all of a user's roles: their role menus in MENU_ORDER, each item once (AC-004.2). */
export function menuFor(roles: readonly Role[]): readonly MenuEntry[] {
  const entries = MENU_ORDER.filter((role) => roles.includes(role)).flatMap((role) => MENUS[role]);
  return entries.filter(
    (entry, index) => entries.findIndex((e) => e.path === entry.path) === index,
  );
}

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
