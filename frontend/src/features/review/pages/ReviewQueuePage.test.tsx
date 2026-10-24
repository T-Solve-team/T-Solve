import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { REVIEW_BATCH_LIMIT } from '../constants';
import { seedReviewQueue } from '../mocks/data';
import { getGetReviewQueueMockHandler } from '@/shared/api/generated/review/review.msw';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

/** The Review Queue with the mock Project Manager; resolves once the first page is shown. */
async function openQueue(path = '/review-queue') {
  const user = userEvent.setup();
  const view = renderApp(path, { role: 'PROJECT_MANAGER' });
  // The first render also loads the lazy page module, which can take longer than findBy's default 1 s.
  await screen.findByRole('link', { name: 'ITSUP-1031' }, { timeout: 5000 });
  return { user, ...view };
}

describe('ReviewQueuePage', () => {
  // BUG-004: preload the lazy module so first-render waits cover the UI, not module loading.
  beforeAll(async () => {
    await import('./ReviewQueuePage');
  });

  it('AC-005.1, AC-005.3 drops stale selected IDs after 409 and retries the remaining selection (BUG-045, BUG-052)', async () => {
    let rows = seedReviewQueue().slice(0, 2);
    const requests: unknown[] = [];
    server.use(
      getGetReviewQueueMockHandler(() => ({
        content: rows.slice(0, 20),
        page: { size: 20, number: 0, totalElements: rows.length, totalPages: 1 },
      })),
      http.get('*/api/v1/review-queue/ticket-ids', () =>
        HttpResponse.json({ ticketIds: rows.map((row) => row.ticketId) }),
      ),
      http.post('*/api/v1/reviews/approve', async ({ request }) => {
        requests.push(await request.json());
        if (requests.length === 1) {
          rows = rows.filter((row) => row.ticketId !== 1);
          return problem(409);
        }
        rows = rows.filter((row) => row.ticketId !== 2);
        return HttpResponse.json({ count: 1 });
      }),
    );
    const { user } = await openQueue();
    await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1102' }));
    await user.click(screen.getByRole('button', { name: 'Approve' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));
    expect(await screen.findByText('1 selected')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'ITSUP-1031' })).not.toBeInTheDocument(),
    );
    await waitFor(() => expect(screen.getByRole('button', { name: 'Approve' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: 'Approve' }));
    expect(await screen.findByText(msg('MSG14', { count: 1 }))).toBeInTheDocument();
    expect(requests).toEqual([{ ticketIds: [1, 2] }, { ticketIds: [2] }]);
  });

  it('AC-001.2 clears selection on Project Back/Forward navigation (BUG-046, BUG-053)', async () => {
    server.use(
      http.get('*/api/v1/review-queue', ({ request }) => {
        const projectId = new URL(request.url).searchParams.get('projectId');
        const rows = seedReviewQueue()
          .slice(0, 3)
          .filter((row) => projectId === null || row.project.projectId === Number(projectId));
        return HttpResponse.json({
          content: rows,
          page: { size: 20, number: 0, totalElements: rows.length, totalPages: 1 },
        });
      }),
    );
    const { user, router } = await openQueue();
    await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    await user.click(screen.getByRole('combobox', { name: 'Project' }));
    await user.click(await screen.findByTitle('ITNET'));
    await screen.findByText('Total 1 items');
    await user.click(screen.getByRole('checkbox', { name: 'Select ITNET-0415' }));
    expect(screen.getByText('1 selected')).toBeInTheDocument();
    expect(router.state.location.search).toBe('?projectId=102');
    await act(async () => {
      await router.navigate(-1);
    });
    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(await screen.findByText('0 selected', {}, { timeout: 5000 })).toBeInTheDocument();
    await act(async () => {
      await router.navigate(1);
    });
    await waitFor(() => expect(router.state.location.search).toBe('?projectId=102'));
    expect(await screen.findByText('0 selected', {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('AC-005.8 keeps all 501 selected and blocks every decision (BUG-036, BUG-053)', async () => {
    const decisions = vi.fn();
    server.use(
      getGetReviewQueueMockHandler({
        content: seedReviewQueue().slice(0, 1),
        page: { size: 20, number: 0, totalElements: REVIEW_BATCH_LIMIT + 1, totalPages: 26 },
      }),
      http.get('*/api/v1/review-queue/ticket-ids', () =>
        HttpResponse.json({
          ticketIds: Array.from({ length: REVIEW_BATCH_LIMIT + 1 }, (_, index) => index + 1),
        }),
      ),
      http.post('*/api/v1/reviews/:action', () => {
        decisions();
        return HttpResponse.json({ count: 501 });
      }),
    );
    const { user } = await openQueue();
    await user.click(screen.getByRole('checkbox', { name: 'Select all' }));
    expect(await screen.findByText('501 selected')).toBeInTheDocument();
    expect(screen.getByText('Select up to 500 tickets per decision.')).toBeInTheDocument();
    for (const name of ['Approve', 'Reject', 'Request Changes']) {
      const action = screen.getByRole('button', { name });
      expect(action).toBeDisabled();
      await user.click(action);
    }
    expect(decisions).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    expect(screen.getByText('500 selected')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Approve' })).toBeEnabled();
  });

  it('AC-001.1 lists the pending tickets of my Projects, earliest review due date first, 20 per page (BUG-004, BUG-031)', async () => {
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
    expect(await screen.findByText(msg('MSG13'), {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('AC-001.4 shows the error in place, with Try again, when the queue cannot be loaded', async () => {
    server.use(http.get('*/api/v1/review-queue', () => problem(500)));
    renderApp('/review-queue');
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(msg('MSG06'));
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('AC-002.1, AC-002.3 approves one ticket at once (no confirm) and shows MSG14', async () => {
    const { user } = await openQueue();
    await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    await user.click(screen.getByRole('button', { name: 'Approve' }));
    expect(await screen.findByText(msg('MSG14', { count: 1 }))).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'ITSUP-1031' })).not.toBeInTheDocument(),
    );
    expect(screen.getByText('0 selected')).toBeInTheDocument();
  });

  it('AC-002.2 asks MSG19 before approving two tickets, then shows MSG14', async () => {
    const { user } = await openQueue();
    await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1102' }));
    expect(screen.getByText('2 selected')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Approve' }));

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent(msg('MSG19', { count: 2 }));
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));

    expect(await screen.findByText(msg('MSG14', { count: 2 }))).toBeInTheDocument();
    expect(await screen.findByText('Total 56 items')).toBeInTheDocument();
  });

  it('AC-003.1, AC-003.2, AC-003.3 rejects with a reason: MSG15 in the popup, MSG01 when empty, MSG16 when done (BUG-053)', async () => {
    const { user } = await openQueue();
    await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    await user.click(screen.getByRole('button', { name: 'Reject' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Reject Ticket')).toBeInTheDocument();
    expect(dialog).toHaveTextContent(msg('MSG15', { count: 1 }));

    await user.click(within(dialog).getByRole('button', { name: 'Reject' }));
    expect(
      await within(dialog).findByText(msg('MSG01', { field_name: 'Reason' })),
    ).toBeInTheDocument();

    await user.click(within(dialog).getByLabelText('Reason'));
    await user.paste('Does not fix the certificate error.');
    await user.click(within(dialog).getByRole('button', { name: 'Reject' }));
    expect(await screen.findByText(msg('MSG16', { count: 1 }))).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'ITSUP-1031' })).not.toBeInTheDocument(),
    );
  });

  it('AC-004.1, AC-004.2 applies one change comment to several selected tickets (BUG-053)', async () => {
    const { user } = await openQueue();
    await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    await user.click(screen.getByRole('checkbox', { name: 'Select ITNET-0415' }));
    await user.click(screen.getByRole('button', { name: 'Request Changes' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByLabelText('Comment'));
    await user.paste('Add reproduction steps');
    await user.click(within(dialog).getByRole('button', { name: 'Send' }));
    expect(await screen.findByText(msg('MSG17'))).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByRole('link', { name: 'ITSUP-1031' })).not.toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'ITNET-0415' })).not.toBeInTheDocument();
    });
    expect(screen.getByText('0 selected')).toBeInTheDocument();
  });

  it('AC-001.3, AC-004.1 applies one change comment to all tickets across pages', async () => {
    const { user } = await openQueue();
    await user.click(screen.getByRole('checkbox', { name: 'Select all' }));
    expect(await screen.findByText('58 selected')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Request Changes' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Comment'), 'Clarify the resolution');
    await user.click(within(dialog).getByRole('button', { name: 'Send' }));
    expect(await screen.findByText(msg('MSG17'))).toBeInTheDocument();
    expect(await screen.findByText(msg('MSG13'), {}, { timeout: 5000 })).toBeInTheDocument();
  });

  it('AC-006.5 reports a failed Select all request and preserves selection (BUG-030)', async () => {
    server.use(http.get('*/api/v1/review-queue/ticket-ids', () => problem(500)));
    const { user } = await openQueue();
    await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    await user.click(screen.getByRole('checkbox', { name: 'Select all' }));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(screen.getByText('1 selected')).toBeInTheDocument();
  });

  it('AC-001.2 filters by Project and keeps the filter in the URL (BUG-012, BUG-022, BUG-034)', async () => {
    const { user, router } = await openQueue();
    await user.click(screen.getByRole('combobox', { name: 'Project' }));
    expect(screen.queryByTitle('HRHELP')).not.toBeInTheDocument();
    await user.click(await screen.findByTitle('ITNET'));

    await waitFor(() => expect(router.state.location.search).toBe('?projectId=102'));
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'ITSUP-1031' })).not.toBeInTheDocument(),
    );
    expect(screen.getByRole('link', { name: 'ITNET-0415' })).toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: 'Select all' }));
    expect(await screen.findByText('19 selected')).toBeInTheDocument();
  });
});
