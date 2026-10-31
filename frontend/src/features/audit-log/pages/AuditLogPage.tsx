/** 9.2 Audit Log — SRS III.9.2 */
import { Flex } from 'antd';
import type { AuditEntry } from '@/shared/api';
import { useListSearchParams } from '@/shared/routing';
import { cells, DataTable, LoadingState, type DataTableColumn } from '@/shared/ui';
import { AuditLogToolbar } from '../components/AuditLogToolbar';
import { ACTION_LABELS, dateRange, isAuditAction } from '../hooks/audit-filters';
import { useAuditLog } from '../hooks/useAuditLog';

const COLUMNS: DataTableColumn<AuditEntry>[] = [
  {
    key: 'createdAt',
    title: 'Date and time',
    width: 170,
    sortable: true,
    render: (_, entry) => cells.dateTime(entry.createdAt),
  },
  { key: 'actor', title: 'Actor', width: 250, render: (_, entry) => cells.text(entry.actor) },
  {
    key: 'action',
    title: 'Action',
    width: 200,
    render: (_, entry) => cells.text(ACTION_LABELS[entry.action]),
  },
  {
    key: 'target',
    title: 'Target',
    width: 310,
    render: (_, entry) => cells.text(`${entry.resourceType} · ${entry.resourceId}`),
  },
  { key: 'summary', title: 'Summary', width: 280, render: (_, entry) => cells.text(entry.summary) },
];

export function AuditLogPage() {
  const list = useListSearchParams({ field: 'createdAt', order: 'desc' });
  const range = dateRange(list.getFilter('from'), list.getFilter('to'));
  const action = list.getFilter('action');
  const audit = useAuditLog(
    {
      actor: list.getFilter('actor')?.trim() || undefined,
      action: isAuditAction(action) ? action : undefined,
      from: range.from,
      to: range.to,
      ...list.apiPaging,
      sort: [`createdAt,${list.sort.order}`],
    },
    range.valid,
  );

  return (
    <Flex vertical gap="large">
      <AuditLogToolbar />
      {range.valid &&
        (audit.isPending ? (
          <LoadingState />
        ) : (
          <DataTable<AuditEntry>
            label="Audit log"
            columns={COLUMNS}
            rows={audit.data?.content}
            rowKey={(entry) => entry.id}
            loading={audit.isFetching}
            error={audit.isError ? audit.error : null}
            onRetry={() => void audit.refetch()}
            pagination={{
              page: list.page,
              total: audit.data?.page.totalElements ?? 0,
              onChange: list.setPage,
            }}
            sort={{ field: 'createdAt', order: list.sort.order }}
            onSortChange={(next) => list.setSort(next ?? { field: 'createdAt', order: 'asc' })}
            scrollX={1210}
          />
        ))}
    </Flex>
  );
}

export default AuditLogPage;
