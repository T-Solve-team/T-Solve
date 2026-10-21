import { Alert, Button, Flex, Tooltip } from 'antd';
import { useState } from 'react';
import type { ReviewTicketResponse } from '@/shared/api';
import { useSession } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { useApproveSelectedTickets } from '../hooks/useReviewDecisions';
import { RejectTicketModal } from './RejectTicketModal';
import { RequestChangesModal } from './RequestChangesModal';

/** UC-30: only the Project's manager may decide a pending ticket. */
export function TicketDecisionActions({ ticket }: { ticket: ReviewTicketResponse }) {
  const { isProjectManagerOf } = useSession();
  const approve = useApproveSelectedTickets();
  const [dialog, setDialog] = useState<'reject' | 'changes' | null>(null);
  if (!isProjectManagerOf(ticket.project.projectId) || ticket.status !== 'PENDING_REVIEW')
    return null;
  const { symptoms, rootCause, resolutionSteps, workaround, scopeOfApplicability } =
    ticket.solution;
  const isEmpty = [symptoms, rootCause, resolutionSteps, workaround, scopeOfApplicability].every(
    (value) => !value?.trim(),
  );
  const isForm = ticket.source === 'SOLUTION_FORM';
  const isBusy = approve.isPending || dialog !== null;
  return (
    <Flex vertical gap="middle">
      {isEmpty && <Alert type="warning" showIcon title={msg('MSG20')} />}
      <Flex gap="middle" wrap>
        <Tooltip title={!isForm ? msg('MSG18') : undefined}>
          <span>
            <Button disabled={!isForm || isBusy} onClick={() => setDialog('changes')}>
              Request Changes
            </Button>
          </span>
        </Tooltip>
        <Button disabled={isBusy} onClick={() => setDialog('reject')}>
          Reject
        </Button>
        <Button
          type="primary"
          loading={approve.isPending}
          disabled={isEmpty || isBusy}
          onClick={() => approve.mutate({ data: { ticketIds: [ticket.ticketId] } })}
        >
          Approve
        </Button>
      </Flex>
      <RejectTicketModal
        ticketIds={[ticket.ticketId]}
        isOpen={dialog === 'reject'}
        onClose={() => setDialog(null)}
        onRejected={() => setDialog(null)}
      />
      <RequestChangesModal
        ticketId={ticket.ticketId}
        isOpen={dialog === 'changes'}
        onClose={() => setDialog(null)}
        onSent={() => setDialog(null)}
      />
    </Flex>
  );
}
