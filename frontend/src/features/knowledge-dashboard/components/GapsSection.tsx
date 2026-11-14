import { Flex, Typography } from 'antd';
import type { DashboardGaps, DashboardQuestionGap, DashboardSearchGap } from '@/shared/api';
import {
  cells,
  DataTable,
  EmptyState,
  SectionCard,
  StatCard,
  type DataTableColumn,
} from '@/shared/ui';

const SEARCH_COLUMNS: DataTableColumn<DashboardSearchGap>[] = [
  { key: 'query', title: 'Search', render: (_, row) => cells.text(row.query) },
  { key: 'count', title: 'Count', width: 120, render: (_, row) => row.count },
];

const QUESTION_COLUMNS: DataTableColumn<DashboardQuestionGap>[] = [
  { key: 'question', title: 'Question', render: (_, row) => cells.text(row.question) },
  { key: 'count', title: 'Count', width: 120, render: (_, row) => row.count },
];

type GapsSectionProps = {
  gaps: DashboardGaps;
  /** Explains the scope of the figures; gaps ignore the chosen Project. */
  note?: string;
};

export function GapsSection({ gaps, note }: GapsSectionProps) {
  const isEmpty =
    gaps.noResultSearches.length === 0 &&
    gaps.chatbotQuestions.length === 0 &&
    gaps.chatbotNoSource === 0;
  return (
    <SectionCard title="Knowledge gaps">
      {note && <Typography.Paragraph type="secondary">{note}</Typography.Paragraph>}
      {isEmpty ? (
        <EmptyState />
      ) : (
        <Flex vertical gap="large">
          <Typography.Title level={3}>Searches with no result</Typography.Title>
          <DataTable
            label="Searches with no result"
            columns={SEARCH_COLUMNS}
            rows={gaps.noResultSearches}
            rowKey={(row) => row.query}
          />
          <Typography.Title level={3}>Knowledge Chatbot answers with no source</Typography.Title>
          <StatCard label="Answers" value={gaps.chatbotNoSource} />
          <DataTable
            label="Knowledge Chatbot questions with no source"
            columns={QUESTION_COLUMNS}
            rows={gaps.chatbotQuestions}
            rowKey={(row) => row.question}
          />
        </Flex>
      )}
    </SectionCard>
  );
}
