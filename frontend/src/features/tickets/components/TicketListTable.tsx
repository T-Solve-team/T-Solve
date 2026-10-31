import type { ApiError, TicketSummary } from '@/shared/api';
import { TICKET_SOURCE_LABELS, type TableSort } from '@/shared/lib';
import { paths } from '@/shared/routing';
import { cells, DataTable, type DataTableColumn } from '@/shared/ui';

/** Figma 6.2 columns and widths (callout 3). Sort keys are the API field names. */
const COLUMNS: DataTableColumn<TicketSummary>[] = [
  {
    key: 'sourceTicketId',
    title: 'Ticket ID',
    width: 110,
    sortable: true,
    render: (_, t) => cells.link(paths.ticketDetail(t.ticketId), t.sourceTicketId),
  },
  {
    key: 'title',
    title: 'Title',
    width: 212,
    sortable: true,
    render: (_, t) => cells.text(t.title),
  },
  {
    key: 'project',
    title: 'Project',
    width: 110,
    sortable: true,
    render: (_, t) => cells.text(t.project.name),
  },
  {
    key: 'department',
    title: 'Department',
    width: 100,
    render: (_, t) => cells.text(t.department.name),
  },
  {
    key: 'source',
    title: 'Source',
    width: 110,
    render: (_, t) => TICKET_SOURCE_LABELS[t.source],
  },
  {
    key: 'status',
    title: 'Status',
    width: 150,
    render: (_, t) => cells.tag(t.status),
  },
  {
    key: 'reviewDueDate',
    title: 'Review due date',
    width: 150,
    sortable: true,
    render: (_, t) => cells.dateTime(t.reviewDueDate) || '—', // only while in review (spec AC-001.3)
  },
  {
    key: 'updatedAt',
    title: 'Updated',
    width: 150,
    sortable: true,
    render: (_, t) => cells.dateTime(t.updatedAt),
  },
];
const TABLE_WIDTH = 110 + 212 + 110 + 100 + 110 + 150 + 150 + 150; // Figma: 1092

type TicketListTableProps = {
  rows: TicketSummary[] | undefined;
  total: number;
  isLoading: boolean;
  error: ApiError | null;
  onRetry: () => void;
  page: number;
  onPageChange: (page: number) => void;
  sort: TableSort;
  onSortChange: (sort: TableSort | null) => void;
};

/** The ticket table of the Ticket List. */
export function TicketListTable({
  rows,
  total,
  isLoading,
  error,
  onRetry,
  page,
  onPageChange,
  sort,
  onSortChange,
}: TicketListTableProps) {
  return (
    <DataTable<TicketSummary>
      label="Tickets"
      columns={COLUMNS}
      rows={rows}
      rowKey={(t) => t.ticketId}
      loading={isLoading}
      error={error}
      onRetry={onRetry}
      pagination={{ page, total, onChange: onPageChange }}
      sort={sort}
      onSortChange={onSortChange}
      scrollX={TABLE_WIDTH}
    />
  );
}
