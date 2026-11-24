import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { getResponse, http } from 'msw';
import { server } from '@/mocks/server';
import {
  askChatbot,
  deleteChatbotConversation,
  listChatbotConversations,
  type ChatbotAsk,
} from '@/shared/api';
import { problem } from '@/shared/api/mocks';
import { msg } from '@/shared/messages';
import { setDevUser } from '@/shared/auth';
import { renderApp } from '@/test/render';
import { handlers } from '../mocks/handlers';

const VPN = 'How do I connect to the VPN?';
const LEAVE = 'How is annual leave counted?';
const STORED_KEY = 'tsolve.session.chatbot.conversations';
const UUID = /^[0-9a-f-]{36}$/;

async function openChatbot() {
  const user = userEvent.setup();
  const view = renderApp('/chatbot', { role: 'EMPLOYEE' });
  const box = await screen.findByRole('textbox', { name: 'Question' }, { timeout: 5000 });
  return { user, box, ...view };
}
const list = () => screen.getByRole('list', { name: 'Conversations' });
const thread = () => screen.getByRole('region', { name: 'Conversation' });
const newButton = () => screen.getByRole('button', { name: 'New conversation', current: false });
const titles = () =>
  within(list())
    .getAllByRole('listitem')
    .map((item) => within(item).getAllByRole('button')[0]?.textContent);

async function ask(view: Awaited<ReturnType<typeof openChatbot>>, question: string) {
  await view.user.click(view.box);
  await view.user.paste(question);
  await view.user.keyboard('{Enter}');
  await waitFor(() => expect(view.box).toHaveValue(''));
  await screen.findByRole('button', { current: true });
}
function deleteButton(title: string) {
  const row = screen.getByRole('button', { name: title }).closest('li');
  if (!row) throw new Error('Missing conversation row');
  return within(row).getByRole('button', { name: 'Delete' });
}
function hold(method: 'get' | 'post', path: string) {
  let release = () => {};
  const waiting = new Promise<void>((resolve) => {
    release = resolve;
  });
  server.use(
    http[method](path, async ({ request }) => {
      await waiting;
      return getResponse(handlers, request);
    }),
  );
  return release;
}

