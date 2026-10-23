import type { CurrentUser } from '@/shared/api';
import { isRole, type Role } from './roles';

/**
 * Who is signed in: the generated `GET /api/v1/me` response, with `roles` normalized — EMPLOYEE is always there
 * (GBR-28) and a role this build does not know is left out.
 */
export type Session = Omit<CurrentUser, 'roles'> & { roles: readonly Role[] };

/** What `/me` can really send: a newer backend may send a role this build does not know yet. */
type MeResponse = Omit<CurrentUser, 'roles'> & { roles: readonly string[] };

export function toSession(user: MeResponse): Session {
  const known = user.roles.filter(isRole);
  return { ...user, roles: known.includes('EMPLOYEE') ? known : ['EMPLOYEE', ...known] };
}
