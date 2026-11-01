import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { delay, http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import type { TicketFeedbackRequest } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderWithProviders } from '@/test/render';
import { GiveFeedbackModal } from '../index';

/** The first Published ticket of the mocks (features/search/mocks/search-data.ts). */
const TICKET_ID = '5ea70000-0000-4000-8000-000000000001';
const FEEDBACK_URL = '*/api/v1/tickets/:ticketId/feedback';

/** Records every Feedback request, then lets the feature's own mock answer it. */
function spyOnFeedback() {
  const requests: { ticketId: string; body: TicketFeedbackRequest }[] = [];
  server.events.on('request:start', async ({ request }) => {
    const match = /\/api\/v1\/tickets\/([^/]+)\/feedback$/.exec(new URL(request.url).pathname);
    if (match?.[1] && request.method === 'PUT') {
      requests.push({
        ticketId: match[1],
        body: (await request.clone().json()) as TicketFeedbackRequest,
      });
    }
  });
  return requests;
}

/** Stands in for 5.5 Ticket Detail: a button opens the popup, which closes itself through onClose. */
function Host({ ticketId = TICKET_ID }: { ticketId?: string }) {
  const [isOpen, setIsOpen] = useState(true);
  return (
    <>
      <button type="button" onClick={() => setIsOpen(true)}>
        Open
      </button>
      <GiveFeedbackModal ticketId={ticketId} isOpen={isOpen} onClose={() => setIsOpen(false)} />
    </>
  );
}

function renderPopup(ticketId?: string) {
  const user = userEvent.setup();
  renderWithProviders(<Host ticketId={ticketId} />, { role: 'TICKET_RESOLVER' });
  return user;
}

async function findDialog() {
  return screen.findByRole('dialog', { name: 'Give Feedback' });
}

const helpful = (dialog: HTMLElement) => within(dialog).getByRole('button', { name: /^Helpful$/ });
const notHelpful = (dialog: HTMLElement) =>
  within(dialog).getByRole('button', { name: /^Not helpful$/ });
const comment = (dialog: HTMLElement) => within(dialog).getByRole('textbox', { name: 'Comment' });
const send = (dialog: HTMLElement) => within(dialog).getByRole('button', { name: /Send/ });

/**
 * The popup is closed: its leave animation ran and it is hidden. jsdom never ends a CSS animation, so the test
 * fires the end event as the browser would; jsdom has no AnimationEvent, so antd listens to the webkit name.
 */
async function expectClosed() {
  const dialog = screen.getByRole('dialog', { name: 'Give Feedback' });
  await waitFor(() => expect(dialog).toHaveClass('ant-zoom-leave-active'));
  fireEvent(dialog, new Event('webkitAnimationEnd'));
  await waitFor(() =>
    expect(screen.queryByRole('dialog', { name: 'Give Feedback' })).not.toBeInTheDocument(),
  );
}

afterEach(() => {
  server.events.removeAllListeners();
});

