/** 3.1 Jira Integration — SRS III.3.1 */
import { useSession } from '@/shared/auth';
import { ScreenPlaceholder } from '@/shared/ui';
import { DepartmentProjects } from '../components/DepartmentProjects';

export function JiraIntegrationPage() {
  const { hasRole } = useSession();
  // An Admin-only session keeps the placeholder and never mounts the Projects query (AC-005.9).
  if (!hasRole('PROJECT_MANAGER'))
    return <ScreenPlaceholder number="3.1" name="Jira Integration" />;
  return <DepartmentProjects />;
}

export default JiraIntegrationPage;
