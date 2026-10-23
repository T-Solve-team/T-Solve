import { paths } from '@/shared/routing/paths';
import type { Role } from './roles';

/** Landing page by role, in the order that decides it (brief §3, decision D4): the first role the user has wins. */
const LANDING_ORDER: readonly (readonly [Role, string])[] = [
  ['ADMIN', paths.userList],
  ['PROJECT_MANAGER', paths.reviewQueue],
  ['KNOWLEDGE_MANAGER', paths.wikiHome],
  ['TICKET_RESOLVER', paths.search],
];

/** Where a user lands after sign-in and on "/" (AC-003.1); an Employee-only user lands on the Knowledge Chatbot. */
export function landingPath(roles: readonly Role[]): string {
  return LANDING_ORDER.find(([role]) => roles.includes(role))?.[1] ?? paths.chatbot;
}