describe('8.1 Give Feedback popup', () => {
  it('AC-003.1 shows Helpful? with Helpful and Not helpful unselected, an empty Comment, Cancel and Send', async () => {
    renderPopup();
    const dialog = await findDialog();
    expect(within(dialog).getByText('Helpful?')).toBeInTheDocument();
    expect(within(dialog).getByRole('group', { name: 'Helpful?' })).toBeInTheDocument();
    expect(helpful(dialog)).toHaveAttribute('aria-pressed', 'false');
    expect(notHelpful(dialog)).toHaveAttribute('aria-pressed', 'false');
    expect(comment(dialog)).toHaveValue('');
    expect(within(dialog).getByRole('button', { name: /Cancel/ })).toBeInTheDocument();
    expect(send(dialog)).toBeInTheDocument();
  });

  it('AC-003.2 shows the clicked button as selected and the other as not selected', async () => {
    const user = renderPopup();
    const dialog = await findDialog();
    await user.click(helpful(dialog));
    expect(helpful(dialog)).toHaveAttribute('aria-pressed', 'true');
    expect(notHelpful(dialog)).toHaveAttribute('aria-pressed', 'false');
    await user.click(notHelpful(dialog));
    expect(helpful(dialog)).toHaveAttribute('aria-pressed', 'false');
    expect(notHelpful(dialog)).toHaveAttribute('aria-pressed', 'true');
  });

  it('AC-003.3 shows MSG01 under Helpful? and sends nothing when neither button is selected', async () => {
    const requests = spyOnFeedback();
    const user = renderPopup();
    const dialog = await findDialog();
    await user.click(send(dialog));
    expect(
      await within(dialog).findByText(msg('MSG01', { field_name: 'Helpful?' })),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole('group', { name: 'Helpful?' })).toHaveAccessibleDescription(
      msg('MSG01', { field_name: 'Helpful?' }),
    );
    expect(requests).toHaveLength(0);
    // Choosing an answer clears the error at once.
    await user.click(notHelpful(dialog));
    await waitFor(() =>
      expect(
        within(dialog).queryByText(msg('MSG01', { field_name: 'Helpful?' })),
      ).not.toBeInTheDocument(),
    );
  });

  it('AC-003.4 shows MSG02 under Comment and sends nothing when it is longer than 500 characters', async () => {
    const requests = spyOnFeedback();
    const user = renderPopup();
    const dialog = await findDialog();
    await user.click(helpful(dialog));
    await user.click(comment(dialog));
    await user.paste('x'.repeat(501));
    await user.click(send(dialog));
    expect(
      await within(dialog).findByText(msg('MSG02', { field_name: 'Comment', max_length: 500 })),
    ).toBeInTheDocument();
    expect(requests).toHaveLength(0);
  });

  it('AC-003.5 sends isHelpful true for Helpful with the trimmed Comment', async () => {
    const requests = spyOnFeedback();
    const user = renderPopup();
    const dialog = await findDialog();
    await user.click(helpful(dialog));
    await user.type(comment(dialog), '  Worked on my laptop  ');
    await user.click(send(dialog));
    await waitFor(() => expect(requests).toHaveLength(1));
    expect(requests[0]).toEqual({
      ticketId: TICKET_ID,
      body: { isHelpful: true, comment: 'Worked on my laptop' },
    });
  });

  it('AC-003.5 sends isHelpful false for Not helpful and comment null for an empty or white-space Comment', async () => {
    const requests = spyOnFeedback();
    const user = renderPopup();
    let dialog = await findDialog();
    await user.click(notHelpful(dialog));
    await user.click(send(dialog));
    await waitFor(() => expect(requests).toHaveLength(1));
    expect(requests[0]?.body).toEqual({ isHelpful: false, comment: null });

    await user.click(screen.getByRole('button', { name: 'Open' }));
    dialog = await findDialog();
    await user.click(notHelpful(dialog));
    await user.type(comment(dialog), '   ');
    await user.click(send(dialog));
    await waitFor(() => expect(requests).toHaveLength(2));
    expect(requests[1]?.body).toEqual({ isHelpful: false, comment: null });
  });

  it('AC-003.6 closes the popup and shows MSG26 when the request succeeds', async () => {
    const user = renderPopup();
    const dialog = await findDialog();
    await user.click(helpful(dialog));
    await user.click(send(dialog));
    expect(await screen.findByText(msg('MSG26'))).toBeInTheDocument();
    await expectClosed();
  });

  it('AC-003.7 shows Send as loading and sends the form only once on a double click (BUG-068)', async () => {
    const requests = spyOnFeedback();
    server.use(
      http.put(FEEDBACK_URL, async () => {
        await delay(200);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = renderPopup();
    const dialog = await findDialog();
    await user.click(helpful(dialog));
    await user.dblClick(send(dialog));
    await waitFor(() => expect(requests.length).toBeGreaterThan(0));
    expect(requests).toHaveLength(1);
    await waitFor(() => expect(send(dialog)).toHaveClass('ant-btn-loading'));
    expect(send(dialog)).toBeDisabled();
    expect(await screen.findByText(msg('MSG26'))).toBeInTheDocument();
    expect(requests).toHaveLength(1);
    expect(screen.getAllByText(msg('MSG26'))).toHaveLength(1);
  });

  it('AC-003.8 shows a field error from the API under its field and keeps the popup open with the input', async () => {
    server.use(
      http.put(FEEDBACK_URL, () =>
        problem(400, 'MSG02', {
          fieldErrors: [
            { field: 'comment', code: 'MSG02', params: { field_name: 'Comment', max_length: 500 } },
          ],
        }),
      ),
    );
    const user = renderPopup();
    const dialog = await findDialog();
    await user.click(helpful(dialog));
    await user.type(comment(dialog), 'Too long for the server');
    await user.click(send(dialog));
    expect(
      await within(dialog).findByText(msg('MSG02', { field_name: 'Comment', max_length: 500 })),
    ).toBeInTheDocument();
    expect(helpful(dialog)).toHaveAttribute('aria-pressed', 'true');
    expect(comment(dialog)).toHaveValue('Too long for the server');
  });

  it('AC-003.8 shows a field error on isHelpful from the API under Helpful?', async () => {
    server.use(
      http.put(FEEDBACK_URL, () =>
        problem(400, 'MSG01', {
          fieldErrors: [{ field: 'isHelpful', code: 'MSG01', params: { field_name: 'Helpful?' } }],
        }),
      ),
    );
    const user = renderPopup();
    const dialog = await findDialog();
    await user.click(helpful(dialog));
    await user.click(send(dialog));
    expect(
      await within(dialog).findByText(msg('MSG01', { field_name: 'Helpful?' })),
    ).toBeInTheDocument();
  });

  it('AC-003.9 keeps the popup open with the input and shows MSG06 for an error without a code (404)', async () => {
    const user = renderPopup('00000000-0000-4000-8000-000000000999'); // not a Published ticket of the mocks
    const dialog = await findDialog();
    await user.click(notHelpful(dialog));
    await user.type(comment(dialog), 'Still broken');
    await user.click(send(dialog));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'Give Feedback' })).toBeInTheDocument();
    expect(notHelpful(dialog)).toHaveAttribute('aria-pressed', 'true');
    expect(comment(dialog)).toHaveValue('Still broken');
  });

  it("AC-003.9 shows the message of the error's code when the API sends one", async () => {
    server.use(http.put(FEEDBACK_URL, () => problem(403, 'MSG08')));
    const user = renderPopup();
    const dialog = await findDialog();
    await user.click(helpful(dialog));
    await user.click(send(dialog));
    expect(await screen.findByText(msg('MSG08'))).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'Give Feedback' })).toBeInTheDocument();
  });

  it('AC-003.10 closes on Cancel or the close icon, sends nothing, and reopens empty', async () => {
    const requests = spyOnFeedback();
    const user = renderPopup();
    let dialog = await findDialog();
    await user.click(helpful(dialog));
    await user.type(comment(dialog), 'Draft');
    await user.click(within(dialog).getByRole('button', { name: /Cancel/ }));
    await expectClosed();

    await user.click(screen.getByRole('button', { name: 'Open' }));
    dialog = await findDialog();
    expect(helpful(dialog)).toHaveAttribute('aria-pressed', 'false');
    expect(comment(dialog)).toHaveValue('');
    await user.click(notHelpful(dialog));
    await user.click(within(dialog).getByRole('button', { name: 'Close' }));
    await expectClosed();
    expect(requests).toHaveLength(0);
  });

  it('AC-003.11 is exported by features/feedback/index.ts and opens for the given ticketId', async () => {
    const requests = spyOnFeedback();
    const user = renderPopup('5ea70000-0000-4000-8000-000000000014');
    const dialog = await findDialog();
    await user.click(helpful(dialog));
    await user.click(send(dialog));
    await waitFor(() => expect(requests).toHaveLength(1));
    expect(requests[0]?.ticketId).toBe('5ea70000-0000-4000-8000-000000000014');
  });
});
