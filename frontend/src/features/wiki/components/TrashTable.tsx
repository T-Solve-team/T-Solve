import { Alert, Button, Flex, Space } from 'antd';
import { useState } from 'react';
import { useListWikiTrash, type WikiTrashItem } from '@/shared/api';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { cells, ConfirmDialog, DataTable, LoadingState, type DataTableColumn } from '@/shared/ui';
import { useWikiTrashMutations } from '../hooks/useWikiTrashMutations';

type TrashTableProps = { spaceId: string };

export function TrashTable({ spaceId }: TrashTableProps) {
  const trash = useListWikiTrash(spaceId);
  const { restore, permanent } = useWikiTrashMutations(spaceId);
  const [selected, setSelected] = useState<WikiTrashItem | null>(null);
  const isBusy = restore.isPending || permanent.isPending;
  const columns: DataTableColumn<WikiTrashItem>[] = [
    {
      key: 'title',
      title: 'Title',
      width: 260,
      render: (_, row) => cells.link(paths.wikiDocument(row.documentId), row.title),
    },
    {
      key: 'folderName',
      title: 'Folder',
      width: 150,
      render: (_, row) => cells.text(row.folderName ?? '—'),
    },
    { key: 'owner', title: 'Owner', width: 180, render: (_, row) => cells.text(row.owner) },
    {
      key: 'deletedBy',
      title: 'Deleted by',
      width: 180,
      render: (_, row) => cells.text(row.deletedBy),
    },
    {
      key: 'deletedAt',
      title: 'Deleted on',
      width: 170,
      render: (_, row) => cells.dateTime(row.deletedAt),
    },
    {
      key: 'actions',
      title: 'Actions',
      width: 260,
      render: (_, row) => (
        <Space>
          <Button
            disabled={isBusy}
            loading={restore.isPending && restore.variables?.documentId === row.documentId}
            onClick={() => {
              if (!isBusy) restore.mutate({ documentId: row.documentId });
            }}
          >
            Restore
          </Button>
          <Button
            danger
            disabled={isBusy}
            onClick={() => {
              // The MSG84 notice belongs to the last restore only (BUG-306).
              restore.reset();
              setSelected(row);
            }}
          >
            Delete permanently
          </Button>
        </Space>
      ),
    },
  ];

  if (trash.isPending) return <LoadingState />;
  return (
    <Flex vertical gap="middle">
      {restore.isSuccess && restore.data.renamed && (
        <Alert type="info" showIcon title={msg('MSG84')} />
      )}
      <DataTable
        label="Trash"
        columns={columns}
        rows={trash.data}
        rowKey={(row) => row.documentId}
        error={trash.error}
        onRetry={() => void trash.refetch()}
        scrollX={1200}
      />
      {selected && (
        <ConfirmDialog
          isOpen
          code="MSG103"
          params={{ title: selected.title }}
          isDanger
          isConfirming={permanent.isPending}
          onCancel={() => {
            if (!permanent.isPending) setSelected(null);
          }}
          onConfirm={() => {
            if (!selected || isBusy) return;
            permanent.mutate(
              { documentId: selected.documentId },
              { onSuccess: () => setSelected(null) },
            );
          }}
        />
      )}
    </Flex>
  );
}
