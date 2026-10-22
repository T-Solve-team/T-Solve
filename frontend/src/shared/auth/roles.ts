import type { Role as ApiRole } from '@/shared/api';

/**
 * The five roles (Report3 4.1, glossary "Role"). Values are the API spellings (guideline 03 §5). A user can hold
 * several; every user has EMPLOYEE (GBR-28).
 */
export const ROLES = [
  'EMPLOYEE',
  'TICKET_RESOLVER',
  'PROJECT_MANAGER',
  'KNOWLEDGE_MANAGER',
  'ADMIN',
] as const satisfies readonly ApiRole[];
export type Role = (typeof ROLES)[number];

/** UI text for each role — exactly as in Report3. */
export const ROLE_LABELS: Record<Role, string> = {
  EMPLOYEE: 'Employee',
  TICKET_RESOLVER: 'Ticket Resolver',
  PROJECT_MANAGER: 'Project Manager',
  KNOWLEDGE_MANAGER: 'Knowledge Manager',
  ADMIN: 'Admin',
};

export function isRole(value: string): value is Role {
  return (ROLES as readonly string[]).includes(value);
}
