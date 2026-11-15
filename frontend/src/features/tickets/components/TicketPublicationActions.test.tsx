import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { TicketStatus } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import type { Role } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

const ticketId = '00000000-0000-4000-8000-000000000136';
/** ITSUP: a Project the fixture Project Manager manages (src/mocks/session.ts). */
const MANAGED_PROJECT = '00000000-0000-4000-8000-000000000101';
const OTHER_PROJECT = '00000000-0000-4000-8000-000000000201';
const TIMEOUT = { timeout: 5000 };

/** The detail answers `status` first and, after a request changed it, `next` (the stored status). */
function showTicket(
  status: TicketStatus,
  options: { next?: TicketStatus; projectId?: string; role?: Role } = {},
) {
  let current = status;
  const sent: { url: string; body: unknown }[] = [];
  server.use(
    http.get('*/api/v1/tickets/:ticketId', () =>
      HttpResponse.json({
        ticketId,
        sourceTicketId: 'PUB-136',
        title: 'Login restored',
        description: null,
        project: { projectId: options.projectId ?? MANAGED_PROJECT, name: 'ITSUP' },
        department: null,
        source: 'SOLUTION_FORM',
        status: current,
        authorId: null,
        authorName: 'Ticket writer',
        resolvedDate: null,
        sourceLink: null,
        reviewDueDate: null,
        solution: {
          symptoms: 'Login failed',
          rootCause: 'Token',
          resolutionSteps: 'Refresh token',
        },
        evidence: [],
        lastReviewComment: null,
        canDecide: false,
      }),
    ),
    http.post('*/api/v1/reviews/:action', async ({ request }) => {
      sent.push({ url: new URL(request.url).pathname, body: await request.json() });
      current = options.next ?? current;
      return HttpResponse.json({ count: 1 });
    }),
  );
  renderApp(`/tickets/${ticketId}`, { role: options.role ?? 'PROJECT_MANAGER' });
  return sent;
}

async function confirm(user: ReturnType<typeof userEvent.setup>, button: string, question: string) {
  await user.click(await screen.findByRole('button', { name: button }, TIMEOUT));
  const dialog = await screen.findByRole('dialog');
  expect(within(dialog).getByText(question)).toBeInTheDocument();
  return dialog;
}

describe('TicketPublicationActions', () => {
  it('AC-009.8 unpublishes a Published ticket after MSG21 and shows MSG22', async () => {
    const user = userEvent.setup();
    const sent = showTicket('PUBLISHED', { next: 'UNPUBLISHED' });
    const dialog = await confirm(user, 'Unpublish', msg('MSG21'));
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));

    expect(await screen.findByText(msg('MSG22'))).toBeInTheDocument();
    expect(sent).toEqual([{ url: '/api/v1/reviews/unpublish', body: { ticketIds: [ticketId] } }]);
    expect(await screen.findByRole('button', { name: 'Re-publish' }, TIMEOUT)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Unpublish' })).not.toBeInTheDocument();
  });

  it('AC-009.8 sends nothing when Unpublish is cancelled', async () => {
    const user = userEvent.setup();
    const sent = showTicket('PUBLISHED');
    const dialog = await confirm(user, 'Unpublish', msg('MSG21'));
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    // antd keeps the closing dialog in the DOM under jsdom (no motion end), as in ReviewQueuePage.test.
    expect(sent).toEqual([]);
    expect(screen.getByRole('button', { name: 'Unpublish' })).toBeInTheDocument();
  });

  it('AC-009.9 re-publishes an Unpublished ticket after MSG23 and shows MSG24', async () => {
    const user = userEvent.setup();
    const sent = showTicket('UNPUBLISHED', { next: 'PUBLISHED' });
    const dialog = await confirm(user, 'Re-publish', msg('MSG23'));
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));

    expect(await screen.findByText(msg('MSG24'))).toBeInTheDocument();
    expect(sent).toEqual([{ url: '/api/v1/reviews/republish', body: { ticketIds: [ticketId] } }]);
    expect(await screen.findByRole('button', { name: 'Unpublish' }, TIMEOUT)).toBeInTheDocument();
  });

  it('AC-009.9 sends nothing when Re-publish is cancelled', async () => {
    const user = userEvent.setup();
    const sent = showTicket('UNPUBLISHED');
    const dialog = await confirm(user, 'Re-publish', msg('MSG23'));
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    // antd keeps the closing dialog in the DOM under jsdom (no motion end), as in ReviewQueuePage.test.
    expect(sent).toEqual([]);
  });

  it.each<[string, Parameters<typeof showTicket>]>([
    ['a Ticket Resolver', ['PUBLISHED', { role: 'TICKET_RESOLVER' }]],
    ['the Project Manager of another Project', ['PUBLISHED', { projectId: OTHER_PROJECT }]],
    ['a Pending review ticket', ['PENDING_REVIEW', {}]],
    ['a Rejected ticket', ['REJECTED', {}]],
  ])('AC-009.8 AC-009.9 shows no button for %s', async (_case, args) => {
    showTicket(...args);
    await screen.findByText('Refresh token', {}, TIMEOUT);
    expect(screen.queryByRole('button', { name: 'Unpublish' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Re-publish' })).not.toBeInTheDocument();
  });

  it('AC-009.10 shows the error and reloads the detail when the request is rejected', async () => {
    const user = userEvent.setup();
    showTicket('PUBLISHED');
    await screen.findByRole('button', { name: 'Unpublish' }, TIMEOUT);
    // Someone else unpublished it meanwhile: the request answers 409 without a code, the detail Unpublished.
    let status: TicketStatus = 'PUBLISHED';
    server.use(
      http.post('*/api/v1/reviews/unpublish', () => {
        status = 'UNPUBLISHED';
        return problem(409);
      }),
      http.get('*/api/v1/tickets/:ticketId', () =>
        HttpResponse.json({
          ticketId,
          sourceTicketId: 'PUB-136',
          title: 'Login restored',
          project: { projectId: MANAGED_PROJECT, name: 'ITSUP' },
          source: 'SOLUTION_FORM',
          status,
          authorName: 'Ticket writer',
          solution: {
            symptoms: 'Login failed',
            rootCause: 'Token',
            resolutionSteps: 'Refresh token',
          },
          evidence: [],
          canDecide: false,
        }),
      ),
    );
    const dialog = await confirm(user, 'Unpublish', msg('MSG21'));
    await user.click(within(dialog).getByRole('button', { name: 'OK' }));

    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Re-publish' }, TIMEOUT)).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG22'))).not.toBeInTheDocument();
  });
});
