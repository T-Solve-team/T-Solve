import type { ProjectRole } from '@/shared/api';

/** UI text for a user's role inside a Project (decision Q3 B: the Member role shows as "Ticket Resolver"). */
export const PROJECT_ROLE_LABELS: Record<ProjectRole, string> = {
  PROJECT_MANAGER: 'Project Manager',
  MEMBER: 'Ticket Resolver',
};
