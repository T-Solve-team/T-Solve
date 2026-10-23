import { Checkbox } from 'antd';
import type { ApiError, ReviewQueueItem } from '@/shared/api';
import { TICKET_SOURCE_LABELS, type TableSort } from '@/shared/lib';
import { paths } from '@/shared/routing';
import { cells, DataTable, type DataTableColumn } from '@/shared/ui';

/** Figma 7.1 columns and widths (SRS 7.1 Ticket table). Sort keys are the API field names. */
const COLUMNS: DataTableColumn<ReviewQueueItem>[] = [
  {
    key: 'sourceTicketId',
    title: 'Ticket ID',
    width: 120,
    sortable: true,
    render: (_, t) => cells.link(paths.ticketDetail(t.ticketId), t.sourceTicketId),
  },
  {
    key: 'title',
    title: 'Title',
    width: 242,
    sortable: true,
    render: (_, t) => cells.text(t.title),
  },
  {
    key: 'project',
    title: 'Project',
    width: 120,
    sortable: true,
    render: (_, t) => cells.text(t.project.name),
  },
  {
    key: 'source',
    title: 'Source',
    width: 120,
    render: (_, t) => TICKET_SOURCE_LABELS[t.source],
  },
  {
    key: 'authorName',
    title: 'Author',
    width: 150,
    sortable: true,
    render: (_, t) => cells.text(t.authorName),
  },
  {
    key: 'enteredQueueAt',
    title: 'Entered queue',
    width: 146,
    sortable: true,
    render: (_, t) => cells.dateTime(t.enteredQueueAt),
  },
  {
    key: 'reviewDueDate',
    title: 'Review due date',
    width: 146,
    sortable: true,
    render: (_, t) => cells.dateTime(t.reviewDueDate),
  },
];
const TABLE_WIDTH = 48 + 120 + 242 + 120 + 120 + 150 + 146 + 146; // Figma: 1092

type ReviewQueueTableProps = {
  rows: ReviewQueueItem[] | undefined;
  total: number;
  isLoading: boolean;
  error: ApiError | null;
  onRetry: () => void;
  page: number;
  onPageChange: (page: number) => void;
  sort: TableSort;
  onSortChange: (sort: TableSort | null) => void;
  selectedIds: number[];
  onSelectionChange: (ids: number[]) => void;
  /** Header checkbox (callout 2/3): every ticket in the queue, on every page (SRS 7.1). */
  onSelectAll: (isChecked: boolean) => void;
};

/** The ticket table of the Review Queue, with row selection across pages. */
export function ReviewQueueTable({
  rows,
  total,
  isLoading,
  error,
  onRetry,
  page,
  onPageChange,
  sort,
  onSortChange,
  selectedIds,
  onSelectionChange,
  onSelectAll,
}: ReviewQueueTableProps) {
  const isAllSelected = total > 0 && selectedIds.length >= total;
  return (
    <DataTable<ReviewQueueItem>
      label="Review Queue tickets"
      columns={COLUMNS}
      rows={rows}
      rowKey={(t) => t.ticketId}
      loading={isLoading}
      error={error}
      onRetry={onRetry}
      emptyCode="MSG13"
      pagination={{ page, total, onChange: onPageChange }}
      sort={sort}
      onSortChange={onSortChange}
      scrollX={TABLE_WIDTH}
      rowSelection={{
        selectedRowKeys: selectedIds,
        preserveSelectedRowKeys: true, // the selection survives paging
        columnWidth: 48,
        onChange: (keys) => onSelectionChange(keys.map(Number)),
        getCheckboxProps: (t) => ({ 'aria-label': `Select ${t.sourceTicketId}` }),
        columnTitle: (
          <Checkbox
            aria-label="Select all"
            checked={isAllSelected}
            indeterminate={selectedIds.length > 0 && !isAllSelected}
            disabled={total === 0}
            onChange={(event) => onSelectAll(event.target.checked)}
          />
        ),
      }}
    />
  );
}
