import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MOCK_USERS } from '@/mocks/session';
import { server } from '@/mocks/server';
import { type ChatbotAnswer, type ChatbotQuestion } from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';

// Split from KnowledgeChatbotPage.test.tsx (KAN-305): the session conversations and the ratings run in this
// file, the rest of the page in the other, so Vitest can run them on two workers. A helper used by both files is
// copied: only *.test.ts(x) files may import the mock server and renderApp (eslint.config.js).

/** Wiki document id of the first mock answer (mocks/data.ts) and the signed-in Employee of `openChatbot`. */
const DOC_9001 = '00000000-0000-4000-8000-000000009001';
const EMPLOYEE_ID = MOCK_USERS.employee.user.id;
const OTHER_USER_ID = '00000000-0000-4000-8000-0000000fffff';

const VPN_QUESTION = 'How do I connect to the VPN from home?';

type RenderOptions = NonNullable<Parameters<typeof renderApp>[1]>;

/** 12.1 Knowledge Chatbot; resolves once the question box is shown. */
async function openChatbot(options: RenderOptions = { role: 'EMPLOYEE' }) {
  const user = userEvent.setup();
  const view = renderApp('/chatbot', options);
  // The first findBy also loads the lazy page module: give it more time (guideline 10 §6).
  const box = await screen.findByRole('textbox', { name: 'Question' }, { timeout: 5000 });
  return { user, box, ...view };
}

/** Records the body of every question the page sends, still answered by the feature's mock. */
function recordQuestions(): ChatbotQuestion[] {
  const sent: ChatbotQuestion[] = [];
  server.events.on('request:start', async ({ request }) => {
    if (request.method === 'POST' && request.url.endsWith('/api/v1/chatbot/answers')) {
      sent.push((await request.clone().json()) as ChatbotQuestion);
    }
  });
  return sent;
}

/** Records the Wiki document ids of every availability check the page makes. */
function recordAvailabilityChecks(): string[][] {
  const checked: string[][] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname.endsWith('/api/v1/chatbot/unavailable-documents')) {
      checked.push(url.searchParams.getAll('wikiDocumentIds'));
    }
  });
  return checked;
}

/** Answers the next question only when `release()` is called. */
function holdAnswer(answer: Partial<ChatbotAnswer>) {
  let release = () => {};
  const released = new Promise<void>((resolve) => {
    release = resolve;
  });
  server.use(
    http.post('*/api/v1/chatbot/answers', async () => {
      await released;
      return HttpResponse.json<ChatbotAnswer>({
        answerId: '00000000-0000-4000-8000-000000000002',
        outcome: 'ANSWERED',
        text: '',
        citations: [],
        ...answer,
      });
    }),
  );
  return release;
}

function thread() {
  return screen.getByRole('region', { name: 'Conversation' });
}

const LEAVE_QUESTION = 'How is annual leave counted?';
let scrollIntoView = vi.fn();
const STORED_KEY = 'tsolve.session.chatbot.conversations';

/** A stored turn in the shape of the version 2 value, with the given (possibly malformed) citations. */
function validTurn(citations: unknown[] = []) {
  return {
    question: 'VPN?',
    answer: {
      answerId: '00000000-0000-4000-8000-000000000001',
      outcome: 'ANSWERED',
      text: 'Use the VPN client.',
      citations,
    },
  };
}

function conversationList() {
  return screen.getByRole('list', { name: 'Conversations' });
}

/** The titles in the list, top to bottom. */
function listTitles() {
  return within(conversationList())
    .getAllByRole('listitem')
    .map((item) => item.textContent);
}

/** The New conversation button; the empty entry in the list is the open one, so it is always `current`. */
function newConversationButton() {
  return screen.getByRole('button', { name: 'New conversation', current: false });
}

async function ask(user: ReturnType<typeof userEvent.setup>, box: HTMLElement, question: string) {
  await user.type(box, `${question}{Enter}`);
  await waitFor(() => expect(box).toHaveValue(''));
  await waitFor(() => expect(screen.getByRole('button', { name: /Send/ })).toBeEnabled());
}

