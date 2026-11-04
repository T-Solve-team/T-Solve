/** 10.1 Suggestion History (KAN-149). */
import { useState } from 'react';
import { Button, Flex, Typography } from 'antd';
import type { SuggestionResultSummary } from '@/shared/api';
import { useListSearchParams } from '@/shared/routing';
import { cells, DataTable, LoadingState, type DataTableColumn } from '@/shared/ui';
import { HistoryFilters } from '../components/HistoryFilters';
import { TriageResultDetailModal } from '../components/TriageResultDetailModal';
import styles from '../components/TriageHistory.module.css';
import { historyDateRange } from '../hooks/history-filters';
import { useSuggestionHistory } from '../hooks/useSuggestionHistory';

function safeIssueUrl(value: string | null): string | null {
  if (value === null) return null;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname ? url.href : null;
  } catch {
    return null;
  }
}

export function TriageHistoryPage() {
  const list = useListSearchParams({ field: 'createdAt', order: 'desc' });
  const range = historyDateRange(list.getFilter('from'), list.getFilter('to'));
  const [selectedIssue, setSelectedIssue] = useState<string | null>(null);
  const answer = list.getFilter('answer');
  const history = useSuggestionHistory(
    {
      projectId: list.getFilter('projectId') || undefined,
      answer: answer === 'YES' || answer === 'NO' || answer === 'NONE' ? answer : undefined,
      from: range.from,
      to: range.to,
      page: list.apiPaging.page,
      size: list.apiPaging.size,
      sort: ['createdAt,desc'],
    },
    range.valid,
  );

  const columns: DataTableColumn<SuggestionResultSummary>[] = [
    {
      key: 'issue',
      title: 'Issue',
      width: 220,
      render: (_, row) => {
        const url = safeIssueUrl(row.jiraIssueUrl);
        return (
          <div className={styles.issueCell}>
            {url ? (
              <a href={url} target="_blank" rel="noopener noreferrer">
                {row.issueKey}
              </a>
            ) : (
              <Typography.Text>{row.issueKey}</Typography.Text>
            )}
            <Typography.Text type="secondary">{row.project.key}</Typography.Text>
            <Button type="link" onClick={() => setSelectedIssue(row.issueKey)}>
              View details
            </Button>
          </div>
        );
      },
    },
    {
      key: 'sources',
      title: 'Sources',
      width: 170,
      render: (_, row) =>
        cells.text(`${row.ticketSourceCount} Tickets · ${row.wikiSourceCount} Wiki`),
    },
    {
      key: 'solution',
      title: 'Suggested solution',
      width: 380,
      render: (_, row) => cells.text(row.suggestedSolutionExcerpt ?? '—'),
    },
    {
      key: 'answer',
      title: "Reporter's answer",
      width: 180,
      render: (_, row) =>
        cells.text(
          row.reporterAnswer === 'YES'
            ? 'Solved'
            : row.reporterAnswer === 'NO'
              ? 'Not solved'
              : 'No answer',
        ),
    },
  ];

  return (
    <Flex vertical gap="large">
      <HistoryFilters />
      {range.valid &&
        (history.isPending ? (
          <LoadingState />
        ) : (
          <DataTable<SuggestionResultSummary>
            label="Suggestion history"
            columns={columns}
            rows={history.data?.content}
            rowKey={(row) => row.issueKey}
            loading={history.isFetching}
            error={history.isError ? history.error : null}
            onRetry={() => void history.refetch()}
            pagination={{
              page: list.page,
              total: history.data?.page.totalElements ?? 0,
              onChange: list.setPage,
            }}
            scrollX={950}
          />
        ))}
      {selectedIssue && (
        <TriageResultDetailModal
          key={selectedIssue}
          issueKey={selectedIssue}
          onClose={() => setSelectedIssue(null)}
        />
      )}
    </Flex>
  );
}

export default TriageHistoryPage;
