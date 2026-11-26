import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
import { DETAIL_TICKET_IDS, ticketUuid } from '../mocks/data';
import { reviewStore } from '../mocks/state';

const firstId = ticketUuid(1);
const detailPath = (id = firstId) => `/tickets/${id}`;
const aiReviewEndpoint = '*/api/v1/tickets/:ticketId/ai-review';

describe('AI check on Ticket Detail', () => {
  it('AC-015.1 offers AI check to the managing Project Manager of a Pending review ticket', async () => {
    renderApp(detailPath());
    await screen.findByText('Pending review', {}, { timeout: 5000 });
    expect(screen.getByRole('button', { name: 'AI check' })).toBeEnabled();
  });

  it.each([DETAIL_TICKET_IDS.published, DETAIL_TICKET_IDS.rejected])(
    'AC-015.1 does not offer AI check on %s, not Pending review',
    async (id) => {
      renderApp(detailPath(id));
      await screen.findByRole('heading', { name: /ITSUP-/ }, { timeout: 5000 });
      expect(screen.queryByRole('button', { name: 'AI check' })).not.toBeInTheDocument();
    },
  );

  it('AC-015.1 does not offer AI check to a Project Manager who does not manage the Project', async () => {
    const record = reviewStore.tickets.find(
      (ticket) => ticket.ticketId === DETAIL_TICKET_IDS.unmanaged,
    );
    if (!record) throw new Error('Missing fixture');
    // authored by the Project Manager, so Ticket Detail is readable without managing the Project
    record.authorId = '00000000-0000-4000-8000-000000000003';
    renderApp(detailPath(record.ticketId));
    expect(await screen.findByRole('heading', { name: 'HRHELP-0318' })).toBeInTheDocument();
    expect(screen.getByText('Pending review')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'AI check' })).not.toBeInTheDocument();
  });

  it('AC-015.1 AC-015.8 does not offer AI check or findings to the writer', async () => {
    renderApp(detailPath(DETAIL_TICKET_IDS.authored), { role: 'TICKET_RESOLVER' });
    await screen.findByText('Please clarify the validation steps.');
    expect(screen.queryByRole('button', { name: 'AI check' })).not.toBeInTheDocument();
  });

  it('AC-015.5 shows the findings grouped by severity with the field name, as plain text', async () => {
    server.use(
      http.post(aiReviewEndpoint, () =>
        HttpResponse.json({
          findings: [
            {
              field: 'ROOT_CAUSE',
              severity: 'HIGH',
              issue: '<b>Not the cause</b>',
              suggestion: 'Explain the lease.',
            },
            { field: 'SCOPE', severity: 'HIGH', issue: 'Too wide', suggestion: 'Name the office.' },
            { field: 'GENERAL', severity: 'LOW', issue: 'Short', suggestion: 'Add detail.' },
          ],
        }),
      ),
    );
    const user = userEvent.setup();
    renderApp(detailPath());
    await user.click(await screen.findByRole('button', { name: 'AI check' }, { timeout: 5000 }));

    const high = await screen.findByRole('list', { name: 'High' });
    const items = within(high).getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('Root cause');
    expect(items[0]).toHaveTextContent('<b>Not the cause</b>');
    expect(items[0]).toHaveTextContent('Explain the lease.');
    expect(items[0]?.querySelector('b')).toBeNull();
    expect(items[1]).toHaveTextContent('Scope');
    expect(
      within(screen.getByRole('list', { name: 'Low' })).getByRole('listitem'),
    ).toHaveTextContent('General');
    expect(screen.queryByRole('list', { name: 'Medium' })).not.toBeInTheDocument();
  });

  it('AC-015.5 shows MSG_NEW_AI_REVIEW_2 when the AI finds no issue', async () => {
    server.use(http.post(aiReviewEndpoint, () => HttpResponse.json({ findings: [] })));
    const user = userEvent.setup();
    renderApp(detailPath());
    await user.click(await screen.findByRole('button', { name: 'AI check' }, { timeout: 5000 }));
    expect(await screen.findByText(msg('MSG_NEW_AI_REVIEW_2'))).toBeInTheDocument();
  });

  it('AC-015.5 shows a loading state and disables AI check while the request is pending', async () => {
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    server.use(
      http.post(aiReviewEndpoint, async () => {
        await pending;
        return HttpResponse.json({ findings: [] });
      }),
    );
    const user = userEvent.setup();
    renderApp(detailPath());
    const button = await screen.findByRole('button', { name: 'AI check' }, { timeout: 5000 });
    await user.click(button);
    try {
      await waitFor(() => expect(button).toBeDisabled());
      expect(button).toHaveAttribute('aria-busy', 'true');
      expect(screen.getByRole('button', { name: 'Approve' })).toBeEnabled();
    } finally {
      finish?.();
    }
    expect(await screen.findByText(msg('MSG_NEW_AI_REVIEW_2'))).toBeInTheDocument();
    expect(button).toBeEnabled();
  });

  it('AC-015.6 shows MSG_NEW_AI_REVIEW_1 with retry and keeps the decision controls working', async () => {
    server.use(http.post(aiReviewEndpoint, () => problem(503, 'MSG_NEW_AI_REVIEW_1')));
    const user = userEvent.setup();
    renderApp(detailPath());
    await user.click(await screen.findByRole('button', { name: 'AI check' }, { timeout: 5000 }));
    expect(await screen.findByText(msg('MSG_NEW_AI_REVIEW_1'))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reject' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Request Changes' })).toBeEnabled();

    server.resetHandlers();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('list', { name: 'High' })).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG_NEW_AI_REVIEW_1'))).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Approve' }));
    expect(await screen.findByText(msg('MSG14', { count: 1 }))).toBeInTheDocument();
    expect(reviewStore.history).toHaveLength(1);
  });
});
