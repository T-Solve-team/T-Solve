/** 11.5 Space Settings — Report3 UC-45–UC-47 (11.7 in the route map). */
import { Tabs } from 'antd';
import { useParams, useSearchParams } from 'react-router';
import { ApiError, useListWikiSpaces } from '@/shared/api';
import { ErrorState, LoadingState } from '@/shared/ui';
import { TrashTable } from '../components/TrashTable';

export function SpaceSettingsPage() {
  const { spaceId = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const spaces = useListWikiSpaces();
  const isManager = spaces.data?.some(
    (space) => space.spaceId === spaceId && space.accessLevel === 'KNOWLEDGE_MANAGER',
  );
  const tab = params.get('tab') === 'trash' ? 'trash' : 'permissions';
  if (spaces.isPending) return <LoadingState />;
  if (spaces.isError)
    return <ErrorState error={spaces.error} onRetry={() => void spaces.refetch()} />;
  if (!isManager) return <ErrorState error={new ApiError({ status: 403, code: 'MSG08' })} />;

  return (
    <Tabs
      activeKey={tab}
      onChange={(tab) =>
        setParams((current) => {
          const next = new URLSearchParams(current);
          next.set('tab', tab);
          return next;
        })
      }
      items={[
        { key: 'permissions', label: 'Permissions' },
        {
          key: 'trash',
          label: 'Trash',
          children: tab === 'trash' ? <TrashTable key={spaceId} spaceId={spaceId} /> : null,
        },
      ]}
    />
  );
}

export default SpaceSettingsPage;