describe('KnowledgeChatbotPage session conversations', () => {
  beforeAll(async () => {
    await import('./KnowledgeChatbotPage');
  });

  beforeEach(() => {
    sessionStorage.clear();
    scrollIntoView = vi.fn();
    Element.prototype.scrollIntoView = scrollIntoView;
  });

  afterEach(() => {
    server.events.removeAllListeners();
    vi.restoreAllMocks();
  });

  it('AC-002.1 shows an empty "New conversation", selected, and stores nothing', async () => {
    await openChatbot();

    expect(listTitles()).toEqual(['New conversation']);
    expect(
      within(conversationList()).getByRole('button', { name: 'New conversation' }),
    ).toHaveAttribute('aria-current', 'true');
    expect(sessionStorage.getItem(STORED_KEY)).toBeNull();
  });

  it('AC-002.2 saves the conversation, titled with its first question, when the first answer arrives', async () => {
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);
    await within(thread()).findByText(/Install GlobalProtect/);

    expect(listTitles()).toEqual([VPN_QUESTION]);
    const stored = JSON.parse(sessionStorage.getItem(STORED_KEY) ?? '{}') as {
      conversations: { turns: { question: string }[] }[];
    };
    expect(stored.conversations).toHaveLength(1);
    expect(stored.conversations[0]?.turns[0]?.question).toBe(VPN_QUESTION);
  });

  it('AC-002.3 lists the conversations newest first by start time and highlights the open one', async () => {
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);
    await user.click(newConversationButton());
    await ask(user, box, LEAVE_QUESTION);

    expect(listTitles()).toEqual([LEAVE_QUESTION, VPN_QUESTION]);
    expect(screen.getByRole('button', { name: LEAVE_QUESTION })).toHaveAttribute(
      'aria-current',
      'true',
    );
    expect(screen.getByRole('button', { name: VPN_QUESTION })).not.toHaveAttribute('aria-current');
  });

  it('AC-002.3 disables the list while an answer is pending and files the answer in its own conversation (edge 17)', async () => {
    const release = holdAnswer({ text: 'Held answer [1].' });
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);
    await within(thread()).findByTestId('pending-answer');
    expect(newConversationButton()).toBeDisabled();
    expect(
      within(conversationList()).getByRole('button', { name: 'New conversation' }),
    ).toBeDisabled();
    release();

    expect(await within(thread()).findByText('Held answer [1].')).toBeInTheDocument();
    expect(listTitles()).toEqual([VPN_QUESTION]);
  });

  it('AC-002.4 shows the questions, answers and citations of the conversation that is opened', async () => {
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);
    await user.click(newConversationButton());
    await ask(user, box, LEAVE_QUESTION);

    await user.click(screen.getByRole('button', { name: VPN_QUESTION }));

    expect(within(thread()).getByText(VPN_QUESTION)).toBeInTheDocument();
    expect(within(thread()).getByText(/Install GlobalProtect/)).toBeInTheDocument();
    expect(within(thread()).getByText('Solution from a ticket', { exact: false })).toBeVisible();
    expect(within(thread()).queryByText(LEAVE_QUESTION)).not.toBeInTheDocument();
  });

  it('AC-002.5 adds a follow-up to the open conversation and sends its earlier turns', async () => {
    const sent = recordQuestions();
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);

    await ask(user, box, 'And on a Mac?');

    expect(sent[1]?.history.map((turn) => turn.question)).toEqual([VPN_QUESTION]);
    expect(listTitles()).toEqual([VPN_QUESTION]);
    expect(within(thread()).getByText('And on a Mac?')).toBeInTheDocument();
  });

  it('AC-002.5 sends only the last 10 turns, oldest first, after 12 turns (edge 18)', async () => {
    const sent = recordQuestions();
    const { user, box } = await openChatbot();
    for (let n = 1; n <= 12; n++) await ask(user, box, `Question ${n}`);

    await ask(user, box, 'Question 13');

    const questions = sent[12]?.history.map((turn) => turn.question);
    expect(questions).toEqual(Array.from({ length: 10 }, (_, i) => `Question ${i + 3}`));
  }, 60_000);

  it('AC-002.6 opens an empty conversation at the top, and keeps one when it is already empty', async () => {
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);

    await user.click(newConversationButton());
    expect(listTitles()).toEqual(['New conversation', VPN_QUESTION]);
    expect(within(thread()).queryByText(VPN_QUESTION)).not.toBeInTheDocument();

    await user.click(newConversationButton());
    expect(listTitles()).toEqual(['New conversation', VPN_QUESTION]);
  });

  it('AC-002.7 brings the conversations back after a page reload, with the empty one open (edge 15)', async () => {
    const first = await openChatbot();
    await ask(first.user, first.box, VPN_QUESTION);
    first.unmount();

    await openChatbot();

    expect(listTitles()).toEqual(['New conversation', VPN_QUESTION]);
    expect(within(thread()).queryByText(VPN_QUESTION)).not.toBeInTheDocument();
  });

  it('AC-002.8 starts empty when the stored conversations belong to another user (edge 12)', async () => {
    sessionStorage.setItem(
      STORED_KEY,
      JSON.stringify({
        version: 2,
        userId: OTHER_USER_ID,
        conversations: [{ id: 'x', startedAt: 1, turns: [validTurn()] }],
      }),
    );

    await openChatbot();

    expect(listTitles()).toEqual(['New conversation']);
  });

  it.each([
    ['a numeric user id', { version: 2, userId: 2 }],
    ['version 1', { version: 1, userId: EMPLOYEE_ID }],
  ])('AC-002.12 starts empty when the stored conversations carry %s', async (_what, head) => {
    sessionStorage.setItem(
      STORED_KEY,
      JSON.stringify({ ...head, conversations: [{ id: 'x', startedAt: 1, turns: [validTurn()] }] }),
    );

    await openChatbot();

    expect(listTitles()).toEqual(['New conversation']);
  });

  it('AC-002.12 starts empty when a stored citation has a numeric Wiki document id', async () => {
    const citation = { number: 1, type: 'WIKI_DOCUMENT', wikiDocumentId: 9001, title: 'VPN' };
    sessionStorage.setItem(
      STORED_KEY,
      JSON.stringify({
        version: 2,
        userId: EMPLOYEE_ID,
        conversations: [{ id: 'x', startedAt: 1, turns: [validTurn([citation])] }],
      }),
    );

    await openChatbot();

    expect(listTitles()).toEqual(['New conversation']);
  });

  it("AC-002.12 stores the conversations under the signed-in user's UUID", async () => {
    const { user, box } = await openChatbot();

    await ask(user, box, VPN_QUESTION);

    const stored = JSON.parse(sessionStorage.getItem(STORED_KEY) ?? '{}') as {
      version: number;
      userId: string;
    };
    expect(stored.version).toBe(2);
    expect(stored.userId).toBe(EMPLOYEE_ID);
  });

  it('AC-002.12 sends the Wiki document ids of the citations as UUID strings in the availability request', async () => {
    const checked = recordAvailabilityChecks();
    const { user, box } = await openChatbot();

    await ask(user, box, VPN_QUESTION);

    await waitFor(() => expect(checked).toEqual([[DOC_9001]]));
  });

  it('AC-002.11 still answers and keeps the conversations of the open page when storage is blocked (edge 14)', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    const { user, box } = await openChatbot();

    await ask(user, box, VPN_QUESTION);
    await user.click(newConversationButton());
    await ask(user, box, LEAVE_QUESTION);

    expect(listTitles()).toEqual([LEAVE_QUESTION, VPN_QUESTION]);
  });

  it('shows the newest turn after each answer (PR #51 finding 2)', async () => {
    const { user, box } = await openChatbot();
    scrollIntoView.mockClear();

    await ask(user, box, VPN_QUESTION);

    expect(scrollIntoView).toHaveBeenCalled();
  });
});

