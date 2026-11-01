import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { renderApp } from '@/test/render';
import { msg } from '@/shared/messages';
import { ReviewDecision, getGetReviewHistoryQueryKey } from '@/shared/api';
import { reviewStore } from '../mocks/state';
import { ticketUuid } from '../mocks/data';
import { server } from '@/mocks/server';
import { http, HttpResponse, delay } from 'msw';
import { problem } from '@/shared/api/mocks';

describe('Review History', () => {
  beforeAll(async () => {
    await import('./ReviewHistoryPage');
  });
  it('AC-008.6 shows MSG04 for an empty history', async () => {
    renderApp('/review-history');
    expect(await screen.findByText(msg('MSG04'), {}, { timeout: 5000 })).toBeInTheDocument();
  });
  it('AC-008.1 / AC-008.4 renders all six decisions, repeated tickets and hostile-looking content as text with no mutations', async () => {
    Object.values(ReviewDecision).forEach((action, index) =>
      reviewStore.history.push({
        decisionId: ticketUuid(900 + index),
        ticketId: ticketUuid(1),
        actorId: ticketUuid(800),
        actorName: 'Historical Manager',
        action,
        timestamp: `2026-11-0${index + 1}T12:00:00Z`,
        beforeStatus: 'PENDING_REVIEW',
        afterStatus: 'PUBLISHED',
        reason: action === 'REJECT' ? '<script>reason</script>' : null,
        comment: action === 'REQUEST_CHANGES' ? 'Please clarify' : null,
      }),
    );
    const ticket = reviewStore.tickets.find((entry) => entry.ticketId === ticketUuid(1));
    if (!ticket) throw new Error('Missing ticket');
    ticket.authorName = null;
    renderApp('/review-history');
    expect(
      await screen.findByText('<script>reason</script>', {}, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'ITSUP-1031' })).toHaveLength(6);
    for (const label of [
      'Approve',
      'Reject',
      'Request changes',
      'Unpublish',
      'Re-publish',
      'Send back to queue',
    ])
      expect(screen.getByText(label)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    const rows = within(screen.getByRole('table'))
      .getAllByRole('row')
      .filter((row) => within(row).queryByRole('link', { name: 'ITSUP-1031' }));
    expect(rows).toHaveLength(6);
    for (const row of rows) expect(within(row).getAllByRole('cell')[6]).toBeEmptyDOMElement();
  });
  it('AC-008.2 sends Department/decision/page/sort filters to the server and restores URL state with Back (BUG-093)', async () => {
    for (let index = 0; index < 25; index++)
      reviewStore.history.push({
        decisionId: ticketUuid(1000 + index),
        ticketId: ticketUuid(1),
        actorId: ticketUuid(800),
        actorName: 'Historical Manager',
        action: 'APPROVE',
        timestamp: new Date(Date.UTC(2026, 10, 1, index)).toISOString(),
        beforeStatus: 'PENDING_REVIEW',
        afterStatus: 'PUBLISHED',
        reason: null,
        comment: null,
      });
    const requests: URL[] = [];
    const onRequest = ({ request }: { request: Request }) => {
      const url = new URL(request.url);
      if (url.pathname === '/api/v1/review-history') requests.push(url);
    };
    server.events.on('request:start', onRequest);
    try {
      const view = renderApp('/review-history');
      await screen.findByText('Total 25 items', {}, { timeout: 5000 });
      fireEvent.click(screen.getByTitle('2'));
      await waitFor(() => expect(requests.at(-1)?.searchParams.get('page')).toBe('1'));
      expect(view.router.state.location.search).toContain('page=2');
      const dateHeader = screen
        .getAllByRole('columnheader')
        .find((header) => header.textContent === 'Date');
      if (!dateHeader) throw new Error('Missing accessible Date header');
      fireEvent.click(within(dateHeader).getByLabelText('caret-up'));
      await waitFor(() => expect(requests.at(-1)?.searchParams.get('sort')).toBe('timestamp,asc'));
      fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Decision' }));
      fireEvent.click(await screen.findByTitle('Reject'));
      await waitFor(() => expect(requests.at(-1)?.searchParams.get('decision')).toBe('REJECT'));
      expect(view.router.state.location.search).not.toContain('page=');
      fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Department' }));
      fireEvent.click(await screen.findByTitle('IT'));
      await waitFor(() =>
        expect(requests.at(-1)?.searchParams.get('departmentId')).toBe(
          '00000000-0000-4000-8000-0000000000d1',
        ),
      );
      await act(async () => {
        await view.router.navigate(-1);
      });
      expect(view.router.state.location.search).toContain('decision=REJECT');
      expect(view.router.state.location.search).not.toContain('departmentId=');
    } finally {
      server.events.removeListener('request:start', onRequest);
    }
  });
  it('AC-008.2 / AC-008.3 restores URL date filters with Back and issues no query for partial/invalid ranges', async () => {
    let reads = 0;
    const onRequest = ({ request }: { request: Request }) => {
      if (new URL(request.url).pathname === '/api/v1/review-history') reads++;
    };
    server.events.on('request:start', onRequest);
    try {
      const view = renderApp('/review-history?dateFrom=2026-11-01&page=3');
      expect(await screen.findByText(msg('MSG39'), {}, { timeout: 5000 })).toBeInTheDocument();
      expect(reads).toBe(0);
      fireEvent.change(screen.getByLabelText('To date'), { target: { value: '2026-11-02' } });
      await screen.findByText(msg('MSG04'));
      expect(view.router.state.location.search).not.toContain('page=');
      expect(view.router.state.location.search).toContain('dateTo=2026-11-02');
      await act(async () => {
        await view.router.navigate(-1);
      });
      expect(await screen.findByText(msg('MSG39'))).toBeInTheDocument();
      expect(screen.getByLabelText('From date')).toHaveValue('2026-11-01');
    } finally {
      server.events.removeListener('request:start', onRequest);
    }
  });
  it('AC-008.6 shows loading and error, then retries the real history query', async () => {
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    server.use(
      http.get('*/api/v1/review-history', async () => {
        await pending;
        return problem(500);
      }),
    );
    renderApp('/review-history');
    try {
      expect(await screen.findByText('Loading ...', {}, { timeout: 5000 })).toBeInTheDocument();
    } finally {
      finish?.();
    }
    expect(await screen.findByRole('alert')).toHaveTextContent(msg('MSG06'));
    server.use(
      http.get('*/api/v1/review-history', async () => {
        await delay(10);
        return HttpResponse.json({
          content: [],
          page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
        });
      }),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText(msg('MSG04'))).toBeInTheDocument();
  });
  it('AC-008.5 invalidates cached history after an existing real Approve mutation', async () => {
    const view = renderApp('/review-history');
    await screen.findByText(msg('MSG04'), {}, { timeout: 5000 });
    await waitFor(() => expect(view.queryClient.isFetching()).toBe(0));
    await act(async () => {
      await view.router.navigate('/tickets/' + ticketUuid(1));
    });
    await screen.findByRole('button', { name: 'Approve' });
    fireEvent.click(screen.getByRole('button', { name: 'Approve' }));
    await waitFor(() => expect(reviewStore.history).toHaveLength(1));
    expect(
      view.queryClient.getQueryState(
        getGetReviewHistoryQueryKey({ page: 0, size: 20, sort: ['timestamp,desc'] }),
      )?.isInvalidated,
    ).toBe(true);
    await act(async () => {
      await view.router.navigate('/review-history');
    });
    expect(await screen.findByRole('link', { name: 'ITSUP-1031' })).toBeInTheDocument();
  });
});
