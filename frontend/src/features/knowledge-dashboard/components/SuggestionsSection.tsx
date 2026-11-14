import { Flex } from 'antd';
import type { DashboardSuggestions } from '@/shared/api';
import { EmptyState, SectionCard, StatCard } from '@/shared/ui';
import { Share } from './Share';

export function SuggestionsSection({ suggestions }: { suggestions: DashboardSuggestions }) {
  return (
    <SectionCard title="Suggestion results">
      {suggestions.posted === 0 ? (
        <EmptyState />
      ) : (
        <Flex vertical gap="large">
          <Flex wrap gap="large">
            <StatCard label="Posted" value={suggestions.posted} />
            <StatCard label="Solved" value={suggestions.solved} />
          </Flex>
          <Share label="Solved share" value={suggestions.solvedShare} />
        </Flex>
      )}
    </SectionCard>
  );
}
