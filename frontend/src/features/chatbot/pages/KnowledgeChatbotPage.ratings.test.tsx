import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import {
  getChatbotConversation,
  rateChatbotAnswer,
  type ChatbotAsk,
  type ChatbotAnswer,
  type ChatbotConversation,
} from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { renderApp } from '@/test/render';
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

function answerWith(answer: Partial<ChatbotAnswer>, wait?: Promise<void>) {
  const id = '00000000-0000-4000-8000-00000000c001';
  const conversation: ChatbotConversation = {
    chatSessionId: id,
    title: '',
    startedAt: new Date().toISOString(),
    turns: [],
  };
  server.use(
    http.post('*/api/v1/chatbot/answers', async ({ request }) => {
      await wait;
      const { question } = (await request.json()) as ChatbotAsk;
      const result: ChatbotAnswer = {
        answerId: crypto.randomUUID(),
        outcome: 'ANSWERED',
        text: '',
        citations: [],
        ...answer,
      };
      conversation.title ||= question;
      conversation.turns.push({ question, answer: result, rating: null });
      return HttpResponse.json({ ...result, chatSessionId: id });
    }),
    http.get(`*/api/v1/chatbot/conversations/${id}`, () => HttpResponse.json(conversation)),
    http.get('*/api/v1/chatbot/conversations', () =>
      HttpResponse.json(
        conversation.turns.length
          ? [{ chatSessionId: id, title: conversation.title, startedAt: conversation.startedAt }]
          : [],
      ),
    ),
  );
}

/** Answers the next question only when `release()` is called. */
function holdAnswer(answer: Partial<ChatbotAnswer>) {
  let release = () => {};
  const released = new Promise<void>((resolve) => {
    release = resolve;
  });
  answerWith(answer, released);
  return release;
}

function thread() {
  return screen.getByRole('region', { name: 'Conversation' });
}

async function enter(user: ReturnType<typeof userEvent.setup>, box: HTMLElement, text: string) {
  const keys = text.indexOf('{');
  await user.click(box);
  await user.paste(keys < 0 ? text : text.slice(0, keys));
  if (keys >= 0) await user.keyboard(text.slice(keys));
}

const LEAVE_QUESTION = 'How is annual leave counted?';
/** The New conversation button; the empty entry in the list is the open one, so it is always `current`. */
function newConversationButton() {
  return screen.getByRole('button', { name: 'New conversation', current: false });
}

async function ask(user: ReturnType<typeof userEvent.setup>, box: HTMLElement, question: string) {
  await enter(user, box, `${question}{Enter}`);
  await waitFor(() => expect(box).toHaveValue(''));
  await waitFor(() => expect(screen.getByRole('button', { name: /Send/ })).toBeEnabled());
}

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
  const hold = async ({ request }: { request: Request }) => {
    await released;
    const { getResponse } = await import('msw');
    const { handlers } = await import('../mocks/handlers');
    return getResponse(handlers, request);
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

    await enter(user, box, `${VPN_QUESTION}{Enter}`);
    await screen.findByText(msg('MSG06'));

    expect(screen.queryByRole('button', { name: 'Helpful' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Not helpful' })).not.toBeInTheDocument();
  });

  it('AC-003.1 shows no rating buttons while the answer is pending (edge 24)', async () => {
    const release = holdAnswer({ text: 'Use GlobalProtect [1].' });
    const { user, box } = await openChatbot();

    await enter(user, box, `${VPN_QUESTION}{Enter}`);
    await within(await screen.findByRole('region', { name: 'Conversation' })).findByTestId(
      'pending-answer',
    );
    expect(screen.queryByRole('button', { name: 'Helpful' })).not.toBeInTheDocument();

    release();
    await within(await screen.findByRole('region', { name: 'Conversation' })).findByText(
      'Use GlobalProtect [1].',
    );
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

  it('AC-003.4 reads the current server rating when opening a saved conversation', async () => {
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);
    const { listChatbotConversations } = await import('@/shared/api');
    const id = (await listChatbotConversations())[0]?.chatSessionId ?? '';
    const answerId = (await getChatbotConversation(id)).turns[0]?.answer.answerId ?? '';
    await rateChatbotAnswer(answerId, { rating: 'NOT_HELPFUL' });
    await user.click(newConversationButton());
    await user.click(await screen.findByRole('button', { name: VPN_QUESTION }));
    expect(
      await within(await screen.findByRole('region', { name: 'Conversation' })).findByRole(
        'button',
        { name: 'Not helpful', pressed: true },
      ),
    ).toBeInTheDocument();
  });

  it('AC-003.4 highlights the rating again when its conversation is opened again', async () => {
    const { user, box } = await openChatbot();
    await ask(user, box, VPN_QUESTION);
    await user.click(helpful());
    await waitFor(() => expect(helpful(true)).toBeEnabled());

    await user.click(newConversationButton());
    await user.click(await screen.findByRole('button', { name: VPN_QUESTION }));

    expect(
      await within(await screen.findByRole('region', { name: 'Conversation' })).findByRole(
        'button',
        { name: 'Helpful', pressed: true },
      ),
    ).toBeInTheDocument();
    expect(notHelpful(false)).toBeInTheDocument();
  });

  it('AC-003.4 highlights the rating after a page reload', async () => {
    const first = await openChatbot();
    await ask(first.user, first.box, VPN_QUESTION);
    await first.user.click(helpful());
    await waitFor(() => expect(helpful(true)).toBeEnabled());
    first.unmount();

    const { user } = await openChatbot();
    await user.click(await screen.findByRole('button', { name: VPN_QUESTION }));

    expect(
      await within(await screen.findByRole('region', { name: 'Conversation' })).findByRole(
        'button',
        { name: 'Helpful', pressed: true },
      ),
    ).toBeInTheDocument();
  });

  it('AC-003.4 keeps a rating whose save finishes after the user left the page (BUG-008)', async () => {
    const release = holdRating();
    const first = await openChatbot();
    await ask(first.user, first.box, VPN_QUESTION);
    await first.user.click(helpful());
    first.unmount();

    release();
    await waitFor(async () => {
      const conversations = await import('@/shared/api').then((api) =>
        api.listChatbotConversations(),
      );
      const conversation = await getChatbotConversation(conversations[0]?.chatSessionId ?? '');
      expect(conversation.turns[0]?.rating).toBe('HELPFUL');
    });
    const { user } = await openChatbot();
    await user.click(await screen.findByRole('button', { name: VPN_QUESTION }));

    expect(
      await within(await screen.findByRole('region', { name: 'Conversation' })).findByRole(
        'button',
        { name: 'Helpful', pressed: true },
      ),
    ).toBeInTheDocument();
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
    await user.click(await screen.findByRole('button', { name: VPN_QUESTION }));

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
    expect(
      await within(await screen.findByRole('region', { name: 'Conversation' })).findByRole(
        'button',
        { name: 'Helpful', pressed: true },
      ),
    ).toBeInTheDocument();
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
    expect(
      await within(await screen.findByRole('region', { name: 'Conversation' })).findByRole(
        'button',
        { name: 'Helpful', pressed: true },
      ),
    ).toBeInTheDocument();
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
    await user.click(await screen.findByRole('button', { name: VPN_QUESTION }));

    await waitFor(() => expect(helpful(true)).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: LEAVE_QUESTION }));
    expect(helpful(false)).toBeInTheDocument();
  });
});
