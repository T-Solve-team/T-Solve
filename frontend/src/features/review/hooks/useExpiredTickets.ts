import { keepPreviousData, useQueryClient } from '@tanstack/react-query';
import {
  useGetExpiredTickets,
  useSendBackTickets,
  getGetExpiredTicketsQueryKey,
  getGetExpiredTicketIdsQueryKey,
  getGetReviewQueueQueryKey,
  getGetReviewQueueTicketIdsQueryKey,
  getGetTicketsQueryKey,
  getGetReviewHistoryQueryKey,
  type GetExpiredTicketsParams,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

export function useExpiredTickets(params: GetExpiredTicketsParams) {
  return useGetExpiredTickets(params, { query: { placeholderData: keepPreviousData } });
}
export function useSendBackSelectedTickets() {
  const client = useQueryClient();
  return useSendBackTickets({
    mutation: {
      onSuccess: (result) => {
        showMessage('MSG25', { count: result.count });
        void client.invalidateQueries({ queryKey: getGetReviewHistoryQueryKey() });
      },
      onSettled: () => {
        const root = getGetTicketsQueryKey()[0];
        void Promise.all([
          ...[
            getGetExpiredTicketsQueryKey(),
            getGetExpiredTicketIdsQueryKey(),
            getGetReviewQueueQueryKey(),
            getGetReviewQueueTicketIdsQueryKey(),
          ].map((queryKey) => client.invalidateQueries({ queryKey })),
          client.invalidateQueries({
            predicate: ({ queryKey }) =>
              typeof queryKey[0] === 'string' &&
              (queryKey[0] === root || queryKey[0].startsWith(`${root}/`)),
          }),
        ]);
      },
    },
  });
}
