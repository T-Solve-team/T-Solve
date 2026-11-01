import { Typography } from 'antd';
import type { ApiError, MySolutionSummary } from '@/shared/api';
import { ErrorState, LoadingState, STATUS_TAGS } from '@/shared/ui';
import styles from './StatusCounters.module.css';

/** The frame's order (1.2.1 (d)), not the TicketStatus enum order. */
const ORDER = [
  'PENDING_REVIEW',
  'CHANGES_REQUESTED',
  'PUBLISHED',
  'REJECTED',
  'EXPIRED',
  'UNPUBLISHED',
] as const;

type StatusCountersProps = {
  counts?: MySolutionSummary['counts'];
  isPending: boolean;
  error?: ApiError | null;
};

/** Six read-only counters (AC-003.21); a failure shows its message in the group only (AC-003.14). */
export function StatusCounters({ counts, isPending, error }: StatusCountersProps) {
  return (
    <div role="group" aria-label="Solution counts">
      {isPending ? (
        <LoadingState />
      ) : error || !counts ? (
        <ErrorState error={error} />
      ) : (
        <div className={styles.grid}>
          {ORDER.map((status) => (
            <div key={status} className={styles.card}>
              <Typography.Text type="secondary">{STATUS_TAGS[status].label}</Typography.Text>
              <Typography.Text className={styles.value}>{counts[status]}</Typography.Text>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
