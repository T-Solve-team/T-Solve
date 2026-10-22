import { paths } from '@/shared/routing/paths';
import type { Role } from './roles';

/**
 * Landing page per role. Knowledge Manager and Employee land on My Profile until their screens (Wiki Home,
 * Knowledge Chatbot) are routed — KAN-60 / KAN-61 replace this with the Report3 landing order (AC-003.1).
 */
export const LANDING_PATHS: Record<Role, string> = {
  ADMIN: paths.userList,
  PROJECT_MANAGER: paths.reviewQueue,
  KNOWLEDGE_MANAGER: paths.profile,
  TICKET_RESOLVER: paths.search,
  EMPLOYEE: paths.profile,
};

const MAIN_ROLE_ORDER: readonly Role[] = [
  'ADMIN',
  'PROJECT_MANAGER',
  'KNOWLEDGE_MANAGER',
  'TICKET_RESOLVER',
  'EMPLOYEE',
];

/** The role whose landing page and menu a user with several roles gets (until KAN-60 merges the menus). */
export function mainRole(roles: readonly Role[]): Role {
  return MAIN_ROLE_ORDER.find((role) => roles.includes(role)) ?? 'EMPLOYEE';
}
