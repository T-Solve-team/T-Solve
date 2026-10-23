import type { Role } from './roles';

/**
 * DEV ONLY (mock mode). Which fixture user the MSW `/api/v1/me` mock returns (src/mocks/session.ts): the dev user
 * switcher and the test helpers write it, the mock reads it. With a real backend it does nothing — the session
 * cookie decides who is signed in.
 */
export const DEV_USERS = {
  employee: 'Employee',
  tr: 'Ticket Resolver',
  'tr-two-departments': 'Ticket Resolver (two Departments)',
  pm: 'Project Manager',
  km: 'Knowledge Manager',
  admin: 'Admin',
  'pm-km': 'Project Manager + Knowledge Manager',
  'admin-tr': 'Admin + Ticket Resolver',
} as const;
export type DevUserId = keyof typeof DEV_USERS;

/** The fixture user that has exactly this role (plus Employee). */
export const DEV_USER_FOR_ROLE: Record<Role, DevUserId> = {
  EMPLOYEE: 'employee',
  TICKET_RESOLVER: 'tr',
  PROJECT_MANAGER: 'pm',
  KNOWLEDGE_MANAGER: 'km',
  ADMIN: 'admin',
};

const STORAGE_KEY = 'tsolve.dev.user';
const DEFAULT_DEV_USER: DevUserId = 'pm';

function isDevUserId(value: string | null): value is DevUserId {
  return value !== null && Object.hasOwn(DEV_USERS, value);
}

/** The dev user, or null when "signed out". */
export function getDevUser(): DevUserId | null {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (stored === '') return null;
    return isDevUserId(stored) ? stored : DEFAULT_DEV_USER;
  } catch {
    return DEFAULT_DEV_USER;
  }
}

export function setDevUser(user: DevUserId | null): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, user ?? '');
  } catch {
    // Storage blocked (private mode): the default user stays in use.
  }
}
