/** 11.5 Space Settings — Report3 UC-45–UC-47 (11.7 in the route map). */
import { Button, Descriptions, Flex, Result, Tabs, Typography } from 'antd';
import { useState } from 'react';
import { useParams, useSearchParams } from 'react-router';
import { useGetWikiSpaceSettings, useListWikiSpaces } from '@/shared/api';
import { msg } from '@/shared/messages';
import { ConfirmDialog, ErrorState, LoadingState } from '@/shared/ui';
import { GrantAccessModal } from '../components/GrantAccessModal';
import { PermissionTables } from '../components/PermissionTables';
import { TrashTable } from '../components/TrashTable';
import { useWikiPermissionMutations } from '../hooks/useWikiPermissionMutations';

export function SpaceSettingsPage() {
  const { spaceId = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const spaces = useListWikiSpaces();
  const isManager = spaces.data?.some(
    (space) => space.spaceId === spaceId && space.accessLevel === 'KNOWLEDGE_MANAGER',
  );
  const settings = useGetWikiSpaceSettings(spaceId, { query: { enabled: Boolean(isManager) } });
  const [isGrantOpen, setGrantOpen] = useState(false);
  const [selected, setSelected] = useState<{ permissionId: string; name: string } | null>(null);
  const { revoke } = useWikiPermissionMutations(spaceId);
  const tab = params.get('tab') === 'trash' ? 'trash' : 'permissions';
  if (spaces.isPending) return <LoadingState />;
  if (spaces.isError)
    return <ErrorState error={spaces.error} onRetry={() => void spaces.refetch()} />;
  if (!isManager || settings.error?.status === 403)
    return <Result status="403" title="403" subTitle={msg('MSG08')} />;
  if (settings.isPending) return <LoadingState />;
  if (settings.isError)
    return <ErrorState error={settings.error} onRetry={() => void settings.refetch()} />;
  const space = settings.data;

  return (
    <Flex vertical gap="middle">
      <Typography.Title level={2}>{space.name}</Typography.Title>
      <Descriptions
        items={[
          { key: 'department', label: 'Department', children: space.departmentName },
          {
            key: 'manager',
            label: 'Knowledge Manager',
            children: space.knowledgeManagers.join(', ') || '—',
          },
        ]}
      />
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
          {
            key: 'permissions',
            label: 'Permissions',
            children:
              tab === 'permissions' ? (
                <Flex vertical gap="middle">
                  <Flex justify="end">
                    <Button
                      type="primary"
                      disabled={revoke.isPending}
                      onClick={() => setGrantOpen(true)}
                    >
                      Grant access
                    </Button>
                  </Flex>
                  <PermissionTables
                    settings={space}
                    isBusy={revoke.isPending}
                    onRemove={setSelected}
                  />
                </Flex>
              ) : null,
          },
          {
            key: 'trash',
            label: 'Trash',
            children: tab === 'trash' ? <TrashTable key={spaceId} spaceId={spaceId} /> : null,
          },
        ]}
      />
      {isGrantOpen && (
        <GrantAccessModal key={spaceId} spaceId={spaceId} onClose={() => setGrantOpen(false)} />
      )}
      {selected && (
        <ConfirmDialog
          isOpen
          code="MSG106"
          params={{ grantee: selected.name, space_name: space.name }}
          isDanger
          isConfirming={revoke.isPending}
          onCancel={() => {
            if (!revoke.isPending) setSelected(null);
          }}
          onConfirm={() => {
            if (!revoke.isPending)
              revoke.mutate(
                { permissionId: selected.permissionId },
                { onSuccess: () => setSelected(null) },
              );
          }}
        />
      )}
    </Flex>
  );
}

export default SpaceSettingsPage;
