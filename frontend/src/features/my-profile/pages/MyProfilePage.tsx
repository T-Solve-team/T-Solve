/** 1.2.1 My Profile — SRS III.1.2.1 */
import { Flex } from 'antd';
import { useSession } from '@/shared/auth';
import { LoadingState } from '@/shared/ui';
import { AccountSection } from '../components/AccountSection';
import { ProjectsSection } from '../components/ProjectsSection';
import { SpacesSection } from '../components/SpacesSection';
import { useMyViewableSpaces } from '../hooks/useMyViewableSpaces';

export function MyProfilePage() {
  const { session } = useSession();
  const viewable = useMyViewableSpaces();
  if (!session) return <LoadingState />;
  return (
    <Flex vertical gap="middle">
      <AccountSection session={session} />
      <ProjectsSection projects={session.projects} />
      <SpacesSection
        managed={session.managedSpaces}
        viewable={{
          spaces: viewable.data,
          loading: viewable.isPending,
          error: viewable.error,
        }}
      />
    </Flex>
  );
}

export default MyProfilePage;
