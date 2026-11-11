import { Flex, Statistic, Typography } from 'antd';
import type { DashboardSuggestions } from '@/shared/api';
import { EmptyState, SectionCard } from '@/shared/ui';
import { Share } from './Share';

export function SuggestionsSection({ suggestions }: { suggestions: DashboardSuggestions }) {
  return (
    <SectionCard>
      <Typography.Title level={2}>Suggestion results</Typography.Title>
      {suggestions.posted === 0 ? (
        <EmptyState />
      ) : (
        <Flex vertical gap="large">
          <Flex wrap gap="large">
            <Statistic title="Posted" value={suggestions.posted} />
            <Statistic title="Solved" value={suggestions.solved} />
          </Flex>
          <Share label="Solved share" value={suggestions.solvedShare} />
        </Flex>
      )}
    </SectionCard>
  );
}
