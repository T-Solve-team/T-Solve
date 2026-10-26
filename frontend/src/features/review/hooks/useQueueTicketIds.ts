import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { getGetReviewQueueTicketIdsQueryKey, getReviewQueueTicketIds } from '@/shared/api';

/**
 * "Select all" takes every ticket in the queue, not only the page shown (SRS 7.1). Returns a function that
 * loads all ticket ids for the current Department filter.
 */
export function useQueueTicketIds(departmentId: string | undefined) {
  const queryClient = useQueryClient();
  return useCallback(async () => {
    const params = departmentId === undefined ? undefined : { departmentId };
    const result = await queryClient.fetchQuery({
      queryKey: getGetReviewQueueTicketIdsQueryKey(params),
      queryFn: ({ signal }) => getReviewQueueTicketIds(params, { signal }),
      staleTime: 0, // always the queue as it is now
    });
    return result.ticketIds;
  }, [queryClient, departmentId]);
}
