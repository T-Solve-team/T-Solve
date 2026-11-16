import type { ApiError, WikiDocumentSummary } from '@/shared/api';
import { paths } from '@/shared/routing';
import { cells, DataTable, type DataTableColumn } from '@/shared/ui';

const COLUMNS: DataTableColumn<WikiDocumentSummary>[] = [
  {
    key: 'title',
    title: 'Title',
    width: 260,
    render: (_, row) => cells.link(paths.wikiDocument(row.documentId), row.title),
  },
  {
    key: 'folderName',
    title: 'Folder',
    width: 160,
    render: (_, row) => cells.text(row.folderName ?? '—'),
  },
  {
    key: 'visibility',
    title: 'Visibility',
    width: 150,
    render: (_, row) => (row.visibility === 'COMPANY_WIDE' ? 'Company-wide' : 'Department'),
  },
  { key: 'owner', title: 'Owner', width: 180, render: (_, row) => cells.text(row.owner) },
  {
    key: 'updatedAt',
    title: 'Updated',
    width: 170,
    render: (_, row) => cells.dateTime(row.updatedAt),
  },
];

type DocumentsTableProps = {
  label?: string;
  rows: WikiDocumentSummary[] | undefined;
  isLoading?: boolean;
  error?: ApiError | null;
  onRetry?: () => void;
};

export function DocumentsTable({
  label = 'Documents',
  rows,
  isLoading,
  error,
  onRetry,
}: DocumentsTableProps) {
  return (
    <DataTable
      label={label}
      columns={COLUMNS}
      rows={rows}
      rowKey={(row) => row.documentId}
      loading={isLoading}
      error={error}
      onRetry={onRetry}
      scrollX={920}
    />
  );
}
