import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { getGetReviewTicketDetailQueryKey } from '@/shared/api';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
import { DETAIL_TICKET_IDS, ticketUuid } from '../mocks/data';
import { reviewStore } from '../mocks/state';

const firstId = ticketUuid(1);
const detailPath = (id = firstId) => `/tickets/${id}`;
const detailEndpoint = '*/api/v1/tickets/:ticketId';

describe('Ticket Detail review', () => {
  it('AC-006.3 AC-005.5 keeps pending actions mounted and disabled during refetch (BUG-089)', async () => {
    const user = userEvent.setup();
    const { queryClient } = renderApp(detailPath());
    const approve = await screen.findByRole('button', { name: 'Approve' });
    const reject = screen.getByRole('button', { name: 'Reject' });
    const changes = screen.getByRole('button', { name: 'Request Changes' });
    approve.focus();
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    const record = reviewStore.tickets.find((ticket) => ticket.ticketId === firstId);
    server.use(
      http.get(detailEndpoint, async () => {
        await pending;
        return HttpResponse.json(record);
      }),
    );
    let refresh: Promise<void> | undefined;
    act(() => {
      refresh = queryClient.invalidateQueries({
        queryKey: getGetReviewTicketDetailQueryKey(firstId),
      });
    });
    try {
      await waitFor(() => expect(approve).toBeDisabled());
      expect(screen.getByRole('button', { name: 'Approve' })).toBe(approve);
      expect(screen.getByRole('button', { name: 'Reject' })).toBe(reject);
      expect(screen.getByRole('button', { name: 'Request Changes' })).toBe(changes);
      expect(reject).toBeDisabled();
      expect(changes).toBeDisabled();
      await user.click(approve);
      expect(reviewStore.history).toHaveLength(0);
    } finally {
      finish?.();
      await act(async () => {
        await refresh;
      });
    }
    await waitFor(() => expect(approve).toBeEnabled());
    expect(screen.getByRole('button', { name: 'Approve' })).toBe(approve);
    expect(approve).toHaveFocus();
  });

  it('AC-006.3 hides every decision for a PM author who does not manage the Project', async () => {
    const record = reviewStore.tickets.find(
      (ticket) => ticket.ticketId === DETAIL_TICKET_IDS.unmanaged,
    );
    if (!record) throw new Error('Missing fixture');
    record.authorId = '00000000-0000-4000-8000-000000000003';
    renderApp(detailPath(record.ticketId), { role: 'PROJECT_MANAGER' });
    expect(await screen.findByRole('heading', { name: 'HRHELP-0318' })).toBeInTheDocument();
    expect(screen.getByText('Pending review')).toBeInTheDocument();
    for (const name of ['Approve', 'Reject', 'Request Changes']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
  });

  it('AC-006.1 AC-006.5 shows MSG04 when the permitted Ticket has no Evidence', async () => {
    const record = reviewStore.tickets.find((ticket) => ticket.ticketId === firstId);
    if (!record) throw new Error('Missing fixture');
    record.evidence = [];
    renderApp(detailPath());
    await screen.findByRole('heading', { name: 'ITSUP-1031' });
    expect(screen.getByText(msg('MSG04'))).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'resolution-check.txt' })).not.toBeInTheDocument();
  });

  it('AC-006.2 shows loading while the confirmed detail request is pending', async () => {
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    const record = reviewStore.tickets.find((ticket) => ticket.ticketId === firstId);
    server.use(
      http.get(detailEndpoint, async () => {
        await pending;
        return HttpResponse.json(record);
      }),
    );
    renderApp(detailPath());
    try {
      expect(await screen.findByText('Loading ...')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    } finally {
      finish?.();
    }
    expect(await screen.findByRole('heading', { name: 'ITSUP-1031' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to Review Queue' })).toHaveAttribute(
      'href',
      '/review-queue',
    );
  });
  it('AC-006.1 shows confirmed read-only Ticket, Solution and Evidence content', async () => {
    renderApp(detailPath());
    expect(await screen.findByRole('heading', { name: 'ITSUP-1031' })).toBeInTheDocument();
    expect(screen.getByText('The client configuration was out of date.')).toBeInTheDocument();
    expect(
      screen.getByText('Update the configuration, restart the client and verify access.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Pending review')).toBeInTheDocument();
    expect(screen.getByText('Solution Form')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it.each(['not-a-uuid', ticketUuid(999)])(
    'AC-006.2 shows MSG04 for invalid or missing id %s',
    async (id) => {
      let reads = 0;
      server.use(
        http.get(detailEndpoint, () => {
          reads++;
          return problem(404, 'MSG04');
        }),
      );
      renderApp(detailPath(id));
      expect(await screen.findByText(msg('MSG04'))).toBeInTheDocument();
      expect(reads).toBe(id === 'not-a-uuid' ? 0 : 1);
    },
  );

  it('AC-006.2 shows a catalog load error and retries the query', async () => {
    const user = userEvent.setup();
    server.use(http.get(detailEndpoint, () => problem(503, 'MSG06')));
    renderApp(detailPath());
    expect(await screen.findByRole('button', { name: 'Try again' })).toBeInTheDocument();
    server.resetHandlers();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('heading', { name: 'ITSUP-1031' })).toBeInTheDocument();
  });

  it('AC-006.3 AC-002.3 AC-005.7 approves a managing PM own ticket immediately and preserves content', async () => {
    const record = reviewStore.tickets.find((ticket) => ticket.ticketId === firstId);
    if (!record) throw new Error('Missing fixture');
    record.authorId = '00000000-0000-4000-8000-000000000003';
    const before = structuredClone(record);
    const user = userEvent.setup();
    renderApp(detailPath());
    await user.click(await screen.findByRole('button', { name: 'Approve' }));
    expect(await screen.findByText(msg('MSG14', { count: 1 }))).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument(),
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(reviewStore.history).toHaveLength(1);
    expect(record.solution).toEqual(before.solution);
    expect(record.evidence).toEqual(before.evidence);
    expect(record.reviewDueDate).toBe(before.reviewDueDate);
    expect(record.jiraStatus).toBe(before.jiraStatus);
    expect(reviewStore.history[0]).toMatchObject({
      ticketId: firstId,
      actorId: record.authorId,
      action: 'APPROVE',
      beforeStatus: 'PENDING_REVIEW',
      afterStatus: 'PUBLISHED',
    });
    expect(Number.isNaN(Date.parse(reviewStore.history[0]?.timestamp ?? ''))).toBe(false);
  });

  it.each([DETAIL_TICKET_IDS.published, DETAIL_TICKET_IDS.rejected])(
    'AC-006.3 hides decisions for nonpending %s',
    async (id) => {
      renderApp(detailPath(id));
      await screen.findByRole('heading', { name: /ITSUP-/ });
      expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument();
    },
  );

  it('AC-006.4 AC-004.4 explains the imported Published restriction accessibly', async () => {
    renderApp(detailPath(DETAIL_TICKET_IDS.imported));
    expect(await screen.findByRole('note')).toHaveTextContent(msg('MSG18'));
    expect(screen.queryByRole('button', { name: 'Request Changes' })).not.toBeInTheDocument();
  });

  it('AC-006.5 renders filenames as text, readable sizes, and only safe references', async () => {
    const record = reviewStore.tickets.find((ticket) => ticket.ticketId === firstId);
    if (!record) throw new Error('Missing fixture');
    record.evidence = [
      '/api/evidence/a',
      'https://example.com/a',
      'javascript:alert(1)',
      '//evil.test/a',
      'data:text/html,test',
    ].map((url, i) => ({
      id: ticketUuid(800 + i),
      filename: `<script>file${i}</script>`,
      sizeBytes: i === 0 ? 1048576 : 128,
      url,
    }));
    renderApp(detailPath());
    expect(await screen.findByRole('link', { name: '<script>file0</script>' })).toHaveAttribute(
      'href',
      '/api/evidence/a',
    );
    expect(screen.getByText('(1 MB)')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '<script>file1</script>' })).toHaveAttribute(
      'rel',
      'noopener noreferrer',
    );
    for (const i of [2, 3, 4]) {
      expect(screen.getByText(`<script>file${i}</script>`)).toBeInTheDocument();
      expect(
        screen.queryByRole('link', { name: `<script>file${i}</script>` }),
      ).not.toBeInTheDocument();
    }
  });

  it('AC-006.6 conceals another author unpublished content and queue navigation from readers', async () => {
    renderApp(detailPath(), { role: 'TICKET_RESOLVER' });
    expect(await screen.findByText(msg('MSG04'))).toBeInTheDocument();
    expect(screen.queryByText('The client configuration was out of date.')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Review Queue/ })).not.toBeInTheDocument();
  });

  it('AC-006.6 lets the author read their own unpublished Solution without decisions', async () => {
    renderApp(detailPath(DETAIL_TICKET_IDS.authored), { role: 'TICKET_RESOLVER' });
    expect(await screen.findByText('Please clarify the validation steps.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Review Queue/ })).not.toBeInTheDocument();
  });

  it('AC-003.1 AC-003.2 AC-003.3 validates, cancels and submits the shared Reject popup', async () => {
    const user = userEvent.setup();
    renderApp(detailPath());
    await user.click(await screen.findByRole('button', { name: 'Reject' }));
    let dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByText(msg('MSG15', { count: 1 }))).toBeInTheDocument();
    await user.click(dialog.getByRole('button', { name: 'Reject' }));
    expect(await dialog.findByText(msg('MSG01', { field_name: 'Reason' }))).toBeInTheDocument();
    await user.click(dialog.getByRole('button', { name: 'Cancel' }));
    expect(reviewStore.history).toHaveLength(0);
    await user.click(screen.getByRole('button', { name: 'Reject' }));
    dialog = within(await screen.findByRole('dialog'));
    await user.type(dialog.getByRole('textbox', { name: 'Reason' }), 'Needs verification');
    await user.click(dialog.getByRole('button', { name: 'Reject' }));
    expect(await screen.findByText(msg('MSG16', { count: 1 }))).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(reviewStore.history[0]?.reason).toBe('Needs verification');
  });

  it('AC-004.1 AC-004.2 AC-004.3 AC-005.5 retains comment after failed mutation then sends successfully', async () => {
    const user = userEvent.setup();
    server.use(http.post('*/api/v1/reviews/request-changes', () => problem(409, 'MSG06')));
    renderApp(detailPath());
    await user.click(await screen.findByRole('button', { name: 'Request Changes' }));
    const dialog = within(await screen.findByRole('dialog'));
    await user.click(dialog.getByRole('button', { name: 'Send' }));
    expect(await dialog.findByText(msg('MSG01', { field_name: 'Comment' }))).toBeInTheDocument();
    await user.type(dialog.getByRole('textbox', { name: 'Comment' }), 'Clarify steps');
    await user.click(dialog.getByRole('button', { name: 'Send' }));
    await screen.findByText(msg('MSG06'));
    expect(dialog.getByRole('textbox', { name: 'Comment' })).toHaveValue('Clarify steps');
    server.resetHandlers();
    await waitFor(() => expect(dialog.getByRole('button', { name: 'Send' })).toBeEnabled());
    await user.click(dialog.getByRole('button', { name: 'Send' }));
    expect(await screen.findByText(msg('MSG17'))).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(reviewStore.history[0]?.comment).toBe('Clarify steps');
  });

  it('AC-005.4 AC-005.6 keeps confirmed mutation success separate from a failed detail refresh', async () => {
    const user = userEvent.setup();
    renderApp(detailPath());
    await screen.findByRole('button', { name: 'Reject' });
    server.use(http.get(detailEndpoint, () => problem(503, 'MSG06')));
    await user.click(screen.getByRole('button', { name: 'Reject' }));
    const dialog = within(await screen.findByRole('dialog'));
    await user.type(dialog.getByRole('textbox', { name: 'Reason' }), 'Incomplete');
    await user.click(dialog.getByRole('button', { name: 'Reject' }));
    expect(await screen.findByText(msg('MSG16', { count: 1 }))).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Try again' })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(reviewStore.history).toHaveLength(1);
  });

  it.each([404, 409])(
    'AC-005.4 refreshes stale actions after a %s decision response and retains input',
    async (status) => {
      const user = userEvent.setup();
      renderApp(detailPath());
      await user.click(await screen.findByRole('button', { name: 'Request Changes' }));
      const dialog = within(await screen.findByRole('dialog'));
      await user.type(dialog.getByRole('textbox', { name: 'Comment' }), 'Preserve this');
      server.use(
        http.post('*/api/v1/reviews/request-changes', () => {
          const ticket = reviewStore.tickets.find((item) => item.ticketId === firstId);
          if (!ticket) throw new Error('Missing fixture');
          ticket.status = 'PUBLISHED';
          return problem(status, status === 404 ? 'MSG04' : 'MSG06');
        }),
      );
      await user.click(dialog.getByRole('button', { name: 'Send' }));
      await waitFor(() => expect(dialog.getByRole('button', { name: 'Send' })).toBeDisabled());
      expect(dialog.getByRole('textbox', { name: 'Comment' })).toHaveValue('Preserve this');
      await waitFor(() =>
        expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument(),
      );
      expect(reviewStore.history).toHaveLength(0);
    },
  );

  it('AC-005.5 prevents duplicate Approve sends while the first request is pending', async () => {
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    let calls = 0;
    server.use(
      http.post('*/api/v1/reviews/approve', async () => {
        calls++;
        await pending;
        return HttpResponse.json({ count: 1 });
      }),
    );
    const user = userEvent.setup();
    renderApp(detailPath());
    const approve = await screen.findByRole('button', { name: 'Approve' });
    try {
      await user.dblClick(approve);
      await waitFor(() => expect(calls).toBe(1));
      expect(approve).toBeDisabled();
      expect(approve).toHaveAttribute('aria-busy', 'true');
      expect(screen.getByRole('button', { name: 'Reject' })).toBeDisabled();
    } finally {
      finish?.();
    }
    await screen.findByText(msg('MSG14', { count: 1 }));
  });

  it('AC-005.4 AC-005.5 preserves failed popup input through a failed refresh and retry', async () => {
    const user = userEvent.setup();
    renderApp(detailPath());
    await user.click(await screen.findByRole('button', { name: 'Request Changes' }));
    const dialog = within(await screen.findByRole('dialog'));
    await user.type(dialog.getByRole('textbox', { name: 'Comment' }), 'Keep through outage');
    server.use(
      http.post('*/api/v1/reviews/request-changes', () => problem(409, 'MSG06')),
      http.get(detailEndpoint, () => problem(503, 'MSG06')),
    );
    await user.click(dialog.getByRole('button', { name: 'Send' }));
    await screen.findByRole('button', { name: 'Try again' });
    expect(dialog.getByRole('textbox', { name: 'Comment' })).toHaveValue('Keep through outage');
    expect(dialog.getByRole('button', { name: 'Send' })).toBeDisabled();
    server.resetHandlers();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    await waitFor(() => expect(dialog.getByRole('button', { name: 'Send' })).toBeEnabled());
    expect(dialog.getByRole('textbox', { name: 'Comment' })).toHaveValue('Keep through outage');
  });
});
