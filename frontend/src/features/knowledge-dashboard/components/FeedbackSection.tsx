import { Flex, Statistic, Typography } from 'antd';
import type { DashboardFeedback, DashboardRatedTicket } from '@/shared/api';
import { paths } from '@/shared/routing';
import { cells, DataTable, EmptyState, SectionCard, type DataTableColumn } from '@/shared/ui';
import { Share } from './Share';

type FeedbackSectionProps = {
  feedback: DashboardFeedback;
  /** Only a Project Manager may open the Ticket Detail from the dashboard. */
  canOpenTickets: boolean;
};

function ticketColumns(canOpenTickets: boolean): DataTableColumn<DashboardRatedTicket>[] {
  return [
    {
      key: 'title',
      title: 'Ticket',
      render: (_, row) =>
        canOpenTickets
          ? cells.link(paths.ticketDetail(row.ticketId), row.title)
          : cells.text(row.title),
    },
    { key: 'helpful', title: 'Helpful', width: 120, render: (_, row) => row.helpful },
    { key: 'notHelpful', title: 'Not helpful', width: 120, render: (_, row) => row.notHelpful },
    {
      key: 'helpfulRate',
      title: 'Helpful rate',
      width: 140,
      render: (_, row) => `${Math.round(row.helpfulRate * 100)}%`,
    },
  ];
}

export function FeedbackSection({ feedback, canOpenTickets }: FeedbackSectionProps) {
  const columns = ticketColumns(canOpenTickets);
  return (
    <SectionCard>
      <Typography.Title level={2}>Reuse &amp; feedback</Typography.Title>
      {feedback.helpful + feedback.notHelpful === 0 ? (
        <EmptyState />
      ) : (
        <Flex vertical gap="large">
          <Flex wrap gap="large">
            <Statistic title="Helpful" value={feedback.helpful} />
            <Statistic title="Not helpful" value={feedback.notHelpful} />
          </Flex>
          <Share label="Helpful rate" value={feedback.helpfulRate} />
          <Typography.Title level={3}>Top 5 tickets</Typography.Title>
          <DataTable
            label="Top 5 tickets"
            columns={columns}
            rows={feedback.topRated}
            rowKey={(row) => row.ticketId}
          />
          <Typography.Title level={3}>Bottom 5 tickets</Typography.Title>
          <DataTable
            label="Bottom 5 tickets"
            columns={columns}
            rows={feedback.lowestRated}
            rowKey={(row) => row.ticketId}
          />
        </Flex>
      )}
    </SectionCard>
  );
}
