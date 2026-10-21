import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { ReviewTicketResponse } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

let ticket: ReviewTicketResponse;
let submitted: unknown;
beforeEach(() => {
  ticket = {
    ticketId: 1,
    sourceTicketId: 'ITSUP-1',
    title: 'VPN repair',
    project: { projectId: 101, name: 'IT Support' },
    source: 'SOLUTION_FORM',
    writerId: 'writer',
    authorName: 'Writer',
    status: 'PENDING_REVIEW',
    enteredQueueAt: '2026-10-01T00:00:00Z',
    reviewDueDate: '2026-10-04T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    solution: { symptoms: 'VPN fails', resolutionSteps: 'Renew certificate' },
    evidence: [{ evidenceId: 'e1', name: 'error.png', mediaType: 'image/png', sizeBytes: 128 }],
  };
  submitted = undefined;
  server.use(
    http.get('*/api/v1/tickets/1', () => HttpResponse.json(ticket)),
    http.post('*/api/v1/reviews/approve', async ({ request }) => {
      submitted = await request.json();
      ticket = { ...ticket, status: 'PUBLISHED' };
      return HttpResponse.json({ count: 1 });
    }),
    http.post('*/api/v1/reviews/reject', async ({ request }) => {
      submitted = await request.json();
      ticket = { ...ticket, status: 'REJECTED' };
      return HttpResponse.json({ count: 1 });
    }),
    http.post('*/api/v1/reviews/request-changes', async ({ request }) => {
      submitted = await request.json();
      ticket = { ...ticket, status: 'CHANGES_REQUESTED', reviewComment: 'Add error details' };
      return HttpResponse.json({ count: 1 });
    }),
  );
});

async function openDetail() {
  const user = userEvent.setup();
  const view = renderApp('/tickets/1', { role: 'PROJECT_MANAGER' });
  await screen.findByRole('heading', { name: 'VPN repair' }, { timeout: 5000 });
  return { user, ...view };
}

describe('Ticket Detail review', () => {
  it('shows Solution and Evidence read-only and approves one ticket without confirmation (AC-002.3, AC-004.1)', async () => {
    const { user } = await openDetail();
    expect(screen.getByText('Renew certificate')).toBeInTheDocument();
    expect(screen.getByText(/error.png/)).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Approve' }));
    expect(await screen.findByText('Published')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(submitted).toEqual({ ticketIds: [1] });
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
  });

  it.each([
    ['Reject', 'Reason', 'Incorrect steps', 'Reject', 'Rejected'],
    ['Request Changes', 'Comment', 'Add error details', 'Send', 'Changes requested'],
  ])(
    'submits %s, validates required input and refreshes detail (BUG-001, AC-003.3, AC-004.2)',
    async (action, field, comment, confirm, status) => {
      const { user } = await openDetail();
      const due = ticket.reviewDueDate;
      await user.click(screen.getByRole('button', { name: action }));
      const dialog = await screen.findByRole('dialog');
      await user.click(within(dialog).getByRole('button', { name: confirm }));
      expect(
        await within(dialog).findByText(msg('MSG01', { field_name: field })),
      ).toBeInTheDocument();
      expect(submitted).toBeUndefined();
      await user.type(within(dialog).getByLabelText(field), comment);
      await user.click(within(dialog).getByRole('button', { name: confirm }));
      expect(await screen.findByText(status, { exact: true })).toBeInTheDocument();
      expect(submitted).toEqual(
        action === 'Reject' ? { ticketIds: [1], reason: comment } : { ticketId: 1, comment },
      );
      expect(ticket.reviewDueDate).toBe(due);
      if (action === 'Request Changes') expect(screen.getByText(comment)).toBeInTheDocument();
    },
  );

  it('disables empty Solution approval and explains source restriction (AC-002.4, AC-004.4)', async () => {
    ticket = { ...ticket, solution: {}, source: 'MANUAL' };
    const { user } = await openDetail();
    expect(screen.getByRole('button', { name: 'Approve' })).toBeDisabled();
    expect(screen.getByText(msg('MSG20'))).toBeInTheDocument();
    const changes = screen.getByRole('button', { name: 'Request Changes' });
    expect(changes).toBeDisabled();
    await user.hover(changes.parentElement ?? changes);
    expect(await screen.findByRole('tooltip')).toHaveTextContent(msg('MSG18'));
  });

  it('hides decisions for a different Project manager (AC-006.1)', async () => {
    ticket = { ...ticket, project: { projectId: 999, name: 'Other Project' } };
    await openDetail();
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument();
  });

  it('preserves the popup and comment after a failed save (AC-004.3, AC-005.1)', async () => {
    server.use(http.post('*/api/v1/reviews/request-changes', () => problem(500)));
    const { user } = await openDetail();
    await user.click(screen.getByRole('button', { name: 'Request Changes' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Comment'), 'Keep this comment');
    await user.click(within(dialog).getByRole('button', { name: 'Send' }));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Comment')).toHaveValue('Keep this comment');
    expect(ticket.status).toBe('PENDING_REVIEW');
  });

  it('blocks duplicate decisions while a mutation is pending (AC-006.5)', async () => {
    let release = () => {};
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.post('*/api/v1/reviews/approve', async () => {
        await gate;
        return HttpResponse.json({ count: 1 });
      }),
    );
    const { user } = await openDetail();
    try {
      await user.click(screen.getByRole('button', { name: 'Approve' }));
      expect(screen.getByRole('button', { name: 'Reject' })).toBeDisabled();
      expect(screen.getByRole('button', { name: 'Request Changes' })).toBeDisabled();
    } finally {
      release();
    }
    await waitFor(() => expect(screen.getByRole('button', { name: 'Reject' })).toBeEnabled());
  });

  it('shows loading then an access error with retry (AC-006.5)', async () => {
    server.use(
      http.get('*/api/v1/tickets/1', async () => {
        await delay(100);
        return problem(403);
      }),
    );
    renderApp('/tickets/1');
    expect(await screen.findByText('Loading ...')).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent(msg('MSG08'));
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});
