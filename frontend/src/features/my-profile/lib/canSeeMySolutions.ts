import type { Role } from '@/shared/auth';

/** The My Solutions card is for Ticket Resolvers and Project Managers only (AC-003.20). */
export function canSeeMySolutions(roles: readonly Role[]): boolean {
  return roles.includes('TICKET_RESOLVER') || roles.includes('PROJECT_MANAGER');
}
