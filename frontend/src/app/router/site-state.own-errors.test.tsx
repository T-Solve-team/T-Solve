import type { QueryClient } from '@tanstack/react-query';
import { screen, waitFor } from '@testing-library/react';
import { http as mock } from 'msw';
import { server } from '@/mocks/server';
import { approveTickets, isApiError } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { SESSION_QUERY_KEY } from '@/shared/auth';
import { msg, showMessage } from '@/shared/messages';
import { renderApp } from '@/test/render';

/**
 * BUG-019: the first MSG119 / MSG120 can answer a mutation that shows its own errors (`handlesOwnErrors`: Reject,
 * Request changes, the chatbot's ask and rate). The site page, the dropped session / cache and the single text
 * (AC-011.5, edges 23 and 24) must be the same as for any other request.
 */
const SITE_ANSWERS = [
  ['MSG119', 404],
  ['MSG120', 403],
] as const;

async function openReviewQueue() {
  const rendered = renderApp('/review-queue');
  // The first findBy also loads the lazy page module: give it more time (guideline 10 §6).
  await screen.findByRole('heading', { level: 1, name: 'Review Queue' }, { timeout: 5000 });
  return rendered;
}

/** Like the Reject popup: the mutation sets `handlesOwnErrors` and the screen shows the error itself. */
function ownErrorsMutation(client: QueryClient) {
  return client
    .getMutationCache()
    .build(client, {
      mutationFn: () => approveTickets({ ticketIds: ['20000000-0000-4000-8000-000000000001'] }),
      meta: { handlesOwnErrors: true },
      onError: (error) => {
        if (isApiError(error)) showMessage(error.code, error.params);
      },
    })
    .execute(undefined)
    .catch(() => undefined);
}

describe('site state when a mutation that handles its own errors is answered first', () => {
  it.each(SITE_ANSWERS)(
    'BUG-019 drops the session and the cache when the first %s answers a mutation that handles its own errors',
    async (code, status) => {
      const { queryClient } = await openReviewQueue();
      expect(queryClient.getQueryData(SESSION_QUERY_KEY)).not.toBeNull();

      server.use(mock.post('*/api/v1/reviews/approve', () => problem(status, code)));
      await ownErrorsMutation(queryClient);

      expect(await screen.findByText(msg(code))).toBeInTheDocument();
      await waitFor(() => expect(queryClient.getQueryData(SESSION_QUERY_KEY)).toBeNull());
      const queues = queryClient.getQueriesData({ queryKey: ['/api/v1/review-queue'] });
      expect(queues.every(([, data]) => data === undefined)).toBe(true);
      expect(queryClient.getMutationCache().getAll()).toHaveLength(0);
      // The page only: no toast from the screen's own showMessage, no dialog.
      expect(screen.getAllByText(msg(code))).toHaveLength(1);
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    },
  );

  it('BUG-019 resets the session once when several such mutations answer MSG120 together', async () => {
    const { queryClient } = await openReviewQueue();
    const clear = vi.spyOn(queryClient.getMutationCache(), 'clear');

    server.use(mock.post('*/api/v1/reviews/approve', () => problem(403, 'MSG120')));
    await Promise.all([1, 2, 3].map(() => ownErrorsMutation(queryClient)));

    expect(await screen.findAllByText(msg('MSG120'))).toHaveLength(1);
    await waitFor(() => expect(clear).toHaveBeenCalled());
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(clear).toHaveBeenCalledTimes(1);
    expect(screen.getAllByText(msg('MSG120'))).toHaveLength(1);
  });
});
