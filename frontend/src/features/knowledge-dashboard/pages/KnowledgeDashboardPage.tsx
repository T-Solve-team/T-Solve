/** 9.1 Knowledge Dashboard — SRS III.9.1 */
import { Flex } from 'antd';
import { useSession } from '@/shared/auth';
import { ErrorState, LoadingState } from '@/shared/ui';
import { useKnowledgeDashboard } from '../api/useKnowledgeDashboard';
import { CoverageSection } from '../components/CoverageSection';
import { DashboardFilters } from '../components/DashboardFilters';
import { FeedbackSection } from '../components/FeedbackSection';
import { GapsSection } from '../components/GapsSection';
import { OutcomesSection } from '../components/OutcomesSection';
import { SuggestionsSection } from '../components/SuggestionsSection';
import { useDashboardFilters } from '../hooks/useDashboardFilters';

export function KnowledgeDashboardPage() {
  const { session, hasRole } = useSession();
  const filters = useDashboardFilters();
  const isAdmin = hasRole('ADMIN');
  const departmentId = isAdmin ? filters.departmentId : session?.department?.departmentId;
  const dashboard = useKnowledgeDashboard(
    { ...filters.params, departmentId },
    filters.isRangeValid && session !== null,
  );

  return (
    <Flex vertical gap="large">
      <DashboardFilters
        filters={filters}
        options={dashboard.data?.options}
        isAdmin={isAdmin}
        departmentId={departmentId}
      />
      {filters.isRangeValid &&
        (dashboard.isFetching || dashboard.isPending ? (
          <LoadingState />
        ) : dashboard.isError ? (
          <ErrorState error={dashboard.error} onRetry={() => void dashboard.refetch()} />
        ) : (
          dashboard.data && (
            <Flex vertical gap="middle">
              <OutcomesSection outcomes={dashboard.data.outcomes} />
              <CoverageSection coverage={dashboard.data.coverage} />
              <FeedbackSection
                feedback={dashboard.data.feedback}
                canOpenTickets={hasRole('PROJECT_MANAGER')}
              />
              <GapsSection
                gaps={dashboard.data.gaps}
                note={
                  filters.projectId
                    ? departmentId
                      ? 'Figures count the whole Department.'
                      : 'Figures count the whole Workspace.'
                    : undefined
                }
              />
              <SuggestionsSection suggestions={dashboard.data.suggestions} />
            </Flex>
          )
        ))}
    </Flex>
  );
}

export default KnowledgeDashboardPage;
