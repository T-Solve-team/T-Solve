import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { server } from '@/mocks/server';
import { getGetReviewQueueMockHandler } from '@/shared/api/generated/review/review.msw';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
import { getGetReviewQueueTicketIdsQueryKey } from '@/shared/api';
import { reviewStore } from '../mocks/state';

/** The Review Queue with the mock Project Manager; resolves once the first page is shown. */
async function openQueue(path = '/review-queue') {
  const user = userEvent.setup();
  const view = renderApp(path, { role: 'PROJECT_MANAGER' });
  // The first render also loads the lazy page module, which can take longer than findBy's default 1 s.
  await screen.findByRole('link', { name: 'ITSUP-1031' }, { timeout: 5000 });
  return { user, ...view };
}

/** BUG-084: the 5 s poll can disable a button between userEvent's hover and click steps. */
async function clickDecision(name: string) {
  const button = screen.getByRole('button', { name });
  await waitFor(() => {
    expect(button).toBeEnabled();
    // Check and activate in one turn; no action is sent while eligibility is refreshing.
    fireEvent.click(button);
  });
}

/** Native events test the same form/selection behavior without per-character/hover work on the large table. */
async function clickControl(control: HTMLElement) {
  await waitFor(() => expect(control).toBeEnabled());
  fireEvent.click(control);
}

