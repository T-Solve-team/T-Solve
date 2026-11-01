import { screen, within, fireEvent, waitFor, act } from '@testing-library/react';
import type { QueryClient } from '@tanstack/react-query';
import { renderApp } from '@/test/render';
import { reviewStore } from '../mocks/state';
import { http, HttpResponse, delay } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { ticketUuid } from '../mocks/data';
import {
  getGetExpiredTicketsQueryKey,
  getGetExpiredTicketsQueryOptions,
  getGetExpiredTicketIdsQueryKey,
  getGetReviewQueueQueryKey,
  getGetReviewQueueTicketIdsQueryKey,
} from '@/shared/api';

async function submitAndAwaitMutation(queryClient: QueryClient, button: HTMLElement) {
  let unsubscribe = () => {};
  const settled = new Promise<void>((resolve) => {
    unsubscribe = queryClient.getMutationCache().subscribe((event) => {
      if (
        event.type === 'updated' &&
        (event.mutation.state.status === 'success' || event.mutation.state.status === 'error')
      )
        resolve();
    });
  });
  try {
    await act(async () => {
      fireEvent.click(button);
      await settled;
    });
  } finally {
    unsubscribe();
  }
}

async function refreshExpiredSelection(queryClient: QueryClient) {
  // Accept the list's new version before waiting for its matching eligibility request.
  await act(async () => {
    await queryClient.refetchQueries({ queryKey: getGetExpiredTicketsQueryKey() });
  });
  await act(async () => {
    await queryClient.refetchQueries({ queryKey: getGetExpiredTicketIdsQueryKey() });
  });
}

