import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import {
  getGetReviewTicketDetailQueryKey,
  type ReviewTicketDetail,
  type TicketStatus,
} from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { SESSION_QUERY_KEY, type Session } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { renderApp, renderWithProviders } from '@/test/render';
import { GiveFeedbackButton } from '../index';

// A Published ticket that the existing Feedback PUT handler accepts.
const TICKET_ID = '5ea70000-0000-4000-8000-000000000014';
const TICKET_PATH = `/api/v1/tickets/${TICKET_ID}`;
const TICKET_TITLE = 'VPN connection restored';
const BUTTON = { name: 'Give feedback' };
const DIALOG = { name: 'Give Feedback' };
const TIMEOUT = { timeout: 5000 };

function showTicket(status: TicketStatus = 'PUBLISHED') {
  const readTicket = vi.fn(() =>
    HttpResponse.json<ReviewTicketDetail>({
      ticketId: TICKET_ID,
      sourceTicketId: 'IT-014',
      title: TICKET_TITLE,
      description: 'The VPN client could not connect.',
      project: { projectId: '00000000-0000-4000-8000-000000000101', name: 'ITSUP' },
      source: 'SOLUTION_FORM',
      status,
      authorId: null,
      authorName: 'Ticket writer',
      reviewDueDate: null,
      solution: {
        symptoms: 'VPN connection timed out',
        rootCause: 'Expired certificate',
        resolutionSteps: 'Renew the client certificate',
        workaround: null,
        scope: null,
      },
      evidence: [],
      lastReviewComment: null,
      canDecide: false,
    }),
  );
  server.use(http.get(`*${TICKET_PATH}`, readTicket));
  return readTicket;
}

describe('GiveFeedbackButton', () => {
  it.each([
    ['Ticket Resolver', 'TICKET_RESOLVER'],
    ['Project Manager', 'PROJECT_MANAGER'],
  ] as const)(
    'AC-003.12 shows Give feedback on a Published ticket for a %s (BUG-361)',
    async (_label, role) => {
      showTicket();
      renderApp(paths.ticketDetail(TICKET_ID), { role });
      expect(await screen.findByRole('button', BUTTON, TIMEOUT)).toBeInTheDocument();
    },
  );

  it.each(['PENDING_REVIEW', 'UNPUBLISHED'] as const)(
    'AC-003.12 shows no Give feedback for a ticket that is not Published (%s) (BUG-361)',
    async (status) => {
      showTicket(status);
      renderApp(paths.ticketDetail(TICKET_ID), { role: 'TICKET_RESOLVER' });
      expect(await screen.findByText(TICKET_TITLE, {}, TIMEOUT)).toBeInTheDocument();
      expect(screen.queryByRole('button', BUTTON)).not.toBeInTheDocument();
    },
  );

  it('AC-003.12 shows no Give feedback for a user without TICKET_RESOLVER or PROJECT_MANAGER (BUG-361)', async () => {
    showTicket();
    const { queryClient } = renderWithProviders(<GiveFeedbackButton ticketId={TICKET_ID} />, {
      role: 'EMPLOYEE',
    });

    await waitFor(() => {
      expect(queryClient.getQueryData<Session>(SESSION_QUERY_KEY)?.roles).toEqual(['EMPLOYEE']);
      expect(
        queryClient.getQueryData<ReviewTicketDetail>(getGetReviewTicketDetailQueryKey(TICKET_ID))
          ?.status,
      ).toBe('PUBLISHED');
    });
    expect(screen.queryByRole('button', BUTTON)).not.toBeInTheDocument();
  });

  it('AC-003.12 shows no Give feedback while the ticket loads or when it fails to load (BUG-361)', async () => {
    let finish: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      finish = resolve;
    });
    server.use(
      http.get(`*${TICKET_PATH}`, async () => {
        await pending;
        return problem(404, 'MSG04');
      }),
    );
    renderApp(paths.ticketDetail(TICKET_ID), { role: 'TICKET_RESOLVER' });

    try {
      expect(await screen.findByText('Loading ...', {}, TIMEOUT)).toBeInTheDocument();
      expect(screen.queryByRole('button', BUTTON)).not.toBeInTheDocument();
    } finally {
      finish?.();
    }
    expect(await screen.findByText(msg('MSG04'))).toBeInTheDocument();
    expect(screen.queryByRole('button', BUTTON)).not.toBeInTheDocument();
  });

  it('AC-003.13 opens 8.1 Give Feedback for the ticket and sends the Feedback with its id (BUG-361)', async () => {
    const readTicket = showTicket();
    const requests: Request[] = [];
    const recordFeedback = ({ request }: { request: Request }) => {
      if (request.method === 'PUT' && new URL(request.url).pathname.endsWith('/feedback')) {
        requests.push(request.clone());
      }
    };
    server.events.on('request:start', recordFeedback);

    try {
      const user = userEvent.setup();
      renderApp(paths.ticketDetail(TICKET_ID), { role: 'TICKET_RESOLVER' });
      await user.click(await screen.findByRole('button', BUTTON, TIMEOUT));
      const dialog = await screen.findByRole('dialog', DIALOG);
      await user.click(within(dialog).getByRole('button', { name: 'Helpful' }));
      await user.click(within(dialog).getByRole('button', { name: 'Send' }));

      expect(await screen.findByText(msg('MSG26'))).toBeInTheDocument();
      expect(requests.map(({ url }) => new URL(url).pathname)).toEqual([`${TICKET_PATH}/feedback`]);
      expect(await requests[0]?.json()).toEqual({ isHelpful: true, comment: null });
      expect(readTicket).toHaveBeenCalledTimes(1);

      // jsdom needs the animation end event to finish closing the antd dialog.
      await waitFor(() => expect(dialog).toHaveClass('ant-zoom-leave-active'));
      fireEvent(dialog, new Event('webkitAnimationEnd'));
      await waitFor(() => expect(screen.queryByRole('dialog', DIALOG)).not.toBeInTheDocument());
      expect(screen.getByRole('button', BUTTON)).toBeInTheDocument();
    } finally {
      server.events.removeListener('request:start', recordFeedback);
    }
  });
});
