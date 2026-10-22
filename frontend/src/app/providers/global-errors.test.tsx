import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http as mock } from 'msw';
import { server } from '@/mocks/server';
import { approveTickets, getReviewQueue } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { setDevUser } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

describe('global error handling', () => {
  it('shows a failed mutation as a toast with its MSG code (BUG-003)', async () => {
    server.use(mock.post('*/api/v1/reviews/approve', () => problem(403)));
    const { queryClient } = renderApp('/review-queue');
    // The first findBy also loads the lazy page module: give it more time (guideline 10 §6).
    await screen.findByRole('heading', { level: 1, name: 'Review Queue' }, { timeout: 5000 });

    await queryClient
      .getMutationCache()
      .build(queryClient, { mutationFn: () => approveTickets({ ticketIds: [1] }) })
      .execute(undefined)
      .catch(() => undefined);

    expect(await screen.findByText(msg('MSG08'))).toBeInTheDocument();
  });

  it('stays quiet for a mutation that shows its own error', async () => {
    server.use(mock.post('*/api/v1/reviews/approve', () => problem(409, 'MSG47')));
    const { queryClient } = renderApp('/review-queue');
    await screen.findByRole('heading', { level: 1, name: 'Review Queue' });

    await queryClient
      .getMutationCache()
      .build(queryClient, {
        mutationFn: () => approveTickets({ ticketIds: [1] }),
        meta: { handlesOwnErrors: true },
      })
      .execute(undefined)
      .catch(() => undefined);

    expect(screen.queryByText(msg('MSG47'))).not.toBeInTheDocument();
  });

  it('on 401 shows MSG07, then sends the user to Login with returnTo', async () => {
    const user = userEvent.setup();
    const { queryClient, router } = renderApp('/review-queue');
    await screen.findByRole('heading', { level: 1, name: 'Review Queue' });

    // The session expires on the server: every request now answers 401.
    setDevUser(null);
    server.use(mock.get('*/api/v1/review-queue', () => problem(401)));
    await queryClient
      .fetchQuery({ queryKey: ['expired'], queryFn: () => getReviewQueue() })
      .catch(() => undefined);

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(msg('MSG07'));
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(router.state.location.search).toBe(`?returnTo=${encodeURIComponent('/review-queue')}`);
  });

  it('shows MSG06 when the session cannot be loaded at all', async () => {
    server.use(mock.get('*/api/v1/me', () => problem(500)));
    renderApp('/review-queue');
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('logs out through the API and lands on Login', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/review-queue');
    await user.click(await screen.findByRole('button', { name: /Đỗ Minh Quân/ }));
    await user.click(await screen.findByText('Log out'));
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
  });
});
