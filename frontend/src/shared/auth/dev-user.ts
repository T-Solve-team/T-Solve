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

/**
 * DEV ONLY (mock mode). What the Workspace site answers instead of a user: every mock API request then answers
 * MSG119 (unknown site, 404) or MSG120 (suspended Workspace, 403) — see src/mocks/session.ts. Stored under the
 * same key as a fixture user id.
 */
export const DEV_SITES = {
  'unknown-site': 'Unknown site',
  'suspended-workspace': 'Suspended Workspace',
} as const;
export type DevSiteId = keyof typeof DEV_SITES;

const STORAGE_KEY = 'tsolve.dev.user';
const DEFAULT_DEV_USER: DevUserId = 'pm';

function isDevSiteId(value: string | null): value is DevSiteId {
  return value !== null && Object.hasOwn(DEV_SITES, value);
}

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
  store(user ?? '');
}

/** The "site" the dev user switcher picked ("Unknown site", "Suspended Workspace"), or null for a normal site. */
export function getDevSite(): DevSiteId | null {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return isDevSiteId(stored) ? stored : null;
  } catch {
    return null;
  }
}

export function setDevSite(site: DevSiteId): void {
  store(site);
}

function store(value: string): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, value);
  } catch {
    // Storage blocked (private mode): the default user stays in use.
  }
}
