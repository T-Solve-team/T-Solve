import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import {
  getGetReviewQueueQueryKey,
  getGetReviewQueueTicketIdsQueryKey,
  useApproveTickets,
  useRejectTickets,
  useRequestTicketChanges,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

/** Every decision changes the queue: refetch all its pages and the ids used by "Select all". */
function refreshQueue(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: getGetReviewQueueQueryKey() }),
    queryClient.invalidateQueries({ queryKey: getGetReviewQueueTicketIdsQueryKey() }),
  ]);
}

/** Approve (SRS 7.1, UC-38): publishes the tickets, then MSG14. Errors: global toast. */
export function useApproveSelectedTickets() {
  const queryClient = useQueryClient();
  return useApproveTickets({
    mutation: {
      onSuccess: (result) => {
        showMessage('MSG14', { count: result.count });
        return refreshQueue(queryClient);
      },
    },
  });
}

/** Reject (SRS 7.3, UC-39): one reason for all selected tickets, then MSG16. The popup shows errors. */
export function useRejectSelectedTickets() {
  const queryClient = useQueryClient();
  return useRejectTickets({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: (result) => {
        showMessage('MSG16', { count: result.count });
        return refreshQueue(queryClient);
      },
    },
  });
}

/** Request changes (SRS 7.2, decision FE-34): one comment for all selected tickets, then MSG17. */
export function useRequestChangesForTickets() {
  const queryClient = useQueryClient();
  return useRequestTicketChanges({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: () => {
        showMessage('MSG17');
        return refreshQueue(queryClient);
      },
    },
  });
}
