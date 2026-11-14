import { Flex, Typography } from 'antd';
import dayjs from 'dayjs';
import type { DashboardOutcomes } from '@/shared/api';
import { EmptyState, SectionCard, StatCard } from '@/shared/ui';
import { BarList } from './BarList';
import { Share } from './Share';

const SERIES = ['Approved', 'Rejected', 'Changes requested'];

export function OutcomesSection({ outcomes }: { outcomes: DashboardOutcomes }) {
  const { approved, rejected, changesRequested, pendingReview, expired } = outcomes;
  const isEmpty = approved + rejected + changesRequested + pendingReview + expired === 0;
  return (
    <SectionCard title="Solution outcomes">
      {isEmpty ? (
        <EmptyState />
      ) : (
        <Flex vertical gap="large">
          <Flex wrap gap="large">
            <StatCard label="Approved" value={approved} />
            <StatCard label="Rejected" value={rejected} />
            <StatCard label="Changes requested" value={changesRequested} />
            <StatCard label="Pending review" value={pendingReview} />
            <StatCard label="Expired" value={expired} />
          </Flex>
          <Share label="Approval rate" value={outcomes.approvalRate} />
          <Typography.Title level={3}>Per day</Typography.Title>
          <BarList
            label="Outcomes per day"
            series={SERIES}
            bars={outcomes.perDay.map((day) => ({
              key: day.date,
              // A calendar date, not an instant: format it as is, without a timezone shift.
              label: dayjs(day.date).format('DD/MM/YYYY'),
              values: [day.approved, day.rejected, day.changesRequested],
            }))}
          />
          <Typography.Title level={3}>Per Project</Typography.Title>
          <BarList
            label="Outcomes per Project"
            series={SERIES}
            bars={outcomes.perProject.map((project) => ({
              key: project.projectId,
              label: project.name,
              values: [project.approved, project.rejected, project.changesRequested],
            }))}
          />
        </Flex>
      )}
    </SectionCard>
  );
}