const RATING_PATH = '*/api/v1/chatbot/answers/:answerId/rating';

type RatingRequest = { method: string; answerId: string; body: unknown };

/** Records every rating request the page sends, still answered by the feature's mock. */
function recordRatings(): RatingRequest[] {
  const sent: RatingRequest[] = [];
  server.events.on('request:start', async ({ request }) => {
    const match = /\/chatbot\/answers\/([^/]+)\/rating$/.exec(new URL(request.url).pathname);
    if (match) {
      const text = await request.clone().text();
      sent.push({
        method: request.method,
        answerId: match[1] ?? '',
        body: text === '' ? null : (JSON.parse(text) as unknown),
      });
    }
  });
  return sent;
}

/** Answers the next rating request (PUT or DELETE) only when `release()` is called. */
function holdRating() {
  let release = () => {};
  const released = new Promise<void>((resolve) => {
    release = resolve;
  });
  const hold = async () => {
    await released;
    return new HttpResponse(null, { status: 204 });
  };
  server.use(http.put(RATING_PATH, hold), http.delete(RATING_PATH, hold));
  return release;
}

const helpful = (pressed?: boolean) =>
  within(thread()).getByRole('button', { name: 'Helpful', pressed });
const notHelpful = (pressed?: boolean) =>
  within(thread()).getByRole('button', { name: 'Not helpful', pressed });

