import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/render';
import { getGetReviewHistoryQueryOptions, getGetReviewHistoryQueryKey } from '@/shared/api';
import {
  useApproveSelectedTickets,
  useRejectSelectedTickets,
  useRequestChangesForTickets,
} from './useReviewDecisions';
import { useSendBackSelectedTickets } from './useExpiredTickets';
import { reviewStore } from '../mocks/state';
import { ticketUuid } from '../mocks/data';

type Action = 'APPROVE' | 'REJECT' | 'REQUEST_CHANGES' | 'SEND_BACK';
function DecisionButton({ action }: { action: Action }) {
  const approve = useApproveSelectedTickets();
  const reject = useRejectSelectedTickets();
  const changes = useRequestChangesForTickets();
  const sendBack = useSendBackSelectedTickets();
  return (
    <button
      onClick={() => {
        const ticketIds = [ticketUuid(action === 'SEND_BACK' ? 300 : 1)];
        if (action === 'APPROVE') approve.mutate({ data: { ticketIds } });
        if (action === 'REJECT') reject.mutate({ data: { ticketIds, reason: 'Reason snapshot' } });
        if (action === 'REQUEST_CHANGES')
          changes.mutate({ data: { ticketIds, comment: 'Comment snapshot' } });
        if (action === 'SEND_BACK') sendBack.mutate({ data: { ticketIds } });
      }}
    >
      Decide
    </button>
  );
}
describe('Review History cache invalidation', () => {
  it.each(['APPROVE', 'REJECT', 'REQUEST_CHANGES', 'SEND_BACK'] as const)(
    'AC-008.5 invalidates cached history after real %s hook succeeds',
    async (action) => {
      const { queryClient } = renderWithProviders(<DecisionButton action={action} />);
      await act(async () => {
        await queryClient.fetchQuery(getGetReviewHistoryQueryOptions());
      });
      expect(queryClient.getQueryState(getGetReviewHistoryQueryKey())?.isInvalidated).toBe(false);
      fireEvent.click(screen.getByRole('button', { name: 'Decide' }));
      await waitFor(() => expect(reviewStore.history).toHaveLength(1));
      await waitFor(() => expect(queryClient.isMutating()).toBe(0));
      expect(reviewStore.history[0]?.action).toBe(action);
      expect(queryClient.getQueryState(getGetReviewHistoryQueryKey())?.isInvalidated).toBe(true);
    },
  );
});
