/** 7.5 Expired Tickets — KAN-158. */
import { Button, Checkbox, Flex, Select, Typography } from 'antd';
import { useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import type { ExpiredTicketItem } from '@/shared/api';
import { useSession } from '@/shared/auth';
import { TICKET_SOURCE_LABELS } from '@/shared/lib';
import { paths, useListSearchParams } from '@/shared/routing';
import { cells, DataTable, ErrorState, StatusTag, type DataTableColumn } from '@/shared/ui';
import { useExpiredTickets, useSendBackSelectedTickets } from '../hooks/useExpiredTickets';
import { useExpiredTicketIds } from '../hooks/useExpiredTicketIds';
import { MAX_REVIEW_SELECTION } from '../review-limits';
import styles from './ReviewQueuePage.module.css';
import toolbarStyles from '../components/ReviewQueueToolbar.module.css';

const columns: DataTableColumn<ExpiredTicketItem>[] = [
  {
    key: 'sourceTicketId',
    title: 'Ticket ID',
    width: 130,
    sortable: true,
    render: (_, t) => cells.link(paths.ticketDetail(t.ticketId), t.sourceTicketId),
  },
  {
    key: 'title',
    title: 'Title',
    width: 280,
    sortable: true,
    render: (_, t) => cells.text(t.title),
  },
  {
    key: 'project',
    title: 'Project',
    width: 140,
    sortable: true,
    render: (_, t) => cells.text(t.project.name),
  },
  { key: 'source', title: 'Source', width: 140, render: (_, t) => TICKET_SOURCE_LABELS[t.source] },
  {
    key: 'expiredAt',
    title: 'Expired on',
    width: 170,
    sortable: true,
    render: (_, t) => cells.dateTime(t.expiredAt),
  },
  {
    key: 'previousStatus',
    title: 'Previous status',
    width: 180,
    render: (_, t) => <StatusTag status={t.previousStatus} />,
  },
];
export function ExpiredTicketsPage() {
  const [params] = useSearchParams();
  const raw = params.get('departmentId');
  const departmentId =
    raw && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(raw)
      ? raw
      : undefined;
  return <ExpiredTicketsContent key={departmentId ?? 'all'} departmentId={departmentId} />;
}
function ExpiredTicketsContent({ departmentId }: { departmentId: string | undefined }) {
  const { session } = useSession();
  const list = useListSearchParams({ field: 'expiredAt', order: 'desc' });
  const query = useExpiredTickets({ departmentId, ...list.apiPaging });
  const sendBack = useSendBackSelectedTickets();
  const [selection, setSelection] = useState<string[]>([]);
  const ids = useExpiredTicketIds(departmentId, selection.length > 0, query.dataUpdatedAt);
  const selected = ids.isFresh
    ? selection.filter((id) => ids.data?.ticketIds.includes(id))
    : selection;
  if (selected.length !== selection.length) setSelection(selected);
  const [selecting, setSelecting] = useState(false);
  const submitting = useRef(false);
  const disabled =
    !selected.length ||
    selected.length > MAX_REVIEW_SELECTION ||
    !ids.isFresh ||
    query.isFetching ||
    query.isError ||
    sendBack.isPending ||
    selecting;
  async function selectAll(checked: boolean) {
    if (!checked) {
      setSelection([]);
      return;
    }
    setSelecting(true);
    try {
      setSelection(await ids.loadAll());
    } catch {
      /* eligibility error is shown below */
    } finally {
      setSelecting(false);
    }
  }
  function submit() {
    if (disabled || submitting.current) return;
    submitting.current = true;
    sendBack.mutate(
      { data: { ticketIds: selected } },
      {
        onSuccess: () => setSelection([]),
        onSettled: () => {
          submitting.current = false;
        },
      },
    );
  }
  return (
    <Flex vertical gap="large">
      <Flex justify="space-between" align="center" gap="middle" wrap>
        <Flex align="center" gap="middle" wrap>
          <Flex align="center" gap="small">
            <Typography.Text>Department</Typography.Text>
            <Select
              className={toolbarStyles.department}
              aria-label="Department"
              virtual={false}
              value={departmentId ?? 'ALL'}
              options={[
                { value: 'ALL', label: 'All Departments' },
                ...(session?.departments ?? []).map((d) => ({
                  value: d.departmentId,
                  label: d.name,
                })),
              ]}
              onChange={(value) => list.setFilter('departmentId', value === 'ALL' ? null : value)}
            />
          </Flex>
          <Typography.Text role="status" aria-live="polite" type="secondary">
            {selected.length} selected
            {selected.length > MAX_REVIEW_SELECTION ? ` / ${MAX_REVIEW_SELECTION} maximum` : ''}
          </Typography.Text>
        </Flex>
        <Button type="primary" loading={sendBack.isPending} disabled={disabled} onClick={submit}>
          Send back to queue
        </Button>
      </Flex>
      {ids.isError && <ErrorState error={ids.error} onRetry={() => void ids.refetch()} />}
      <div className={styles.card}>
        <DataTable<ExpiredTicketItem>
          label="Expired tickets"
          columns={columns}
          rows={query.data?.content}
          rowKey={(t) => t.ticketId}
          loading={query.isPending}
          error={query.isError ? query.error : null}
          onRetry={() => void query.refetch()}
          pagination={{
            page: list.page,
            total: query.data?.page.totalElements ?? 0,
            onChange: list.setPage,
          }}
          sort={list.sort}
          onSortChange={list.setSort}
          scrollX={1088}
          rowSelection={{
            selectedRowKeys: selected,
            preserveSelectedRowKeys: true,
            onChange: (keys) => setSelection(keys.map(String)),
            getCheckboxProps: (t) => ({
              'aria-label': `Select ${t.sourceTicketId}`,
              disabled: sendBack.isPending || selecting,
            }),
            columnTitle: (
              <Checkbox
                aria-label="Select all"
                checked={
                  !!query.data?.page.totalElements &&
                  selected.length === query.data.page.totalElements
                }
                indeterminate={
                  selected.length > 0 && selected.length !== query.data?.page.totalElements
                }
                disabled={
                  !query.data?.page.totalElements ||
                  query.isFetching ||
                  sendBack.isPending ||
                  selecting
                }
                onChange={(event) => void selectAll(event.target.checked)}
              />
            ),
          }}
        />
      </div>
    </Flex>
  );
}
export default ExpiredTicketsPage;
