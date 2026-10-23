import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { setDevUser } from '@/shared/auth';
import type { ChatbotAnswer, ChatbotQuestion } from '@/shared/api';
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

function answerWith(answer: Partial<ChatbotAnswer>) {
  server.use(
    http.post('*/api/v1/chatbot/answers', () =>
      HttpResponse.json<ChatbotAnswer>({
        answerId: '00000000-0000-4000-8000-000000000001',
        outcome: 'ANSWERED',
        text: '',
        citations: [],
        ...answer,
      }),
    ),
  );
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

describe('KnowledgeChatbotPage', () => {
  beforeAll(async () => {
    await import('./KnowledgeChatbotPage');
  });

  afterEach(() => {
    server.events.removeAllListeners();
  });

  it.each([
    ['an Employee', { role: 'EMPLOYEE' }],
    ['a Project Manager who is also a Knowledge Manager', { user: 'pm-km' }],
  ] as const)(
    'AC-001.1 shows the thread, the question box and Send to %s',
    async (_who, options) => {
      await openChatbot(options);
      expect(thread()).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Send' })).toBeInTheDocument();
    },
  );

  it('AC-001.2 sends the question when Enter is pressed, and adds a line with Shift+Enter', async () => {
    const sent = recordQuestions();
    const { user, box } = await openChatbot();

    await user.type(box, 'VPN{Shift>}{Enter}{/Shift}from home');
    expect(box).toHaveValue('VPN\nfrom home');
    expect(sent).toHaveLength(0);

    await user.keyboard('{Enter}');
    await waitFor(() => expect(sent).toHaveLength(1));
    expect(sent[0]?.question).toBe('VPN\nfrom home');
  });

  it('AC-001.3 sends the question when Send is clicked', async () => {
    const sent = recordQuestions();
    const { user, box } = await openChatbot();

    await user.type(box, VPN_QUESTION);
    await user.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => expect(sent).toHaveLength(1));
    expect(sent[0]).toEqual({ question: VPN_QUESTION, history: [] });
  });

  it('AC-001.4 shows MSG01 and sends nothing when the question is only white space', async () => {
    const sent = recordQuestions();
    const { user, box } = await openChatbot();

    await user.type(box, '   ');
    await user.click(screen.getByRole('button', { name: 'Send' }));

    expect(await screen.findByText(msg('MSG01', { field_name: 'Question' }))).toBeInTheDocument();
    expect(sent).toHaveLength(0);
  });

  it('AC-001.5 counts up to 1,000 characters and shows MSG02, sending nothing, above it', async () => {
    const sent = recordQuestions();
    const { user, box } = await openChatbot();
    expect(screen.getByText('0 / 1000')).toBeInTheDocument();

    await user.click(box);
    await user.paste('a'.repeat(1001));
    await user.click(screen.getByRole('button', { name: 'Send' }));

    expect(
      await screen.findByText(msg('MSG02', { field_name: 'Question', max_length: 1000 })),
    ).toBeInTheDocument();
    expect(sent).toHaveLength(0);
  });

  it('AC-001.5 sends a question of exactly 1,000 characters', async () => {
    const sent = recordQuestions();
    const { user, box } = await openChatbot();

    await user.click(box);
    await user.paste('a'.repeat(1000));
    await user.keyboard('{Enter}');

    await waitFor(() => expect(sent).toHaveLength(1));
    expect(sent[0]?.question).toHaveLength(1000);
  });

  it('AC-001.6 sends one question when Enter is pressed twice quickly', async () => {
    const sent = recordQuestions();
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}{Enter}`);

    await within(thread()).findByText(/Install GlobalProtect/);
    expect(sent).toHaveLength(1);
  });

  it('AC-001.6 shows the question with a loading answer and disables Send until the answer arrives', async () => {
    const release = holdAnswer({ text: 'Use GlobalProtect [1].' });
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);

    expect(await within(thread()).findByText(VPN_QUESTION)).toBeInTheDocument();
    expect(within(thread()).getByTestId('pending-answer')).toHaveAttribute('aria-busy', 'true');
    expect(screen.getByRole('button', { name: /Send/ })).toBeDisabled();

    release();
    expect(await within(thread()).findByText('Use GlobalProtect [1].')).toBeInTheDocument();
    expect(within(thread()).queryByTestId('pending-answer')).not.toBeInTheDocument();
    // jsdom never ends antd's fade-out of the loading icon, so its "loading" label stays in the button's name.
    expect(screen.getByRole('button', { name: /Send/ })).toBeEnabled();
  });

  it('AC-001.6 sends the earlier turns of the conversation with a follow-up question', async () => {
    const sent = recordQuestions();
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);
    await within(thread()).findByText(/Install GlobalProtect/);
    await user.type(box, 'And on a Mac?{Enter}');

    await waitFor(() => expect(sent).toHaveLength(2));
    expect(sent[1]?.question).toBe('And on a Mac?');
    expect(sent[1]?.history).toHaveLength(1);
    expect(sent[1]?.history[0]?.question).toBe(VPN_QUESTION);
    expect(sent[1]?.history[0]?.answer).toMatch(/^Install GlobalProtect/);
  });

  it('AC-001.7 shows the answer text with its numbered markers and empties the question box', async () => {
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);

    const answer = await within(thread()).findByText(/Install GlobalProtect/);
    expect(answer).toHaveTextContent('with your company account [1].');
    expect(answer).toHaveTextContent('Power Options [2].');
    expect(box).toHaveValue('');
  });

  it('AC-001.7 keeps a question typed while the answer was written', async () => {
    const release = holdAnswer({ text: 'Use GlobalProtect [1].' });
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);
    await within(thread()).findByTestId('pending-answer');
    await user.clear(box);
    await user.type(box, 'And on a Mac?');
    release();

    expect(await within(thread()).findByText('Use GlobalProtect [1].')).toBeInTheDocument();
    expect(box).toHaveValue('And on a Mac?');
  });

  it('AC-001.7 lists the citations under the answer in number order', async () => {
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);

    const citations = await within(thread()).findByRole('list', { name: 'Citations' });
    const items = within(citations).getAllByRole('listitem');
    expect(items.map((item) => item.textContent?.slice(0, 3))).toEqual(['[1]', '[2]']);
  });

  it('AC-001.8 shows HTML in a citation as plain text', async () => {
    answerWith({
      text: 'See [1] and [2].',
      citations: [
        { number: 1, type: 'WIKI_DOCUMENT', wikiDocumentId: 9001, title: '<b>VPN</b> guide' },
        {
          number: 2,
          type: 'TICKET_SOLUTION',
          solution: {
            symptoms: '<script>window.hacked = true</script>',
            rootCause: 'Cause',
            resolutionSteps: 'Steps',
            workaround: null,
            scopeOfApplicability: 'Scope',
          },
        },
      ],
    });
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);
    await user.click(await within(thread()).findByRole('button', { name: 'Show' }));

    expect(within(thread()).getByRole('link', { name: /<b>VPN<\/b> guide/ })).toBeInTheDocument();
    expect(within(thread()).getByText('<script>window.hacked = true</script>')).toBeInTheDocument();
    expect(thread().querySelector('b, script')).toBeNull();
  });

  it('AC-001.9 shows a Wiki citation as a link that opens the Wiki Document in the same tab', async () => {
    const { user, box, router } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);
    const link = await within(thread()).findByRole('link', {
      name: 'VPN setup on Windows 11 · Wiki document',
    });
    expect(link).toHaveAttribute('href', '/wiki/documents/9001');
    expect(link).not.toHaveAttribute('target');

    await user.click(link);
    await waitFor(() => expect(router.state.location.pathname).toBe('/wiki/documents/9001'));
  });

  it('AC-001.10 shows a ticket citation as "Solution from a ticket" with no ticket details', async () => {
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);

    const citations = await within(thread()).findByRole('list', { name: 'Citations' });
    const [, ticket] = within(citations).getAllByRole('listitem');
    expect(ticket).toHaveTextContent(/^\[2\]\s*Solution from a ticket\s*Show$/);
    expect(ticket?.querySelector('a')).toBeNull();
  });

  it('AC-001.11 expands the Solution inline with Show and collapses it with Hide', async () => {
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);
    await user.click(await within(thread()).findByRole('button', { name: 'Show' }));

    for (const section of [
      'Symptoms',
      'Root cause',
      'Resolution steps',
      'Scope of applicability',
    ]) {
      expect(within(thread()).getByText(section)).toBeInTheDocument();
    }
    expect(
      within(thread()).getByText('USB selective suspend turns off the network adapter.'),
    ).toBeInTheDocument();
    expect(within(thread()).queryByText('Workaround')).not.toBeInTheDocument();

    await user.click(within(thread()).getByRole('button', { name: 'Hide' }));
    expect(within(thread()).queryByText('Symptoms')).not.toBeInTheDocument();
    expect(within(thread()).getByRole('button', { name: 'Show' })).toBeInTheDocument();
  });

  it('AC-001.11 shows the Workaround section when the Solution has one', async () => {
    const { user, box } = await openChatbot();

    await user.type(box, 'Leave policy for public holidays{Enter}');
    await user.click(await within(thread()).findByRole('button', { name: 'Show' }));

    expect(within(thread()).getByText('Workaround')).toBeInTheDocument();
    expect(
      within(thread()).getByText('HR adds the day back to the balance by hand.'),
    ).toBeInTheDocument();
  });

  it('AC-002.9 shows MSG110 in place of the link of a Wiki document that is no longer available', async () => {
    const { user, box } = await openChatbot();

    await user.type(box, 'How do I file an expense claim?{Enter}');

    expect(await within(thread()).findByText(msg('MSG110'))).toBeInTheDocument();
    expect(
      within(thread()).queryByRole('link', { name: /Expense claim guide/ }),
    ).not.toBeInTheDocument();
  });

  it('AC-002.10 keeps the Wiki links and shows no message when the availability check fails', async () => {
    server.use(http.get('*/api/v1/chatbot/unavailable-documents', () => problem(500)));
    const { user, box } = await openChatbot();

    await user.type(box, 'How do I file an expense claim?{Enter}');

    expect(
      await within(thread()).findByRole('link', { name: 'Expense claim guide · Wiki document' }),
    ).toBeInTheDocument();
    expect(screen.queryByText(msg('MSG110'))).not.toBeInTheDocument();
    expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
  });

  it('AC-001.8 shows HTML in an answer as plain text', async () => {
    answerWith({ text: '<b>Bold</b> <script>window.hacked = true</script> [1]' });
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);

    expect(
      await within(thread()).findByText('<b>Bold</b> <script>window.hacked = true</script> [1]'),
    ).toBeInTheDocument();
    expect(thread().querySelector('b, script')).toBeNull();
  });

  it('AC-001.12 shows MSG109 with "Jira" and no citations when no source is found', async () => {
    const { user, box } = await openChatbot();

    await user.type(box, 'Who won the football match yesterday?{Enter}');

    const answer = await within(thread()).findByText(msg('MSG109'));
    expect(answer).toHaveTextContent('create an issue in Jira');
    expect(within(thread()).queryByRole('list', { name: 'Citations' })).not.toBeInTheDocument();
  });

  it.each([
    ['the AI module is unavailable (503)', () => problem(503, 'MSG06')],
    ['the network fails', () => HttpResponse.error()],
  ])(
    'AC-001.13 shows MSG06, keeps the question in the box and adds nothing when %s',
    async (_why, failure) => {
      server.use(http.post('*/api/v1/chatbot/answers', failure));
      const { user, box } = await openChatbot();

      await user.type(box, `${VPN_QUESTION}{Enter}`);

      expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
      expect(box).toHaveValue(VPN_QUESTION);
      expect(within(thread()).queryByText(VPN_QUESTION)).not.toBeInTheDocument();
      expect(within(thread()).queryByTestId('pending-answer')).not.toBeInTheDocument();
    },
  );

  it('AC-001.13 leaves a 401 to the sign-out (MSG07) and shows no MSG06', async () => {
    const { user, box } = await openChatbot();
    // The session expires on the server: every request now answers 401.
    setDevUser(null);
    server.use(http.post('*/api/v1/chatbot/answers', () => problem(401)));

    await user.type(box, `${VPN_QUESTION}{Enter}`);

    expect(await screen.findByRole('dialog')).toHaveTextContent(msg('MSG07'));
    expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
  });

  it('AC-001.13 shows MSG06 for a field error on another field than the question (BUG-006)', async () => {
    server.use(
      http.post('*/api/v1/chatbot/answers', () =>
        problem(400, 'MSG02', {
          fieldErrors: [
            { field: 'history', code: 'MSG02', params: { field_name: 'History', max_length: 10 } },
          ],
        }),
      ),
    );
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);

    expect(await screen.findByText(msg('MSG06'))).toBeInTheDocument();
    expect(box).toHaveValue(VPN_QUESTION);
  });

  it('AC-001.14 shows a field error from the server under the box and keeps the question', async () => {
    server.use(
      http.post('*/api/v1/chatbot/answers', () =>
        problem(400, 'MSG02', {
          fieldErrors: [
            {
              field: 'question',
              code: 'MSG02',
              params: { field_name: 'Question', max_length: 1000 },
            },
          ],
        }),
      ),
    );
    const { user, box } = await openChatbot();

    await user.type(box, `${VPN_QUESTION}{Enter}`);

    expect(
      await screen.findByText(msg('MSG02', { field_name: 'Question', max_length: 1000 })),
    ).toBeInTheDocument();
    expect(box).toHaveValue(VPN_QUESTION);
    expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument();
  });
});