describe('Expired Tickets', () => {
  beforeAll(async () => {
    await import('./ExpiredTicketsPage');
  });
  it('AC-007.1 lists expired tickets with detail links and expiry metadata', async () => {
    renderApp('/expired-tickets');
    const table = await screen.findByRole('table', {}, { timeout: 5000 });
    await screen.findByRole('link', { name: 'EXP-323' }, { timeout: 5000 });
    expect(within(table).getAllByText('Expired on').length).toBeGreaterThan(0);
    expect(within(table).getAllByText('Previous status').length).toBeGreaterThan(0);
    expect(within(table).getAllByRole('link')).toHaveLength(20);
    expect(screen.getByText('Total 24 items')).toBeInTheDocument();
  });
  it('AC-007.3 / AC-007.4 selects all filtered tickets across pages and sends them back', async () => {
    renderApp('/expired-tickets');
    await screen.findByRole('link', { name: 'EXP-323' }, { timeout: 5000 });
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select all' }));
    const button = screen.getByRole('button', { name: 'Send back to queue' });
    await waitFor(() => expect(button).toBeEnabled());
    fireEvent.click(button);
    await waitFor(() =>
      expect(reviewStore.history.filter((entry) => entry.action === 'SEND_BACK')).toHaveLength(24),
    );
  });
  it('AC-007.6 shows empty results and recovers from catalog errors with retry', async () => {
    server.use(
      http.get('*/api/v1/expired-tickets', async () => {
        await delay(80);
        return HttpResponse.json({
          content: [],
          page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
        });
      }),
    );
    const view = renderApp('/expired-tickets');
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
    server.use(http.get('*/api/v1/expired-tickets', () => problem(500)));
    await act(async () => {
      await view.queryClient.invalidateQueries({ queryKey: getGetExpiredTicketsQueryKey() });
    });
    expect(await screen.findByRole('alert')).toHaveTextContent(msg('MSG06'));
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
    server.use(
      http.get('*/api/v1/expired-tickets', () =>
        HttpResponse.json({
          content: [],
          page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
        }),
      ),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText(msg('MSG04'))).toBeInTheDocument();
  });
  it('AC-007.7 preserves selection on failure and prunes stale selection after conflict (BUG-092)', async () => {
    const view = renderApp('/expired-tickets');
    await screen.findByRole('link', { name: 'EXP-323' }, { timeout: 5000 });
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select EXP-323' }));
    const button = screen.getByRole('button', { name: 'Send back to queue' });
    await waitFor(() => expect(button).toBeEnabled());
    const invalidations = vi.spyOn(view.queryClient, 'invalidateQueries');
    server.use(http.post('*/api/v1/reviews/send-back', () => problem(500)));
    await submitAndAwaitMutation(view.queryClient, button);
    await screen.findByText(msg('MSG06'));
    for (const queryKey of [
      getGetExpiredTicketsQueryKey(),
      getGetExpiredTicketIdsQueryKey(),
      getGetReviewQueueQueryKey(),
      getGetReviewQueueTicketIdsQueryKey(),
    ])
      expect(invalidations).toHaveBeenCalledWith({ queryKey });
    await refreshExpiredSelection(view.queryClient);
    await waitFor(() => expect(button).toBeEnabled());
    expect(screen.getByText('1 selected')).toBeInTheDocument();
    const ticket = reviewStore.tickets.find((t) => t.ticketId === ticketUuid(323));
    if (!ticket) throw new Error('Missing fixture');
    ticket.status = 'PENDING_REVIEW';
    server.use(http.post('*/api/v1/reviews/send-back', () => problem(409)));
    invalidations.mockClear();
    await submitAndAwaitMutation(view.queryClient, button);
    expect(invalidations).toHaveBeenCalledWith({ queryKey: getGetExpiredTicketsQueryKey() });
    expect(invalidations).toHaveBeenCalledWith({ queryKey: getGetExpiredTicketIdsQueryKey() });
    await refreshExpiredSelection(view.queryClient);
    await waitFor(() => expect(screen.getByText('0 selected')).toBeInTheDocument());
    expect(button).toBeDisabled();
    expect(view.queryClient.isMutating()).toBe(0);
  });
  it('AC-007.2 / AC-007.3 clears selection on Department navigation and preserves URL paging', async () => {
    const view = renderApp('/expired-tickets');
    await screen.findByRole('link', { name: 'EXP-323' }, { timeout: 5000 });
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select EXP-323' }));
    expect(screen.getByText('1 selected')).toBeInTheDocument();
    await act(async () => {
      await view.router.navigate(
        '/expired-tickets?departmentId=00000000-0000-4000-8000-0000000000d2&page=2',
      );
    });
    expect(await screen.findByText('0 selected')).toBeInTheDocument();
    expect(await screen.findByText(msg('MSG04'))).toBeInTheDocument();
    expect(view.router.state.location.search).toContain('page=2');
  });
  it('AC-007.5 disables an oversized selection instead of silently truncating', async () => {
    server.use(
      http.get('*/api/v1/expired-tickets/ticket-ids', () =>
        HttpResponse.json({
          ticketIds: Array.from({ length: 501 }, (_, i) => ticketUuid(i + 300)),
        }),
      ),
    );
    renderApp('/expired-tickets');
    await screen.findByRole('link', { name: 'EXP-323' }, { timeout: 5000 });
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select all' }));
    expect(await screen.findByText('501 selected / 500 maximum')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Send back to queue' })).toBeDisabled();
  });
  it('AC-007.5 / AC-007.7 prevents duplicate sends and retains success when the subsequent list refetch fails', async () => {
    const view = renderApp('/expired-tickets');
    await screen.findByRole('link', { name: 'EXP-323' }, { timeout: 5000 });
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select EXP-323' }));
    const button = screen.getByRole('button', { name: 'Send back to queue' });
    await waitFor(() => expect(button).toBeEnabled());
    let sends = 0;
    server.use(
      http.post('*/api/v1/reviews/send-back', async () => {
        sends++;
        await delay(120);
        server.use(http.get('*/api/v1/expired-tickets', () => problem(500)));
        return HttpResponse.json({ count: 1 });
      }),
    );
    fireEvent.click(button);
    fireEvent.click(button);
    await waitFor(() => expect(button).toBeDisabled());
    expect(await screen.findByText(msg('MSG25', { count: 1 }))).toBeInTheDocument();
    await waitFor(() =>
      expect(view.queryClient.isMutating() + view.queryClient.isFetching()).toBe(0),
    );
    expect(sends).toBe(1);
    expect(screen.getByText('0 selected')).toBeInTheDocument();
    expect(
      screen.getAllByRole('alert').some((element) => element.textContent?.includes(msg('MSG06'))),
    ).toBe(true);
  });
  it('AC-007.3 retains individual selections across pages and resets on browser Back across Departments (BUG-091)', async () => {
    const view = renderApp('/expired-tickets');
    await screen.findByRole('link', { name: 'EXP-323' }, { timeout: 5000 });
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select EXP-323' }));
    // Load the real second page before exercising selection/navigation. Navigation itself
    // does not await React Query data, and refetching active queries can still target page 1.
    await act(async () => {
      await view.queryClient.ensureQueryData(
        getGetExpiredTicketsQueryOptions({ page: 1, size: 20, sort: ['expiredAt,desc'] }),
      );
    });
    await act(async () => {
      await view.router.navigate('/expired-tickets?page=2');
    });
    expect(await screen.findByRole('link', { name: 'EXP-303' })).toBeInTheDocument();
    expect(screen.getByText('1 selected')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select EXP-303' }));
    expect(screen.getByText('2 selected')).toBeInTheDocument();
    await act(async () => {
      await view.router.navigate(
        '/expired-tickets?departmentId=00000000-0000-4000-8000-0000000000d2',
      );
    });
    expect(await screen.findByText('0 selected')).toBeInTheDocument();
    await act(async () => {
      await view.router.navigate(-1);
    });
    expect(await screen.findByRole('link', { name: 'EXP-303' })).toBeInTheDocument();
    expect(screen.getByText('0 selected')).toBeInTheDocument();
  });
});
