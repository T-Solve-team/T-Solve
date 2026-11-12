/** 2.2 Workspace Settings — Report3 UC-11; SRS v3460 layout */
import { Flex } from 'antd';
import { useGetWorkspace } from '@/shared/api';
import { ErrorState, LoadingState, ScreenPlaceholder, SectionCard } from '@/shared/ui';
import { WorkspaceInfoForm } from '../components/WorkspaceInfoForm';

export function WorkspaceSettingsPage() {
  const query = useGetWorkspace();

  if (query.isPending) return <LoadingState />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  return (
    <Flex vertical gap="large">
      <WorkspaceInfoForm workspace={query.data} />
      {/* TEMPORARY — the Departments section of 2.2 is KAN-120, not built yet. */}
      <SectionCard title="Departments">
        <ScreenPlaceholder number="2.2" name="Workspace Settings" />
      </SectionCard>
    </Flex>
  );
}

export default WorkspaceSettingsPage;
