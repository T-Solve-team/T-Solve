import { useSession } from '@/shared/auth';

/** The Projects the user manages, as Select options "ITSUP · IT" (SRS III.5.1 Project field). */
export function useManagedProjectOptions() {
  const { session } = useSession();
  return (session?.projects ?? [])
    .filter((project) => project.roleInProject === 'PROJECT_MANAGER')
    .map((project) => ({
      value: project.projectId,
      label: `${project.jiraProjectKey} · ${project.departmentName}`,
    }));
}
