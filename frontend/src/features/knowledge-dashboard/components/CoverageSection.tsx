import { Flex, Statistic, Typography } from 'antd';
import type { DashboardCoverage } from '@/shared/api';
import { EmptyState, SectionCard } from '@/shared/ui';
import { Share } from './Share';

export function CoverageSection({ coverage }: { coverage: DashboardCoverage }) {
  return (
    <SectionCard>
      <Typography.Title level={2}>Knowledge coverage</Typography.Title>
      {coverage.allTickets === 0 ? (
        <EmptyState />
      ) : (
        <Flex vertical gap="large">
          <Flex wrap gap="large">
            <Statistic title="Published tickets" value={coverage.publishedTickets} />
            <Statistic title="All tickets" value={coverage.allTickets} />
          </Flex>
          <Share label="Published share" value={coverage.publishedShare} />
          {coverage.perDepartment.map((department) => (
            <Share
              key={department.departmentId}
              label={`${department.name}: ${department.publishedTickets} of ${department.allTickets} Published`}
              value={department.publishedShare}
            />
          ))}
        </Flex>
      )}
    </SectionCard>
  );
}
