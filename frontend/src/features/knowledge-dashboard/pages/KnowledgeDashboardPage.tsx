/** 9.1 Knowledge Dashboard — SRS III.9.1 */
import { Flex, Typography } from 'antd';
import { useSession } from '@/shared/auth';
import { ErrorState, LoadingState, SectionCard } from '@/shared/ui';
import { useKnowledgeDashboard } from '../api/useKnowledgeDashboard';
import { DashboardFilters } from '../components/DashboardFilters';
import { useDashboardFilters } from '../hooks/useDashboardFilters';

const SECTIONS = [
  'Solution outcomes',
  'Knowledge coverage',
  'Reuse & feedback',
  'Knowledge gaps',
  'Suggestion results',
];

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
              {SECTIONS.map((title) => (
                <SectionCard key={title}>
                  <Typography.Title level={2}>{title}</Typography.Title>
                  <span aria-hidden>—</span>
                  {title === 'Knowledge gaps' && filters.projectId && (
                    <Typography.Paragraph type="secondary">
                      {departmentId
                        ? 'Figures count the whole Department.'
                        : 'Figures count the whole Workspace.'}
                    </Typography.Paragraph>
                  )}
                </SectionCard>
              ))}
            </Flex>
          )
        ))}
    </Flex>
  );
}

export default KnowledgeDashboardPage;
