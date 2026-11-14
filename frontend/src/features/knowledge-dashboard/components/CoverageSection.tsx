import { Flex } from 'antd';
import type { DashboardCoverage } from '@/shared/api';
import { EmptyState, SectionCard, StatCard } from '@/shared/ui';
import { Share } from './Share';

export function CoverageSection({ coverage }: { coverage: DashboardCoverage }) {
  return (
    <SectionCard title="Knowledge coverage">
      {coverage.allTickets === 0 ? (
        <EmptyState />
      ) : (
        <Flex vertical gap="large">
          <Flex wrap gap="large">
            <StatCard label="Published tickets" value={coverage.publishedTickets} />
            <StatCard label="All tickets" value={coverage.allTickets} />
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
