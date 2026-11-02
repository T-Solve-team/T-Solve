import { Button, Flex } from 'antd';
import { useRef, useState } from 'react';
import { Link } from 'react-router';
import { useGetReviewTicketDetail } from '@/shared/api';
import { useSession } from '@/shared/auth';
import { paths } from '@/shared/routing';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui';
import { useApproveSelectedTickets } from '../hooks/useReviewDecisions';
import { RejectTicketModal } from './RejectTicketModal';
import { RequestChangesModal } from './RequestChangesModal';
import { ReviewTicketContent } from './ReviewTicketContent';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function ReviewTicketDetail({ ticketId }: { ticketId: string }) {
  const validId = UUID_PATTERN.test(ticketId);
  const detail = useGetReviewTicketDetail(ticketId, { query: { enabled: validId } });
  const { hasRole, isProjectManagerOf } = useSession();
  const approve = useApproveSelectedTickets();
  const submitting = useRef(false);
  const [dialog, setDialog] = useState<'reject' | 'changes' | null>(null);
  const ticket = detail.data;
  const canDecide =
    !!ticket &&
    !detail.isError &&
    hasRole('PROJECT_MANAGER') &&
    isProjectManagerOf(ticket.project.projectId) &&
    ticket.status === 'PENDING_REVIEW';
  const canSubmit = canDecide && !detail.isFetching;

  function handleApprove() {
    if (!canSubmit || submitting.current) return;
    submitting.current = true;
    approve.mutate(
      { data: { ticketIds: [ticketId] } },
      {
        onSettled: () => {
          submitting.current = false;
        },
      },
    );
  }

  return (
    <Flex vertical gap="large">
      {hasRole('PROJECT_MANAGER') && <Link to={paths.reviewQueue}>Back to Review Queue</Link>}
      {!validId || detail.error?.status === 404 ? (
        <EmptyState />
      ) : detail.isError ? (
        <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />
      ) : detail.isPending ? (
        <LoadingState />
      ) : (
        ticket && (
          <>
            {canDecide && (
              <Flex gap="small" wrap>
                <Button
                  aria-label="Approve"
                  aria-busy={approve.isPending}
                  type="primary"
                  loading={approve.isPending}
                  disabled={!canSubmit || approve.isPending || dialog !== null}
                  onClick={handleApprove}
                >
                  Approve
                </Button>
                <Button
                  danger
                  disabled={!canSubmit || approve.isPending || dialog !== null}
                  onClick={() => setDialog('reject')}
                >
                  Reject
                </Button>
                <Button
                  disabled={!canSubmit || approve.isPending || dialog !== null}
                  onClick={() => setDialog('changes')}
                >
                  Request Changes
                </Button>
              </Flex>
            )}
            <ReviewTicketContent ticket={ticket} />
          </>
        )
      )}
      {dialog === 'reject' && (
        <RejectTicketModal
          ticketIds={[ticketId]}
          isOpen
          isDisabled={!canSubmit}
          onClose={() => setDialog(null)}
          onRejected={() => setDialog(null)}
        />
      )}
      {dialog === 'changes' && (
        <RequestChangesModal
          ticketIds={[ticketId]}
          isOpen
          isDisabled={!canSubmit}
          onClose={() => setDialog(null)}
          onSent={() => setDialog(null)}
        />
      )}
    </Flex>
  );
}
