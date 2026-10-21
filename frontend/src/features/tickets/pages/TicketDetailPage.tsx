/** Ticket Detail — Report 3 UC-24 / UC-30, review scope. */
import { Descriptions, Flex, List, Typography } from 'antd';
import { Link, useParams } from 'react-router';
import { TicketDecisionActions } from '@/features/review';
import { ApiError, useGetReviewTicket, type SolutionSections } from '@/shared/api';
import { formatDateTime } from '@/shared/lib';
import { paths } from '@/shared/routing';
import { ErrorState, LoadingState, SectionCard, StatusTag } from '@/shared/ui';
import styles from './TicketDetailPage.module.css';

const SECTIONS: [keyof SolutionSections, string][] = [
  ['symptoms', 'Symptoms'],
  ['rootCause', 'Root cause'],
  ['resolutionSteps', 'Resolution steps'],
  ['workaround', 'Workaround'],
  ['scopeOfApplicability', 'Scope of applicability'],
];

export function TicketDetailPage() {
  const { ticketId } = useParams();
  const id = Number(ticketId);
  const isValid = Number.isSafeInteger(id) && id > 0;
  const query = useGetReviewTicket(id, { query: { enabled: isValid } });
  if (!isValid) return <ErrorState error={new ApiError({ status: 404, code: 'MSG04' })} />;
  if (query.isPending) return <LoadingState />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  const ticket = query.data;
  return (
    <Flex vertical gap="large">
      <Link to={paths.reviewQueue}>Back to Review Queue</Link>
      <SectionCard title={ticket.sourceTicketId}>
        <Flex vertical gap="middle">
          <Typography.Title level={2}>{ticket.title}</Typography.Title>
          <Descriptions
            column={{ xs: 1, md: 2 }}
            items={[
              { key: 'project', label: 'Project', children: ticket.project.name },
              { key: 'author', label: 'Author', children: ticket.authorName },
              {
                key: 'source',
                label: 'Source',
                children: ticket.source === 'SOLUTION_FORM' ? 'Solution Form' : ticket.source,
              },
              { key: 'status', label: 'Status', children: <StatusTag status={ticket.status} /> },
              {
                key: 'entered',
                label: 'Entered queue',
                children: formatDateTime(ticket.enteredQueueAt),
              },
              {
                key: 'due',
                label: 'Review due date',
                children: formatDateTime(ticket.reviewDueDate),
              },
            ]}
          />
          <TicketDecisionActions ticket={ticket} />
        </Flex>
      </SectionCard>
      {ticket.reviewComment && (
        <SectionCard title="Review comment">
          <p className={styles.text}>{ticket.reviewComment}</p>
        </SectionCard>
      )}
      <SectionCard title="Solution">
        {SECTIONS.map(([field, label]) => (
          <section key={field}>
            <Typography.Title level={3}>{label}</Typography.Title>
            <p className={styles.text}>{ticket.solution[field] || '—'}</p>
          </section>
        ))}
      </SectionCard>
      <SectionCard title="Evidence">
        <List
          dataSource={ticket.evidence}
          locale={{ emptyText: 'No Evidence files.' }}
          renderItem={(file) => (
            <List.Item>
              {file.name} · {file.mediaType} · {file.sizeBytes} bytes
            </List.Item>
          )}
        />
      </SectionCard>
    </Flex>
  );
}
export default TicketDetailPage;