describe('KnowledgeChatbotPage saved conversations', () => {
  it('AC-001.7 scrolls to the newest turn after an answer (PR #51 finding 2)', async () => {
    const previous = Object.getOwnPropertyDescriptor(Element.prototype, 'scrollIntoView');
    const scroll = vi.fn();
    Object.defineProperty(Element.prototype, 'scrollIntoView', {
      configurable: true,
      writable: true,
      value: scroll,
    });
    try {
      const view = await openChatbot();
      scroll.mockClear();
      await ask(view, VPN);
      expect(scroll).toHaveBeenCalled();
    } finally {
      if (previous) Object.defineProperty(Element.prototype, 'scrollIntoView', previous);
      else Reflect.deleteProperty(Element.prototype, 'scrollIntoView');
    }
  });

  beforeAll(async () => {
    await import('./KnowledgeChatbotPage');
  });
  beforeEach(() => {
    sessionStorage.clear();
    setDevUser('employee');
  });
  afterEach(() => {
    server.events.removeAllListeners();
    vi.restoreAllMocks();
  });

  it('AC-002.1 selects one empty New conversation ready for the first question', async () => {
    await openChatbot();
    expect(titles()).toEqual(['New conversation']);
    expect(within(list()).getByRole('button', { name: 'New conversation' })).toHaveAttribute(
      'aria-current',
      'true',
    );
    expect(within(thread()).queryByRole('article')).not.toBeInTheDocument();
  });

  it('AC-002.2 uses the first answer server id and title and keeps the conversation open', async () => {
    const view = await openChatbot();
    await ask(view, VPN);
    expect(titles()).toEqual([VPN]);
    const saved = (await listChatbotConversations()).content[0];
    expect(saved?.chatSessionId).toMatch(UUID);
    expect(
      await within(await screen.findByRole('region', { name: 'Conversation' })).findByText(VPN),
    ).toBeInTheDocument();
    expect(sessionStorage.getItem(STORED_KEY)).toBeNull();
  });

  it('AC-002.3 loads server conversations newest first and highlights the open one (BUG-382)', async () => {
    await askChatbot({ question: VPN });
    await askChatbot({ question: LEAVE });
    const view = await openChatbot();
    await screen.findByRole('button', { name: VPN }, { timeout: 5000 });
    expect(titles()).toEqual(['New conversation', LEAVE, VPN]);
    await view.user.click(screen.getByRole('button', { name: VPN }));
    expect(screen.getByRole('button', { name: VPN })).toHaveAttribute('aria-current', 'true');
  });

  it('AC-002.4 loads every saved turn in order with its citations', async () => {
    const first = await askChatbot({ question: VPN });
    await askChatbot({ question: LEAVE, chatSessionId: first.chatSessionId });
    const view = await openChatbot();
    await view.user.click(await screen.findByRole('button', { name: VPN }, { timeout: 5000 }));
    expect(
      await within(await screen.findByRole('region', { name: 'Conversation' })).findByText(
        /Install GlobalProtect/,
      ),
    ).toBeInTheDocument();
    expect(within(thread()).getByText(/Public holidays/)).toBeInTheDocument();
    expect(within(thread()).getAllByRole('list', { name: 'Citations' })).toHaveLength(2);
    expect(thread().textContent?.indexOf(VPN)).toBeLessThan(
      thread().textContent?.indexOf(LEAVE) ?? 0,
    );
  });

  it('AC-002.5 sends the reopened UUID with no history and appends the follow-up', async () => {
    const first = await askChatbot({ question: VPN });
    const sent: ChatbotAsk[] = [];
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'POST' && request.url.endsWith('/chatbot/answers'))
        sent.push((await request.clone().json()) as ChatbotAsk);
    });
    const view = await openChatbot();
    await view.user.click(await screen.findByRole('button', { name: VPN }, { timeout: 5000 }));
    await within(await screen.findByRole('region', { name: 'Conversation' })).findByText(
      /Install GlobalProtect/,
    );
    await ask(view, 'And on Mac?');
    expect(sent).toEqual([{ question: 'And on Mac?', chatSessionId: first.chatSessionId }]);
    expect(
      await within(await screen.findByRole('region', { name: 'Conversation' })).findByText(
        'And on Mac?',
      ),
    ).toBeInTheDocument();
    expect(titles()).toEqual([VPN]);
  });

  it('AC-002.6 opens a single empty conversation at the top', async () => {
    const view = await openChatbot();
    await ask(view, VPN);
    await view.user.click(newButton());
    await view.user.click(newButton());
    expect(titles()).toEqual(['New conversation', VPN]);
    expect(within(thread()).queryByText(VPN)).not.toBeInTheDocument();
  });

  it('AC-002.7 reloads the server list with the empty conversation open and ignores old storage', async () => {
    sessionStorage.setItem(
      STORED_KEY,
      '{"version":2,"conversations":[{"title":"Old local chat"}]}',
    );
    const first = await openChatbot();
    await ask(first, VPN);
    first.unmount();
    const getItem = vi.spyOn(Storage.prototype, 'getItem');
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    await openChatbot();
    await screen.findByRole('button', { name: VPN }, { timeout: 5000 });
    expect(titles()).toEqual(['New conversation', VPN]);
    expect(within(thread()).queryByText(VPN)).not.toBeInTheDocument();
    expect(getItem).not.toHaveBeenCalledWith(STORED_KEY);
    expect(setItem.mock.calls.some(([key]) => key === STORED_KEY)).toBe(false);
  });

  it('AC-002.8 shows list loading, then MSG06 and Retry while a new question remains available', async () => {
    let release = () => {};
    const waiting = new Promise<void>((resolve) => {
      release = resolve;
    });
    let fails = true;
    server.use(
      http.get('*/api/v1/chatbot/conversations', async ({ request }) => {
        await waiting;
        return fails ? problem(500) : getResponse(handlers, request);
      }),
    );
    const view = await openChatbot();
    expect(screen.getByRole('status', { name: 'Loading conversations' })).toBeInTheDocument();
    release();
    expect(await screen.findByRole('alert')).toHaveTextContent(msg('MSG06'));
    expect(newButton()).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Send' })).toBeEnabled();
    fails = false;
    await view.user.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    await ask(view, VPN);
  });

  it('AC-002.8 retries a failed conversation and still lets the user start a new one', async () => {
    const saved = await askChatbot({ question: VPN });
    const path = `*/api/v1/chatbot/conversations/${saved.chatSessionId}`;
    // A 404 means the conversation is gone (BUG-389); any other failure offers Retry.
    server.use(http.get(path, () => problem(500, 'MSG06')));
    const view = await openChatbot();
    await view.user.click(await screen.findByRole('button', { name: VPN }, { timeout: 5000 }));
    expect(await screen.findByRole('alert')).toHaveTextContent(msg('MSG06'));
    server.use(http.get(path, ({ request }) => getResponse(handlers, request)));
    await view.user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(
      await within(await screen.findByRole('region', { name: 'Conversation' })).findByText(
        /Install GlobalProtect/,
      ),
    ).toBeInTheDocument();
    await view.user.click(newButton());
    await ask(view, LEAVE);
  });

  it('AC-002.9 checks UUID Wiki citations on reopen and replaces unavailable links', async () => {
    await askChatbot({ question: 'expense' });
    const view = await openChatbot();
    await view.user.click(await screen.findByRole('button', { name: 'expense' }));
    expect(
      await within(await screen.findByRole('region', { name: 'Conversation' })).findByText(
        msg('MSG110'),
      ),
    ).toBeInTheDocument();
    expect(within(thread()).queryByRole('link')).not.toBeInTheDocument();
  });

  it('AC-002.12 passes server conversation UUIDs unchanged to open and delete', async () => {
    const answer = await askChatbot({ question: VPN });
    const requested: string[] = [];
    server.events.on('request:start', ({ request }) => {
      if (request.url.endsWith(`/conversations/${answer.chatSessionId}`))
        requested.push(`${request.method} ${new URL(request.url).pathname}`);
    });
    const view = await openChatbot();
    await view.user.click(await screen.findByRole('button', { name: VPN }, { timeout: 5000 }));
    await within(await screen.findByRole('region', { name: 'Conversation' })).findByText(
      /Install GlobalProtect/,
    );
    await view.user.click(deleteButton(VPN));
    await view.user.click(screen.getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(requested).toContain(`DELETE /api/v1/chatbot/conversations/${answer.chatSessionId}`),
    );
    expect(requested).toContain(`GET /api/v1/chatbot/conversations/${answer.chatSessionId}`);
  });

  it('AC-002.13 asks MSG103 before deleting and Cancel changes nothing', async () => {
    await askChatbot({ question: VPN });
    const view = await openChatbot();
    await screen.findByRole('button', { name: VPN }, { timeout: 5000 });
    await view.user.click(deleteButton(VPN));
    expect(await screen.findByRole('dialog')).toHaveTextContent(msg('MSG103', { title: VPN }));
    expect((await listChatbotConversations()).content).toHaveLength(1);
    await view.user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect((await listChatbotConversations()).content).toHaveLength(1);
    expect(titles()).toEqual(['New conversation', VPN]);
  });

  it.each([true, false])(
    'AC-002.13 removes a confirmed conversation (open: %s) after success',
    async (isOpen) => {
      await askChatbot({ question: VPN });
      const view = await openChatbot();
      await screen.findByRole('button', { name: VPN }, { timeout: 5000 });
      if (isOpen) {
        await view.user.click(screen.getByRole('button', { name: VPN }));
        await within(await screen.findByRole('region', { name: 'Conversation' })).findByText(
          /Install GlobalProtect/,
        );
      }
      await view.user.click(deleteButton(VPN));
      await view.user.click(screen.getByRole('button', { name: 'OK' }));
      await waitFor(() => expect(titles()).toEqual(['New conversation']));
      expect(within(thread()).queryByText(VPN)).not.toBeInTheDocument();
    },
  );

  it('AC-002.13 deleting another conversation keeps the open thread', async () => {
    await askChatbot({ question: VPN });
    await askChatbot({ question: LEAVE });
    const view = await openChatbot();
    await view.user.click(await screen.findByRole('button', { name: VPN }, { timeout: 5000 }));
    await within(await screen.findByRole('region', { name: 'Conversation' })).findByText(
      /Install GlobalProtect/,
    );
    await view.user.click(deleteButton(LEAVE));
    await view.user.click(screen.getByRole('button', { name: 'OK' }));
    await waitFor(() => expect(titles()).toEqual([VPN]));
    expect(within(thread()).getByText(VPN)).toBeInTheDocument();
  });

  it('AC-002.14 shows MSG06 and keeps the conversation after delete fails', async () => {
    await askChatbot({ question: VPN });
    server.use(http.delete('*/api/v1/chatbot/conversations/:id', () => problem(404, 'MSG04')));
    const view = await openChatbot();
    await view.user.click(await screen.findByRole('button', { name: VPN }, { timeout: 5000 }));
    await within(await screen.findByRole('region', { name: 'Conversation' })).findByText(
      /Install GlobalProtect/,
    );
    await view.user.click(deleteButton(VPN));
    await view.user.click(screen.getByRole('button', { name: 'OK' }));
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(titles()).toEqual([VPN]);
    expect(within(thread()).getByText(VPN)).toBeInTheDocument();
  });

  it('AC-013.4 leaves a conversation deleted elsewhere when a question gets 404 (BUG-389)', async () => {
    const view = await openChatbot();
    await ask(view, VPN);
    const saved = (await listChatbotConversations()).content[0];
    if (!saved) throw new Error('Missing saved conversation');
    await deleteChatbotConversation(saved.chatSessionId);
    await view.user.click(view.box);
    await view.user.paste(LEAVE);
    await view.user.keyboard('{Enter}');
    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    await waitFor(() => expect(view.box).toHaveValue(LEAVE));
    await waitFor(() => expect(titles()).toEqual(['New conversation']));
    expect(within(list()).getByRole('button', { name: 'New conversation' })).toHaveAttribute(
      'aria-current',
      'true',
    );
    await view.user.keyboard('{Enter}');
    await waitFor(() => expect(titles()).toEqual([LEAVE]));
  });

  it('AC-013.4 leaves a conversation that is gone when it is opened (BUG-389)', async () => {
    const { chatSessionId } = await askChatbot({ question: VPN });
    const view = await openChatbot();
    await screen.findByRole('button', { name: VPN }, { timeout: 5000 });
    if (!chatSessionId) throw new Error('Missing conversation id');
    await deleteChatbotConversation(chatSessionId);
    await view.user.click(screen.getByRole('button', { name: VPN }));
    await waitFor(() => expect(titles()).toEqual(['New conversation']));
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
  });

  it('AC-002.15 disables Delete, list and New conversation while an answer is pending', async () => {
    await askChatbot({ question: VPN });
    const release = hold('post', '*/api/v1/chatbot/answers');
    const view = await openChatbot();
    await screen.findByRole('button', { name: VPN }, { timeout: 5000 });
    await view.user.click(view.box);
    await view.user.paste(LEAVE);
    await view.user.keyboard('{Enter}');
    await screen.findByTestId('pending-answer');
    for (const button of within(list()).getAllByRole('button')) expect(button).toBeDisabled();
    expect(newButton()).toBeDisabled();
    release();
    await screen.findByRole('button', { name: LEAVE });
    await waitFor(() => expect(deleteButton(VPN)).toBeEnabled());
  });

  it('AC-001.6 renders a successful answer even while its conversation is being refreshed', async () => {
    const release = hold('get', '*/api/v1/chatbot/conversations/:id');
    const view = await openChatbot();
    await ask(view, VPN);
    expect(
      await within(await screen.findByRole('region', { name: 'Conversation' })).findByText(
        /Install GlobalProtect/,
      ),
    ).toBeInTheDocument();
    release();
  });

  describe('paging', () => {
    const showMore = () => screen.queryByRole('button', { name: /Show more/ });
    async function seed(count: number) {
      for (let i = 0; i < count; i++)
        await askChatbot({ question: `Q${String(i).padStart(2, '0')}` });
    }

    it('AC-002.3 shows the 10 newest conversations and AC-002.16 appends the next page on Show more (KAN-382)', async () => {
      await seed(11);
      const view = await openChatbot();
      await screen.findByRole('button', { name: 'Q10' }, { timeout: 5000 });
      expect(titles()).toHaveLength(11);
      expect(screen.queryByRole('button', { name: 'Q00' })).not.toBeInTheDocument();
      await view.user.click(showMore() as HTMLElement);
      await screen.findByRole('button', { name: 'Q00' }, { timeout: 5000 });
      expect(titles()).toEqual([
        'New conversation',
        ...Array.from({ length: 11 }, (_, i) => `Q${String(10 - i).padStart(2, '0')}`),
      ]);
      expect(showMore()).not.toBeInTheDocument();
    });

    it('AC-002.16 hides Show more when every conversation fits one page', async () => {
      await seed(2);
      await openChatbot();
      await screen.findByRole('button', { name: 'Q01' }, { timeout: 5000 });
      expect(showMore()).not.toBeInTheDocument();
    });

    it('AC-002.16 shows Show more as loading while the next page is fetched', async () => {
      await seed(11);
      const view = await openChatbot();
      await screen.findByRole('button', { name: 'Q10' }, { timeout: 5000 });
      const release = hold('get', '*/api/v1/chatbot/conversations');
      await view.user.click(showMore() as HTMLElement);
      await waitFor(() => expect(showMore()).toHaveClass('ant-btn-loading'), { timeout: 5000 });
      release();
      await screen.findByRole('button', { name: 'Q00' }, { timeout: 5000 });
    });

    it('AC-002.16 shows MSG06 and keeps the button and the rows when the next page fails', async () => {
      await seed(11);
      const view = await openChatbot();
      await screen.findByRole('button', { name: 'Q10' }, { timeout: 5000 });
      server.use(http.get('*/api/v1/chatbot/conversations', () => problem(500)));
      await view.user.click(showMore() as HTMLElement);
      expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
      expect(showMore()).toBeEnabled();
      expect(titles()).toHaveLength(11);
      expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
    });

    it('AC-002.16 reloads the loaded pages after an ask, so nothing is shown twice (P1)', async () => {
      await seed(11);
      const view = await openChatbot();
      await screen.findByRole('button', { name: 'Q10' }, { timeout: 5000 });
      await view.user.click(showMore() as HTMLElement);
      await screen.findByRole('button', { name: 'Q00' }, { timeout: 5000 });
      await ask(view, VPN);
      await waitFor(() => expect(titles()).toHaveLength(12), { timeout: 5000 });
      expect(titles().slice(0, 2)).toEqual([VPN, 'Q10']);
      expect(new Set(titles()).size).toBe(12);
      expect(titles().at(-1)).toBe('Q00');
    });

    it('AC-002.16 reloads the loaded pages after a delete, so no row is skipped (P2)', async () => {
      await seed(11);
      const view = await openChatbot();
      await screen.findByRole('button', { name: 'Q10' }, { timeout: 5000 });
      await view.user.click(showMore() as HTMLElement);
      await screen.findByRole('button', { name: 'Q00' }, { timeout: 5000 });
      await view.user.click(deleteButton('Q10'));
      await view.user.click(screen.getByRole('button', { name: 'OK' }));
      await waitFor(() => expect(titles()).toHaveLength(11), { timeout: 5000 });
      expect(titles()[1]).toBe('Q09');
      expect(titles().at(-1)).toBe('Q00');
      expect(new Set(titles()).size).toBe(11);
    });
  });
});
