import { useQueryClient } from '@tanstack/react-query';
import { Button, Flex } from 'antd';
import { useState } from 'react';
import {
  getGetReviewHistoryQueryKey,
  getGetReviewTicketDetailQueryKey,
  getGetTicketsQueryKey,
  useGetReviewTicketDetail,
  useRepublishTickets,
  useUnpublishTickets,
} from '@/shared/api';
import { useSession } from '@/shared/auth';
import { showMessage } from '@/shared/messages';
import { ConfirmDialog } from '@/shared/ui';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/**
 * Unpublish / Re-publish of Ticket Detail (SRS 5.5, UC-25): only for the Project Manager of the ticket's Project.
 * Reads the ticket with the query `ReviewTicketDetail` uses, so there is one request. Errors: global toast.
 */
export function TicketPublicationActions({ ticketId }: { ticketId: string }) {
  const queryClient = useQueryClient();
  const detail = useGetReviewTicketDetail(ticketId, {
    query: { enabled: UUID_PATTERN.test(ticketId) },
  });
  const { hasRole, isProjectManagerOf } = useSession();
  const [isAsking, setIsAsking] = useState(false);

  /** Success or error: show the stored status here, in the Ticket List and in Review History. */
  const mutation = {
    onSettled: () => {
      setIsAsking(false);
      const ticketRoot = getGetTicketsQueryKey()[0];
      void queryClient.invalidateQueries({ queryKey: getGetReviewTicketDetailQueryKey(ticketId) });
      void queryClient.invalidateQueries({ queryKey: getGetReviewHistoryQueryKey() });
      void queryClient.invalidateQueries({
        predicate: ({ queryKey }) =>
          typeof queryKey[0] === 'string' &&
          (queryKey[0] === ticketRoot || queryKey[0].startsWith(`${ticketRoot}/`)),
      });
    },
  };
  const unpublish = useUnpublishTickets({
    mutation: { ...mutation, onSuccess: () => showMessage('MSG22') },
  });
  const republish = useRepublishTickets({
    mutation: { ...mutation, onSuccess: () => showMessage('MSG24') },
  });

  const ticket = detail.data;
  if (
    !ticket ||
    detail.isError ||
    (ticket.status !== 'PUBLISHED' && ticket.status !== 'UNPUBLISHED') ||
    !hasRole('PROJECT_MANAGER') ||
    !isProjectManagerOf(ticket.project.projectId)
  ) {
    return null;
  }
  const isPublished = ticket.status === 'PUBLISHED';
  const action = isPublished ? unpublish : republish;

  return (
    <Flex>
      <Button
        danger={isPublished}
        disabled={detail.isFetching || action.isPending}
        onClick={() => setIsAsking(true)}
      >
        {isPublished ? 'Unpublish' : 'Re-publish'}
      </Button>
      <ConfirmDialog
        isOpen={isAsking}
        code={isPublished ? 'MSG21' : 'MSG23'}
        isDanger={isPublished}
        isConfirming={action.isPending}
        onConfirm={() => action.mutate({ data: { ticketIds: [ticketId] } })}
        onCancel={() => setIsAsking(false)}
      />
    </Flex>
  );
}
