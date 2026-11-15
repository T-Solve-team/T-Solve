import { useSession } from '@/shared/auth';

/**
 * The Projects the user manages, as Select options "ITSUP · IT" (SRS III.5.1 Project field). Only Active ones for a
 * new import (BUG-216); the Drafts list passes `anyStatus` to keep the Drafts of a REMOVED Project (BUG-217).
 */
export function useManagedProjectOptions({ anyStatus = false }: { anyStatus?: boolean } = {}) {
  const { session } = useSession();
  return (session?.projects ?? [])
    .filter(
      (project) =>
        project.roleInProject === 'PROJECT_MANAGER' &&
        (anyStatus || project.projectStatus === 'ACTIVE'),
    )
    .map((project) => ({
      value: project.projectId,
      label: `${project.jiraProjectKey} · ${project.departmentName}`,
    }));
}
