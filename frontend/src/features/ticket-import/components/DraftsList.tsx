import { useQueryClient } from '@tanstack/react-query';
import { Button, Flex, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import {
  getListImportDraftsQueryKey,
  useDeleteImportDraft,
  useListImportDrafts,
  type ImportDraftSummary,
  type ImportSource,
} from '@/shared/api';
import { paths } from '@/shared/routing';
import { cells, ConfirmDialog, DataTable, type DataTableColumn } from '@/shared/ui';
import { useManagedProjectOptions } from '../hooks/useManagedProjects';

/** "Type (Jira / CSV / Manual)" of SRS III.5.1 Drafts. */
const TYPE_LABELS: Record<ImportSource, string> = { JIRA_IMPORT: 'Jira', CSV: 'CSV' };

/**
 * Drafts of 5.1 Import Tickets — SRS III.5.1, AC-004.2…AC-004.5: the caller's own Drafts, newest first; Open goes
 * to the Import Preview, Delete asks MSG42 first.
 */
export function DraftsList() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const projects = useManagedProjectOptions();
  const drafts = useListImportDrafts();
  const [toDelete, setToDelete] = useState<ImportDraftSummary | null>(null);

  // Errors: the global toast.
  const remove = useDeleteImportDraft({
    mutation: {
      onSettled: () => setToDelete(null),
      onSuccess: () => queryClient.invalidateQueries({ queryKey: getListImportDraftsQueryKey() }),
    },
  });

  const columns: DataTableColumn<ImportDraftSummary>[] = [
    { key: 'type', title: 'Type', render: (_, draft) => TYPE_LABELS[draft.source] },
    {
      key: 'project',
      title: 'Project',
      // no title attribute: the Project options of the import forms are found by their title
      ellipsis: { showTitle: false },
      render: (_, draft) =>
        projects.find((project) => project.value === draft.projectId)?.label ?? draft.projectId,
    },
    { key: 'rowCount', title: 'Rows', dataIndex: 'rowCount' },
    { key: 'savedAt', title: 'Date saved', render: (_, draft) => cells.dateTime(draft.savedAt) },
    {
      key: 'actions',
      title: '',
      render: (_, draft) => (
        <Flex gap="small">
          <Button
            type="link"
            onClick={() => void navigate(`${paths.importPreview}?previewId=${draft.previewId}`)}
          >
            Open
          </Button>
          <Button type="link" danger onClick={() => setToDelete(draft)}>
            Delete
          </Button>
        </Flex>
      ),
    },
  ];

  return (
    <Flex vertical gap="middle">
      <Typography.Title level={5}>Drafts</Typography.Title>
      <DataTable<ImportDraftSummary>
        label="Drafts"
        columns={columns}
        rows={drafts.data}
        rowKey={(draft) => draft.previewId}
        loading={drafts.isPending}
        error={drafts.error}
        onRetry={() => void drafts.refetch()}
      />
      <ConfirmDialog
        isOpen={toDelete !== null}
        code="MSG42"
        isDanger
        okText="Delete"
        isConfirming={remove.isPending}
        onConfirm={() => toDelete && remove.mutate({ previewId: toDelete.previewId })}
        onCancel={() => setToDelete(null)}
      />
    </Flex>
  );
}
