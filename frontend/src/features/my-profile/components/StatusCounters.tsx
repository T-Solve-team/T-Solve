import { Flex, Typography } from 'antd';
import type { ApiError, MySolutionSummary } from '@/shared/api';
import { ErrorState, LoadingState, StatusTag } from '@/shared/ui';

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
        <Flex gap="large" wrap>
          {ORDER.map((status) => (
            <Flex key={status} vertical gap="small">
              <StatusTag status={status} />
              <Typography.Text strong>{counts[status]}</Typography.Text>
            </Flex>
          ))}
        </Flex>
      )}
    </div>
  );
}
