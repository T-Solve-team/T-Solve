/** 7.4 Review History — read-only decisions of managed Projects. */
import { Button, Flex, Input, Select, Typography } from 'antd';
import { useSearchParams } from 'react-router';
import type { ReviewHistoryItem } from '@/shared/api';
import { useSession } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { paths, useListSearchParams } from '@/shared/routing';
import { cells, DataTable, LoadingState, type DataTableColumn } from '@/shared/ui';
import { DECISION_LABELS, historyDateRange, isReviewDecision } from '../hooks/history-filters';
import { useReviewHistory } from '../hooks/useReviewHistory';
import styles from './ReviewQueuePage.module.css';
import toolbarStyles from '../components/ReviewQueueToolbar.module.css';

const COLUMNS: DataTableColumn<ReviewHistoryItem>[] = [
  {
    key: 'timestamp',
    title: 'Date',
    width: 170,
    sortable: true,
    render: (_, entry) => cells.dateTime(entry.timestamp),
  },
  {
    key: 'sourceTicketId',
    title: 'Ticket ID',
    width: 130,
    render: (_, entry) => cells.link(paths.ticketDetail(entry.ticketId), entry.sourceTicketId),
  },
  { key: 'title', title: 'Title', width: 240, render: (_, entry) => cells.text(entry.title) },
  {
    key: 'project',
    title: 'Project',
    width: 140,
    render: (_, entry) => cells.text(entry.project.name),
  },
  {
    key: 'decision',
    title: 'Decision',
    width: 180,
    render: (_, entry) => DECISION_LABELS[entry.decision],
  },
  {
    key: 'actorName',
    title: 'Project Manager',
    width: 180,
    render: (_, entry) => cells.text(entry.actorName),
  },
  {
    key: 'authorName',
    title: 'Author',
    width: 180,
    render: (_, entry) => cells.text(entry.authorName),
  },
  {
    key: 'content',
    title: 'Reason / comment',
    width: 300,
    render: (_, entry) => cells.text(entry.reason ?? entry.comment),
  },
];
export function ReviewHistoryPage() {
  const [params, setParams] = useSearchParams();
  const list = useListSearchParams({ field: 'timestamp', order: 'desc' });
  const { session } = useSession();
  const departmentId = params.get('departmentId');
  const rawDecision = params.get('decision');
  const decision = isReviewDecision(rawDecision) ? rawDecision : undefined;
  const dateFrom = params.get('dateFrom');
  const dateTo = params.get('dateTo');
  const range = historyDateRange(dateFrom, dateTo);
  const filtersValid =
    (rawDecision === null || decision !== undefined) &&
    (departmentId === null ||
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(departmentId));
  const history = useReviewHistory(
    {
      departmentId: departmentId ?? undefined,
      decision,
      dateFrom: dateFrom ?? undefined,
      dateTo: dateTo ?? undefined,
      ...list.apiPaging,
    },
    range.valid && filtersValid,
  );
  function clearDates() {
    setParams((current) => {
      const next = new URLSearchParams(current);
      next.delete('dateFrom');
      next.delete('dateTo');
      next.delete('page');
      return next;
    });
  }
  return (
    <Flex vertical gap="large">
      <Flex align="start" gap="middle" wrap>
        <Flex vertical gap="small">
          <label htmlFor="history-department">Department</label>
          <Select
            id="history-department"
            className={toolbarStyles.department}
            virtual={false}
            value={departmentId ?? ''}
            options={[
              { value: '', label: 'All Departments' },
              ...(session?.departments ?? []).map((d) => ({
                value: d.departmentId,
                label: d.name,
              })),
            ]}
            onChange={(value) => list.setFilter('departmentId', value || null)}
          />
        </Flex>
        <Flex vertical gap="small">
          <label htmlFor="history-decision">Decision</label>
          <Select
            id="history-decision"
            className={toolbarStyles.department}
            virtual={false}
            value={rawDecision ?? ''}
            options={[
              { value: '', label: 'All decisions' },
              ...Object.entries(DECISION_LABELS).map(([value, label]) => ({ value, label })),
            ]}
            onChange={(value) => list.setFilter('decision', value || null)}
          />
        </Flex>
        <Flex vertical gap="small">
          <label htmlFor="history-from">From date</label>
          <Input
            id="history-from"
            type="date"
            value={dateFrom ?? ''}
            status={range.valid ? undefined : 'error'}
            aria-describedby={!range.valid ? 'history-date-error' : undefined}
            onChange={(event) => list.setFilter('dateFrom', event.target.value || null)}
          />
        </Flex>
        <Flex vertical gap="small">
          <label htmlFor="history-to">To date</label>
          <Input
            id="history-to"
            type="date"
            value={dateTo ?? ''}
            status={range.valid ? undefined : 'error'}
            aria-describedby={!range.valid ? 'history-date-error' : undefined}
            onChange={(event) => list.setFilter('dateTo', event.target.value || null)}
          />
        </Flex>
        {(dateFrom !== null || dateTo !== null) && (
          <Button onClick={clearDates}>Clear date range</Button>
        )}
      </Flex>
      {!range.valid ? (
        <Typography.Text id="history-date-error" role="alert" type="danger">
          {msg('MSG39')}
        </Typography.Text>
      ) : !filtersValid ? (
        <Typography.Text role="alert" type="danger">
          {msg('MSG06')}
        </Typography.Text>
      ) : history.isPending ? (
        <LoadingState />
      ) : (
        <div className={styles.card}>
          <DataTable<ReviewHistoryItem>
            label="Review History"
            columns={COLUMNS}
            rows={history.data?.content}
            rowKey={(entry) => entry.decisionId}
            loading={history.isFetching}
            error={history.isError ? history.error : null}
            onRetry={() => void history.refetch()}
            pagination={{
              page: list.page,
              total: history.data?.page.totalElements ?? 0,
              onChange: list.setPage,
            }}
            sort={list.sort}
            onSortChange={(sort) => list.setSort(sort ?? { field: 'timestamp', order: 'asc' })}
            scrollX={1520}
          />
        </div>
      )}
    </Flex>
  );
}
export default ReviewHistoryPage;
