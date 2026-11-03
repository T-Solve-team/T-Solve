import { Flex, Tooltip, Typography } from 'antd';
import { useEffect } from 'react';
import { useSearchParams } from 'react-router';
import type { MySolution } from '@/shared/api';
import { formatDate } from '@/shared/lib';
import { paths, useListSearchParams } from '@/shared/routing';
import { cells, DataTable, LoadingState, SectionCard } from '@/shared/ui';
import type { DataTableColumn } from '@/shared/ui';
import { useMySolutions } from '../hooks/useMySolutions';
import { useMySolutionSummary } from '../hooks/useMySolutionSummary';
import { useSolutionFilters } from '../hooks/useSolutionFilters';
import { SolutionFilters } from './SolutionFilters';
import { StatusCounters } from './StatusCounters';

/** One line with "…" and the full text in a tooltip (AC-003.23). */
function oneLine(value: string | null | undefined) {
  return value ? (
    <Tooltip title={value}>
      <Typography.Text ellipsis>{value}</Typography.Text>
    </Tooltip>
  ) : null;
}

const COLUMNS: DataTableColumn<MySolution>[] = [
  {
    key: 'ticketKey',
    title: 'Ticket ID',
    width: 120,
    render: (_, row) =>
      row.canOpen ? cells.link(paths.ticketDetail(row.ticketId), row.ticketKey) : row.ticketKey,
  },
  { key: 'title', title: 'Title', render: (_, row) => oneLine(row.title) },
  {
    key: 'projectName',
    title: 'Project',
    width: 170,
    render: (_, row) => cells.text(row.projectName),
  },
  { key: 'status', title: 'Status', width: 160, render: (_, row) => cells.tag(row.status) },
  {
    key: 'latestDecision',
    title: 'Latest decision',
    width: 260,
    render: (_, row) => oneLine(row.latestDecision?.comment) ?? '—',
  },
  { key: 'updatedAt', title: 'Date', width: 120, render: (_, row) => formatDate(row.updatedAt) },
];

/** 1.2.1 (d) My Solutions: the user's Solutions, 20 per page (AC-003.11 to .14). Mounted only for allowed roles. */
export function MySolutionsSection() {
  // The sort is not sent: the API orders by `updatedAt` desc itself.
  const { page, setPage } = useListSearchParams({ field: 'updatedAt', order: 'desc' });
  const [, setSearchParams] = useSearchParams();
  const summary = useMySolutionSummary();
  const { filters, setFilters } = useSolutionFilters(summary.projects, summary.isSuccess);
  const query = useMySolutions(page, filters);
  const total = query.data?.page.totalElements ?? 0;
  const totalPages = query.data?.page.totalPages ?? 0;

  // A page past the last goes to the last page (or page 1 when empty), without a history entry (edge 63).
  useEffect(() => {
    if (!query.data || page <= Math.max(1, totalPages)) return;
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (totalPages > 1) next.set('page', String(totalPages));
        else next.delete('page');
        return next;
      },
      { replace: true },
    );
  }, [query.data, page, totalPages, setSearchParams]);

  return (
    <section aria-label="My Solutions">
      <SectionCard title="My Solutions">
        <Flex vertical gap="large">
          <StatusCounters
            counts={summary.data?.counts}
            isPending={summary.isPending}
            error={summary.error}
          />
          <SolutionFilters filters={filters} projects={summary.projects} onChange={setFilters} />
          {query.isPending ? (
            <LoadingState />
          ) : (
            <DataTable
              label="My Solutions"
              columns={COLUMNS}
              rows={query.data?.content}
              rowKey={(row) => row.ticketId}
              loading={query.isFetching}
              error={query.error}
              pagination={total > 20 ? { page, total, onChange: setPage } : undefined}
            />
          )}
        </Flex>
      </SectionCard>
    </section>
  );
}
