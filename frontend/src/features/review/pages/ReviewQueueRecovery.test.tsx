import { act, cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { getGetReviewQueueTicketIdsQueryKey } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

const ticketId = (n: number) => `20000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const departmentId = '00000000-0000-4000-8000-0000000000d1';
const first = {
  ticketId: ticketId(1),
  sourceTicketId: 'ITSUP-1031',
  title: 'First pending Solution',
  project: { projectId: '00000000-0000-4000-8000-000000000101', name: 'IT Support' },
  source: 'SOLUTION_FORM',
  authorName: 'Writer',
  enteredQueueAt: '2026-10-01T11:35:00Z',
  reviewDueDate: '2026-10-04T11:35:00Z',
};
const second = {
  ...first,
  ticketId: ticketId(2),
  sourceTicketId: 'ITSUP-1102',
  title: 'Second pending Solution',
};
const clients = new Set<ReturnType<typeof renderApp>['queryClient']>();

async function openQueue() {
  const view = renderApp('/review-queue', { role: 'PROJECT_MANAGER' });
  clients.add(view.queryClient);
  await screen.findByRole('link', { name: first.sourceTicketId }, { timeout: 5000 });
  return { ...view, user: userEvent.setup() };
}

describe('Review Queue decision recovery', () => {
  beforeAll(async () => {
    await import('./ReviewQueuePage');
  });
  afterEach(async () => {
    for (const client of clients) await waitFor(() => expect(client.isMutating()).toBe(0));
    cleanup();
    await Promise.all([...clients].map((client) => client.cancelQueries()));
    for (const client of clients) client.clear();
    clients.clear();
  });

  function queueResponse(rows = [first, second]) {
    return HttpResponse.json({
      content: rows,
      page: { size: 20, number: 0, totalElements: rows.length, totalPages: 1 },
    });
  }

  it('AC-005.1 sends selected UUIDs unchanged rather than numeric coercion (BUG-080)', async () => {
    let submitted: unknown;
    server.use(
      http.get('*/api/v1/review-queue', () => queueResponse()),
      http.get('*/api/v1/review-queue/ticket-ids', () =>
        HttpResponse.json({ ticketIds: [ticketId(1), ticketId(2)] }),
      ),
      http.post('*/api/v1/reviews/approve', async ({ request }) => {
        submitted = await request.json();
        return HttpResponse.json({ count: 1 });
      }),
    );
    const { user } = await openQueue();
    await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
    await user.click(screen.getByRole('button', { name: 'Approve' }));
    await waitFor(() => expect(submitted).toEqual({ ticketIds: [ticketId(1)] }));
  });

  it('AC-001.6 / AC-005.4 refreshes a rejected batch and retries only eligible off-page-safe selection (BUG-082)', async () => {
    let rows = [first, second];
    const submissions: unknown[] = [];
    server.use(
      http.get('*/api/v1/review-queue', () => queueResponse(rows)),
      http.get('*/api/v1/review-queue/ticket-ids', () =>
        HttpResponse.json({ ticketIds: rows.map((row) => row.ticketId) }),
      ),
      http.post('*/api/v1/reviews/approve', async ({ request }) => {
        submissions.push(await request.json());
        if (submissions.length === 1) {
          rows = [second];
          return problem(409);
        }
        rows = [];
        return HttpResponse.json({ count: 1 });
      }),
    );
    const { user } = await openQueue();
    for (const name of ['Select ITSUP-1031', 'Select ITSUP-1102'])
      await user.click(screen.getByRole('checkbox', { name }));
    await user.click(screen.getByRole('button', { name: 'Approve' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: first.sourceTicketId })).not.toBeInTheDocument(),
    );
    expect(await screen.findByText(/1 selected/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Approve' }));
    await waitFor(() =>
      expect(submissions).toEqual([
        { ticketIds: [ticketId(1), ticketId(2)] },
        { ticketIds: [ticketId(2)] },
      ]),
    );
  });

  it('AC-005.5 / AC-005.6 retains failed popup input and closes successful retry before slow refetches (BUG-083)', async () => {
    let attempts = 0;
    let refresh = false;
    let release: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.get('*/api/v1/review-queue', async () => {
        if (refresh) await pending;
        return queueResponse();
      }),
      http.get('*/api/v1/review-queue/ticket-ids', () =>
        HttpResponse.json({ ticketIds: [ticketId(1), ticketId(2)] }),
      ),
      http.post('*/api/v1/reviews/reject', () => {
        attempts++;
        if (attempts === 1) return problem(500);
        refresh = true;
        return HttpResponse.json({ count: 1 });
      }),
    );
    const { user, queryClient } = await openQueue();
    try {
      await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
      await user.click(screen.getByRole('button', { name: 'Reject' }));
      const dialog = await screen.findByRole('dialog');
      await user.click(within(dialog).getByLabelText('Reason'));
      await user.paste('The Solution needs a different approach.');
      await user.click(within(dialog).getByRole('button', { name: 'Reject' }));
      expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
      expect(within(dialog).getByLabelText('Reason')).toHaveValue(
        'The Solution needs a different approach.',
      );
      await user.click(within(dialog).getByRole('button', { name: 'Reject' }));
      await waitFor(() => expect(queryClient.isMutating()).toBe(0));
      // The popup is already leaving while the deliberately blocked queue request is still pending.
      expect(dialog).toHaveClass('ant-zoom-leave');
      expect(screen.getByText('0 selected')).toBeInTheDocument();
      expect(attempts).toBe(2);
    } finally {
      await act(async () => {
        release?.();
        await pending;
      });
    }
  });

  it('AC-001.3 ignores Select all from a previous filter and resets Back/Forward selection (BUG-082)', async () => {
    let release: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.get('*/api/v1/review-queue', () => queueResponse()),
      http.get('*/api/v1/review-queue/ticket-ids', async ({ request }) => {
        if (!new URL(request.url).searchParams.has('departmentId')) await pending;
        return HttpResponse.json({ ticketIds: [ticketId(1), ticketId(2)] });
      }),
    );
    const { user, router } = await openQueue();
    try {
      await user.click(screen.getByRole('checkbox', { name: 'Select all' }));
      await user.click(screen.getByRole('combobox', { name: 'Department' }));
      await user.click(await screen.findByTitle('IT'));
      await waitFor(() => expect(router.state.location.search).toContain(departmentId));
      await act(async () => {
        release?.();
        await pending;
      });
      expect(screen.getByText('0 selected')).toBeInTheDocument();
      await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
      expect(await screen.findByText('1 selected')).toBeInTheDocument();
      await act(() => router.navigate(-1));
      expect(await screen.findByText('0 selected')).toBeInTheDocument();
      await act(() => router.navigate(1));
      expect(await screen.findByText('0 selected')).toBeInTheDocument();
    } finally {
      release?.();
    }
  });

  it('AC-005.2 keeps 501 selected ids and disables every decision with accessible limit guidance', async () => {
    server.use(
      http.get('*/api/v1/review-queue', () => queueResponse([first])),
      http.get('*/api/v1/review-queue/ticket-ids', () =>
        HttpResponse.json({ ticketIds: Array.from({ length: 501 }, (_, n) => ticketId(n + 1)) }),
      ),
    );
    const { user } = await openQueue();
    await user.click(screen.getByRole('checkbox', { name: 'Select all' }));
    expect(await screen.findByRole('status')).toHaveTextContent('501 selected / 500 maximum');
    for (const name of ['Approve', 'Reject', 'Request Changes'])
      expect(screen.getByRole('button', { name })).toBeDisabled();
  });

  it('AC-001.6 retains a newly selected ticket while cached eligibility refreshes (BUG-082)', async () => {
    let release: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.get('*/api/v1/review-queue', () => queueResponse()),
      http.get('*/api/v1/review-queue/ticket-ids', async () => {
        await pending;
        return HttpResponse.json({ ticketIds: [ticketId(1), ticketId(2)] });
      }),
    );
    const { user, queryClient } = await openQueue();
    queryClient.setQueryData(getGetReviewQueueTicketIdsQueryKey(), { ticketIds: [ticketId(1)] });
    try {
      await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1102' }));
      expect(screen.getByText('1 selected')).toBeInTheDocument();
      expect(screen.getByRole('checkbox', { name: 'Select ITSUP-1102' })).toBeChecked();
      expect(screen.getByRole('button', { name: 'Approve' })).toBeDisabled();
      await act(async () => {
        release?.();
        await pending;
      });
      await waitFor(() => expect(screen.getByRole('button', { name: 'Approve' })).toBeEnabled());
      expect(screen.getByText('1 selected')).toBeInTheDocument();
    } finally {
      release?.();
    }
  });

  it('AC-005.5 prevents duplicate pending decisions and keeps selection after failure', async () => {
    let calls = 0;
    let release: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.get('*/api/v1/review-queue', () => queueResponse()),
      http.get('*/api/v1/review-queue/ticket-ids', () =>
        HttpResponse.json({ ticketIds: [ticketId(1), ticketId(2)] }),
      ),
      http.post('*/api/v1/reviews/approve', async () => {
        calls++;
        await pending;
        return problem(500);
      }),
    );
    const { user } = await openQueue();
    try {
      await user.click(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' }));
      const button = screen.getByRole('button', { name: 'Approve' });
      await waitFor(() => expect(button).toBeEnabled());
      await user.dblClick(button);
      await waitFor(() => expect(calls).toBe(1));
      expect(button).toBeDisabled();
      expect(screen.getByRole('checkbox', { name: 'Select ITSUP-1031' })).toBeDisabled();
      await act(async () => {
        release?.();
        await pending;
      });
      expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
      expect(screen.getByText('1 selected')).toBeInTheDocument();
    } finally {
      release?.();
    }
  });
});
