/** 1.2.1 My Profile — SRS III.1.2.1 */
import { Flex } from 'antd';
import { useSession } from '@/shared/auth';
import { LoadingState } from '@/shared/ui';
import { AccountSection } from '../components/AccountSection';
import { ProjectsSection } from '../components/ProjectsSection';
import { SpacesSection } from '../components/SpacesSection';

export function MyProfilePage() {
  const { session } = useSession();
  if (!session) return <LoadingState />;
  return (
    <Flex vertical gap="middle">
      <AccountSection session={session} />
      <ProjectsSection projects={session.projects} />
      <SpacesSection spaces={session.managedSpaces} />
    </Flex>
  );
}

export default MyProfilePage;
