/** 5.4 Import History — SRS III.5.4 */
import { Button, DatePicker, Flex, Select } from 'antd';
import dayjs from 'dayjs';
import { useSearchParams } from 'react-router';
import {
  ImportSource,
  TicketSource,
  useGetImportHistory,
  type ImportHistoryItem,
} from '@/shared/api';
import { formatDate, TICKET_SOURCE_LABELS } from '@/shared/lib';
import { useListSearchParams } from '@/shared/routing';
import { cells, DataTable, LoadingState, type DataTableColumn } from '@/shared/ui';
import { useManagedProjectOptions } from '../hooks/useManagedProjects';

const COLUMNS: DataTableColumn<ImportHistoryItem>[] = [
  {
    key: 'createdAt',
    title: 'Date',
    width: 170,
    render: (_, row) => cells.dateTime(row.createdAt),
  },
  {
    key: 'performedBy',
    title: 'Project Manager',
    width: 180,
    render: (_, row) => cells.text(row.performedBy),
  },
  {
    key: 'project',
    title: 'Project',
    width: 130,
    render: (_, row) => cells.text(row.project.projectKey),
  },
  {
    key: 'department',
    title: 'Department',
    width: 150,
    render: (_, row) => cells.text(row.department.name),
  },
  {
    key: 'source',
    title: 'Source',
    width: 120,
    render: (_, row) => TICKET_SOURCE_LABELS[row.source],
  },
  {
    key: 'range',
    title: 'Date range / file',
    width: 250,
    render: (_, row) =>
      cells.text(
        row.source === 'JIRA_IMPORT' && row.dateFrom && row.dateTo
          ? `${formatDate(row.dateFrom)} – ${formatDate(row.dateTo)}`
          : row.source === 'CSV'
            ? (row.fileName ?? '—')
            : '—',
      ),
  },
  { key: 'publishedCount', title: 'Published', width: 100, render: (_, row) => row.publishedCount },
  {
    key: 'counts',
    title: 'Removed / errors',
    width: 150,
    render: (_, row) => `${row.removedCount} / ${row.errorCount}`,
  },
  { key: 'status', title: 'Status', width: 120, render: (_, row) => cells.tag(row.status) },
  { key: 'error', title: 'Error', width: 120, render: (_, row) => cells.text(row.errorCode) },
];

export function ImportHistoryPage() {
  const [params, setParams] = useSearchParams();
  const list = useListSearchParams({ field: 'createdAt', order: 'desc' });
  const projects = useManagedProjectOptions();
  const source = Object.values(TicketSource).find((value) => value === params.get('source'));
  const dateFrom = params.get('dateFrom');
  const dateTo = params.get('dateTo');
  const history = useGetImportHistory({
    projectId: params.get('projectId') ?? undefined,
    source,
    dateFrom: dateFrom ?? undefined,
    dateTo: dateTo ?? undefined,
    page: list.apiPaging.page,
    size: list.apiPaging.size,
  });

  function setDates(from: string | null, to: string | null) {
    setParams((current) => {
      const next = new URLSearchParams(current);
      next.delete('page');
      for (const [name, value] of Object.entries({ dateFrom: from, dateTo: to })) {
        if (value) next.set(name, value);
        else next.delete(name);
      }
      return next;
    });
  }

  return (
    <Flex vertical gap="large">
      <Flex align="start" gap="middle" wrap>
        <Flex vertical gap="small">
          <label htmlFor="import-history-project">Project</label>
          <Select
            id="import-history-project"
            virtual={false}
            value={params.get('projectId') ?? ''}
            options={[{ value: '', label: 'All managed Projects' }, ...projects]}
            onChange={(value) => list.setFilter('projectId', value || null)}
          />
        </Flex>
        <Flex vertical gap="small">
          <label htmlFor="import-history-source">Source</label>
          <Select
            id="import-history-source"
            virtual={false}
            value={source ?? ''}
            options={[
              { value: '', label: 'All sources' },
              ...Object.values(ImportSource).map((value) => ({
                value,
                label: TICKET_SOURCE_LABELS[value],
              })),
            ]}
            onChange={(value) => list.setFilter('source', value || null)}
          />
        </Flex>
        <Flex vertical gap="small">
          <label htmlFor="import-history-from">Date range</label>
          <label htmlFor="import-history-to" hidden>
            To date
          </label>
          <DatePicker.RangePicker
            id={{ start: 'import-history-from', end: 'import-history-to' }}
            format="DD/MM/YYYY"
            placeholder={['From date', 'To date']}
            value={[dateFrom ? dayjs(dateFrom) : null, dateTo ? dayjs(dateTo) : null]}
            onChange={(dates) =>
              setDates(
                dates?.[0]?.format('YYYY-MM-DD') ?? null,
                dates?.[1]?.format('YYYY-MM-DD') ?? null,
              )
            }
          />
        </Flex>
        {(dateFrom !== null || dateTo !== null) && (
          <Button onClick={() => setDates(null, null)}>Clear date range</Button>
        )}
      </Flex>
      {history.isPending ? (
        <LoadingState />
      ) : (
        <DataTable<ImportHistoryItem>
          label="Import History"
          columns={COLUMNS}
          rows={history.data?.content}
          rowKey={(row) => row.batchId}
          loading={history.isFetching}
          error={history.isError ? history.error : null}
          onRetry={() => void history.refetch()}
          pagination={{
            page: list.page,
            total: history.data?.page.totalElements ?? 0,
            onChange: list.setPage,
          }}
          scrollX={1490}
        />
      )}
    </Flex>
  );
}

export default ImportHistoryPage;
