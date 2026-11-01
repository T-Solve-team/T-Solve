import { Flex, Typography } from 'antd';
import { useState } from 'react';
import type { ImportPreviewRowSummary, RowError } from '@/shared/api';
import { isMsgCode, msg } from '@/shared/messages';
import { cells, DataTable, type DataTableColumn } from '@/shared/ui';
import { useImportPreviewRows } from '../hooks/useImportPreview';

/** The Solution sections in template order (GBR-33). */
const SECTIONS = [
  ['Symptoms', 'symptoms'],
  ['Root cause', 'rootCause'],
  ['Resolution steps', 'resolutionSteps'],
  ['Workaround', 'workaround'],
  ['Scope', 'scope'],
] as const;

/** A code the catalog does not know shows MSG06 (design edge case 38). */
function errorText(error: RowError): string {
  return isMsgCode(error.code) ? msg(error.code, error.params) : msg('MSG06');
}

const COLUMNS: DataTableColumn<ImportPreviewRowSummary>[] = [
  { key: 'ticketId', title: 'Ticket ID', width: 120, render: (_, row) => cells.text(row.ticketId) },
  { key: 'title', title: 'Title', width: 220, render: (_, row) => cells.text(row.title) },
  { key: 'author', title: 'Author', width: 160, render: (_, row) => cells.text(row.author) },
  {
    key: 'resolvedDate',
    title: 'Resolved date',
    width: 120,
    render: (_, row) => cells.text(row.resolvedDate), // as written in the file
  },
  {
    key: 'solution',
    title: 'Solution',
    width: 360,
    ellipsis: false,
    render: (_, row) => (
      <Flex vertical>
        {SECTIONS.map(([label, field]) => {
          const text = row[field];
          return text ? (
            <Typography.Text key={field} ellipsis={{ tooltip: text }}>
              <Typography.Text type="secondary">{label}: </Typography.Text>
              {text}
            </Typography.Text>
          ) : null;
        })}
      </Flex>
    ),
  },
  { key: 'status', title: 'Result', width: 100, render: (_, row) => cells.tag(row.status) },
  {
    key: 'errors',
    title: 'Error details',
    width: 280,
    ellipsis: false,
    render: (_, row) => (
      <Flex vertical>
        {row.errors.map((error, i) => (
          <Typography.Text key={i} type="danger">
            {errorText(error)}
          </Typography.Text>
        ))}
      </Flex>
    ),
  },
];

type ImportPreviewTableProps = {
  previewId: number;
  /** Row numbers of the selected rows, on every page. */
  selectedRowNos: number[];
  onSelectionChange: (rowNos: number[]) => void;
};

/** The rows of 5.2 Import Preview (AC-009.2), 20 per page; Removed rows cannot be selected. */
export function ImportPreviewTable({
  previewId,
  selectedRowNos,
  onSelectionChange,
}: ImportPreviewTableProps) {
  // Local: the page is reached only from Upload and its address carries only the preview id (D11).
  const [page, setPage] = useState(1);
  const rows = useImportPreviewRows(previewId, page);

  return (
    <DataTable<ImportPreviewRowSummary>
      label="Import rows"
      columns={COLUMNS}
      rows={rows.data?.content}
      rowKey={(row) => row.rowNo}
      loading={rows.isPending}
      error={rows.error}
      onRetry={() => void rows.refetch()}
      pagination={{ page, total: rows.data?.page.totalElements ?? 0, onChange: setPage }}
      rowSelection={{
        selectedRowKeys: selectedRowNos,
        preserveSelectedRowKeys: true,
        columnWidth: 48,
        onChange: (keys) => onSelectionChange(keys.map(Number)),
        getCheckboxProps: (row) => ({
          'aria-label': `Select row ${row.rowNo}`,
          disabled: row.status === 'REMOVED',
        }),
      }}
      scrollX={1408}
    />
  );
}