describe('KnowledgeChatbotPage ratings', () => {
  beforeAll(async () => {
    await import('./KnowledgeChatbotPage');
  });

  beforeEach(() => sessionStorage.clear());

  afterEach(() => {
    server.events.removeAllListeners();
    vi.restoreAllMocks();
  });

  it('AC-003.1 shows Helpful and Not helpful under an answer, the MSG109 answer included', async () => {
    const { user, box } = await openChatbot();

    await ask(user, box, VPN_QUESTION);
    expect(helpful(false)).toBeEnabled();
    expect(notHelpful(false)).toBeEnabled();

    await ask(user, box, 'Who won the football match yesterday?');
    expect(within(thread()).getAllByRole('button', { name: 'Helpful' })).toHaveLength(2);
    expect(within(thread()).getAllByRole('button', { name: 'Not helpful' })).toHaveLength(2);
  });

  it('AC-003.1 shows no rating buttons after a failed ask (edge 24)', async () => {
    server.use(http.post('*/api/v1/chatbot/answers', () => problem(503, 'MSG06')));
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);
    await screen.findByText(msg('MSG06'));

    expect(screen.queryByRole('button', { name: 'Helpful' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Not helpful' })).not.toBeInTheDocument();
  });

  it('AC-003.1 shows no rating buttons while the answer is pending (edge 24)', async () => {
    const release = holdAnswer({ text: 'Use GlobalProtect [1].' });
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);
    await within(thread()).findByTestId('pending-answer');
    expect(screen.queryByRole('button', { name: 'Helpful' })).not.toBeInTheDocument();

    release();
    await within(thread()).findByText('Use GlobalProtect [1].');
    expect(helpful(false)).toBeInTheDocument();
  });

  it('AC-003.2 sends PUT with the rating, disables both buttons until the response, then highlights it', async () => {
    const sent = recordRatings();
    const release = holdRating();
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);

    await user.click(helpful());

    await waitFor(() => expect(sent).toHaveLength(1));
    expect(sent[0]).toMatchObject({ method: 'PUT', body: { rating: 'HELPFUL' } });
    expect(helpful()).toBeDisabled();
    expect(notHelpful()).toBeDisabled();
    expect(helpful()).toHaveAttribute('aria-pressed', 'false');

    release();
    await waitFor(() => expect(helpful(true)).toBeEnabled());
    expect(notHelpful(false)).toBeEnabled();
  });

  it('AC-003.2 sends one request when a button is clicked twice quickly (edge 22)', async () => {
    const sent = recordRatings();
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);

    await user.dblClick(helpful());

    await waitFor(() => expect(helpful(true)).toBeEnabled());
    expect(sent).toHaveLength(1);
  });

  it('AC-003.3 sends the new rating when the other button is clicked and highlights only that one', async () => {
    const sent = recordRatings();
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);
    await user.click(helpful());
    await waitFor(() => expect(helpful(true)).toBeEnabled());

    await user.click(notHelpful());

    await waitFor(() => expect(notHelpful(true)).toBeEnabled());
    expect(helpful(false)).toBeInTheDocument();
    expect(sent.map((r) => [r.method, r.body])).toEqual([
      ['PUT', { rating: 'HELPFUL' }],
      ['PUT', { rating: 'NOT_HELPFUL' }],
    ]);
  });

  it('AC-003.4 highlights the rating again when its conversation is opened again', async () => {
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);
    await user.click(helpful());
    await waitFor(() => expect(helpful(true)).toBeEnabled());

    await user.click(newConversationButton());
    await user.click(screen.getByRole('button', { name: VPN_QUESTION }));

    expect(helpful(true)).toBeInTheDocument();
    expect(notHelpful(false)).toBeInTheDocument();
  });

  it('AC-003.4 highlights the rating after a page reload', async () => {
    const first = await openChatbot();
    await ask(first.user, first.box, VPN_QUESTION);
    await first.user.click(helpful());
    await waitFor(() => expect(helpful(true)).toBeEnabled());
    first.unmount();

    const { user } = await openChatbot();
    await user.click(screen.getByRole('button', { name: VPN_QUESTION }));

    expect(helpful(true)).toBeInTheDocument();
  });

  it('AC-003.4 keeps a rating whose save finishes after the user left the page (BUG-008)', async () => {
    const release = holdRating();
    const first = await openChatbot();
    await ask(first.user, first.box, VPN_QUESTION);
    await first.user.click(helpful());
    first.unmount();

    release();
    const key = 'tsolve.session.chatbot.conversations';
    await waitFor(() => expect(sessionStorage.getItem(key)).toContain('"rating":"HELPFUL"'));
    const { user } = await openChatbot();
    await user.click(screen.getByRole('button', { name: VPN_QUESTION }));

    expect(helpful(true)).toBeInTheDocument();
  });

  it('AC-003.4 keeps the rating in memory when storage is blocked (edge 25)', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);
    await user.click(notHelpful());
    await waitFor(() => expect(notHelpful(true)).toBeEnabled());

    await user.click(newConversationButton());
    await user.click(screen.getByRole('button', { name: VPN_QUESTION }));

    expect(notHelpful(true)).toBeInTheDocument();
  });

  it('AC-003.5 shows MSG06 and keeps the previous highlight when saving a rating fails', async () => {
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);
    await user.click(helpful());
    await waitFor(() => expect(helpful(true)).toBeEnabled());
    server.use(http.put(RATING_PATH, () => problem(500)));

    await user.click(notHelpful());

    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    await waitFor(() => expect(notHelpful()).toBeEnabled());
    expect(helpful(true)).toBeInTheDocument();
    expect(notHelpful(false)).toBeInTheDocument();
  });

  it('AC-003.5 shows MSG06 and keeps the highlight when removing a rating fails (edge 27)', async () => {
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);
    await user.click(helpful());
    await waitFor(() => expect(helpful(true)).toBeEnabled());
    server.use(http.delete(RATING_PATH, () => problem(500)));

    await user.click(helpful());

    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    await waitFor(() => expect(helpful()).toBeEnabled());
    expect(helpful(true)).toBeInTheDocument();
  });

  it('AC-003.6 sends DELETE when the highlighted button is clicked and removes the highlight once it succeeds', async () => {
    const sent = recordRatings();
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);
    await user.click(notHelpful());
    await waitFor(() => expect(notHelpful(true)).toBeEnabled());
    const release = holdRating();

    await user.click(notHelpful());

    await waitFor(() => expect(sent).toHaveLength(2));
    expect(sent[1]).toMatchObject({ method: 'DELETE', body: null });
    expect(notHelpful()).toHaveAttribute('aria-pressed', 'true');
    release();
    await waitFor(() => expect(notHelpful(false)).toBeEnabled());
    expect(helpful(false)).toBeInTheDocument();
  });

  it('AC-003.2 puts the rating on its own turn when another conversation is opened while it saves (edge 23)', async () => {
    const release = holdRating();
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);
    await user.click(helpful());

    await user.click(newConversationButton());
    await ask(user, box, LEAVE_QUESTION);
    expect(helpful(false)).toBeInTheDocument();
    release();
    await user.click(screen.getByRole('button', { name: VPN_QUESTION }));

    await waitFor(() => expect(helpful(true)).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: LEAVE_QUESTION }));
    expect(helpful(false)).toBeInTheDocument();
  });
});
