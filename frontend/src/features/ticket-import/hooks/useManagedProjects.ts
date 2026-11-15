import { useSession } from '@/shared/auth';

/** The Active Projects the user manages, as Select options "ITSUP · IT" (SRS III.5.1 Project field, BUG-216). */
export function useManagedProjectOptions() {
  const { session } = useSession();
  return (session?.projects ?? [])
    .filter(
      (project) =>
        project.roleInProject === 'PROJECT_MANAGER' && project.projectStatus === 'ACTIVE',
    )
    .map((project) => ({
      value: project.projectId,
      label: `${project.jiraProjectKey} · ${project.departmentName}`,
    }));
}
