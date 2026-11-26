import { Button, Flex, Typography } from 'antd';
import {
  useRunAiReview,
  type AiReviewFinding,
  type AiReviewFindingField,
  type AiReviewFindingSeverity,
} from '@/shared/api';
import { msg } from '@/shared/messages';
import { ErrorState } from '@/shared/ui';
import styles from './ReviewTicketDetail.module.css';

/** The labels Ticket Detail shows for these fields. */
const FIELD_LABELS: Record<AiReviewFindingField, string> = {
  TITLE: 'Title',
  DESCRIPTION: 'Description',
  SYMPTOMS: 'Symptoms',
  ROOT_CAUSE: 'Root cause',
  RESOLUTION_STEPS: 'Resolution steps',
  WORKAROUND: 'Workaround',
  SCOPE: 'Scope',
  GENERAL: 'General',
};

const SEVERITIES: [AiReviewFindingSeverity, string][] = [
  ['HIGH', 'High'],
  ['MEDIUM', 'Medium'],
  ['LOW', 'Low'],
];

/**
 * AI check (STORY-015): advisory findings for the managing Project Manager, never stored. Rendered with the
 * decision controls only, and independent of them: a failure here leaves Approve, Reject and Request Changes working.
 */
export function AiReviewPanel({ ticketId }: { ticketId: string }) {
  const review = useRunAiReview({ mutation: { meta: { handlesOwnErrors: true } } });
  const run = () => {
    if (!review.isPending) review.mutate({ ticketId });
  };
  const findings = review.data?.findings;

  return (
    <Flex vertical gap="small" align="flex-start">
      <Button
        aria-busy={review.isPending}
        loading={review.isPending}
        disabled={review.isPending}
        onClick={run}
      >
        AI check
      </Button>
      {review.isError ? (
        <ErrorState error={review.error} onRetry={run} />
      ) : (
        findings &&
        (findings.length === 0 ? (
          <p>{msg('MSG_NEW_AI_REVIEW_2')}</p>
        ) : (
          SEVERITIES.map(([severity, label]) => {
            const group = findings.filter((finding) => finding.severity === severity);
            return group.length === 0 ? null : (
              <section key={severity} className={styles.content}>
                <Typography.Title level={3}>{label}</Typography.Title>
                <ul aria-label={label} className={styles.evidence}>
                  {group.map((finding, index) => (
                    <FindingItem key={index} finding={finding} />
                  ))}
                </ul>
              </section>
            );
          })
        ))
      )}
    </Flex>
  );
}

function FindingItem({ finding }: { finding: AiReviewFinding }) {
  return (
    <li className={styles.text}>
      <Typography.Text strong>{FIELD_LABELS[finding.field]}</Typography.Text>: {finding.issue}{' '}
      <Typography.Text type="secondary">{finding.suggestion}</Typography.Text>
    </li>
  );
}