describe('ReviewQueuePage', () => {
  // BUG-004: load the lazy page module once before the tests, so the first test's 5 s wait covers only the
  // render, not the module load as well (guideline 10 §6 forbids longer timeouts).
  beforeAll(async () => {
    await import('./ReviewQueuePage');
  });

  it('AC-001.1 lists the pending tickets of my Projects, earliest review due date first, 20 per page (BUG-004)', async () => {
    await openQueue();
    const table = screen.getByRole('table');
    const rows = within(table).getAllByRole('row').slice(1); // without the header row
    expect(rows).toHaveLength(20);
    expect(rows[0]).toHaveTextContent('ITSUP-1031');
    expect(rows[0]).toHaveTextContent('04/10/2026 18:35'); // review due date in Vietnam time
    expect(screen.getByText('Total 58 items')).toBeInTheDocument();
  });

  it('AC-001.4 shows MSG13 when no tickets are waiting', async () => {
    server.use(
      getGetReviewQueueMockHandler({
        content: [],
        page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
      }),
    );
    renderApp('/review-queue');
    // BUG-004: every test's first findBy after a render waits up to 5 s (guideline 10 §6), not only the file's first.
    expect(await screen.findByText(msg('MSG13'), {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('AC-001.4 shows the error in place, with Try again, when the queue cannot be loaded', async () => {
    server.use(http.get('*/api/v1/review-queue', () => problem(500)));
    renderApp('/review-queue');
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG06'));
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('AC-002.1 / AC-002.3 approves one ticket at once (no confirm) and shows MSG14', async () => {
    await openQueue();
    await clickControl(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    await clickDecision('Approve');
    expect(await screen.findByText(msg('MSG14', { count: 1 }))).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'ITSUP-1031' })).not.toBeInTheDocument(),
    );
    expect(screen.getByText('0 selected')).toBeInTheDocument();
  });

  it('AC-002.2 / AC-002.5 asks MSG19, cancels without writes and then approves two tickets (BUG-090)', async () => {
    const { queryClient } = await openQueue();
    await clickControl(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    await clickControl(screen.getByRole('checkbox', { name: 'Select ITSUP-1102' }));
    expect(screen.getByText('2 selected')).toBeInTheDocument();
    await clickDecision('Approve');

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(msg('MSG19', { count: 2 }));
    await clickControl(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(screen.getByText('2 selected')).toBeInTheDocument();
    expect(reviewStore.history).toEqual([]);
    await clickDecision('Approve');
    const confirmation = within(await screen.findByRole('dialog'));
    // The queue poll can refresh selection eligibility while its confirmation remains open.
    await act(async () => {
      await queryClient.refetchQueries({ queryKey: getGetReviewQueueTicketIdsQueryKey() });
    });
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    fireEvent.click(confirmation.getByRole('button', { name: 'OK' }));
    await waitFor(() => expect(reviewStore.history).toHaveLength(2));
    await waitFor(() => expect(queryClient.isMutating()).toBe(0));

    expect(await screen.findByText(msg('MSG14', { count: 2 }))).toBeInTheDocument();
    expect(await screen.findByText('Total 56 items')).toBeInTheDocument();
  });

  it('AC-003.1 / AC-003.2 / AC-003.3 rejects with MSG15, requires one reason and shows MSG16 (BUG-084)', async () => {
    const { queryClient } = await openQueue();
    await clickControl(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    await clickDecision('Reject');

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Reject Ticket')).toBeInTheDocument();
    expect(dialog).toHaveTextContent(msg('MSG15', { count: 1 }));

    await clickControl(within(dialog).getByRole('button', { name: 'Reject' }));
    expect(
      await within(dialog).findByText(msg('MSG01', { field_name: 'Reason' })),
    ).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText('Reason'), {
      target: { value: 'Does not fix the certificate error.' },
    });
    await clickControl(within(dialog).getByRole('button', { name: 'Reject' }));
    expect(await screen.findByText(msg('MSG16', { count: 1 }))).toBeInTheDocument();
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'ITSUP-1031' })).not.toBeInTheDocument(),
    );
  });

  it('AC-004.1 / AC-004.3 requests changes for every selected ticket with one comment (MSG17, BUG-084)', async () => {
    const { queryClient } = await openQueue();
    await clickControl(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    await clickControl(screen.getByRole('checkbox', { name: 'Select ITNET-0415' }));
    await clickDecision('Request Changes');

    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('Comment'), {
      target: { value: 'Please add the exact error message.' },
    });
    await clickControl(within(dialog).getByRole('button', { name: 'Send' }));
    await waitFor(() => expect(reviewStore.history).toHaveLength(2));
    await waitFor(() => expect(queryClient.isMutating()).toBe(0));

    expect(await screen.findByText(msg('MSG17'))).toBeInTheDocument();
    expect(await screen.findByText('Total 56 items')).toBeInTheDocument();
  });

  it('AC-001.3 "Select all" selects every ticket in the queue, not only this page', async () => {
    const { queryClient } = await openQueue();
    await clickControl(screen.getByRole('checkbox', { name: 'Select all' }));
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));
    expect(await screen.findByText('58 selected')).toBeInTheDocument();
  });

  it('AC-001.2 filters by Department and keeps the filter in the URL', async () => {
    const { user, router } = await openQueue();
    await user.click(screen.getByRole('combobox', { name: 'Department' }));
    // The mock Project Manager has exactly one Department, IT (GBR-16).
    await user.click(await screen.findByTitle('IT'));

    await waitFor(() =>
      expect(router.state.location.search).toBe(
        '?departmentId=00000000-0000-4000-8000-0000000000d1',
      ),
    );
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'HRHELP-0318' })).not.toBeInTheDocument(),
    );
    expect(screen.getByRole('link', { name: 'ITSUP-1031' })).toBeInTheDocument();
  });

  it('BUG-017 ignores a departmentId that is not a UUID', async () => {
    const queueUrls: URL[] = [];
    const onRequest = ({ request }: { request: Request }) => {
      const url = new URL(request.url);
      if (url.pathname.endsWith('/review-queue')) queueUrls.push(url);
    };
    server.events.on('request:start', onRequest);
    try {
      renderApp('/review-queue?departmentId=abc', { role: 'PROJECT_MANAGER' });
      await waitFor(() => expect(queueUrls.length).toBeGreaterThan(0), { timeout: 5000 });
    } finally {
      server.events.removeListener('request:start', onRequest);
    }
    expect(queueUrls.some((url) => url.searchParams.has('departmentId'))).toBe(false);
  });
});
