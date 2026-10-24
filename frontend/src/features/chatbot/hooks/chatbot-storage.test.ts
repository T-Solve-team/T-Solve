import type { ChatbotAnswer } from '@/shared/api';
import type { Conversation } from '../model';
import { CONVERSATIONS_KEY, readConversations, writeConversations } from './chatbot-storage';

const ANSWER: ChatbotAnswer = {
  answerId: '00000000-0000-4000-8000-000000000001',
  outcome: 'ANSWERED',
  text: 'Use the VPN client [1].',
  citations: [],
};

const CONVERSATION: Conversation = {
  id: 'c1',
  startedAt: 1,
  turns: [{ question: 'VPN?', answer: ANSWER }],
};

const stored = (conversations: unknown[]) =>
  JSON.stringify({ version: 1, userId: 7, conversations });

/** The conversation with the given (possibly malformed) citations on its answer. */
const withCitations = (citations: unknown[]) => ({
  ...CONVERSATION,
  turns: [{ question: 'VPN?', answer: { ...ANSWER, citations } }],
});

describe('chatbot-storage', () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it('AC-002.7 keeps the conversations under a tsolve.session.chatbot. key and reads them back', () => {
    writeConversations(7, [CONVERSATION]);

    expect(CONVERSATIONS_KEY.startsWith('tsolve.session.chatbot.')).toBe(true);
    expect(JSON.parse(sessionStorage.getItem(CONVERSATIONS_KEY) ?? '')).toEqual({
      version: 1,
      userId: 7,
      conversations: [CONVERSATION],
    });
    expect(readConversations(7)).toEqual([CONVERSATION]);
  });

  it('AC-002.8 drops conversations that belong to another user (edge 12)', () => {
    writeConversations(7, [CONVERSATION]);

    expect(readConversations(8)).toEqual([]);
    expect(sessionStorage.getItem(CONVERSATIONS_KEY)).toBeNull();
  });

  it.each([
    ['broken JSON', '{not json'],
    ['an older version', JSON.stringify({ version: 0, userId: 7, conversations: [] })],
    ['a wrong shape', JSON.stringify({ version: 1, userId: 7, conversations: [{ id: 1 }] })],
    ['a conversation without turns', stored([{ ...CONVERSATION, turns: [] }])],
    [
      'a citation of a wrong shape',
      stored([withCitations([{ number: 1, type: 'WIKI_DOCUMENT' }])]),
    ],
    ['a citation of an unknown type', stored([withCitations([{ number: 1, type: 'OTHER' }])])],
  ])('AC-002.8 drops %s (edge 13)', (_what, stored) => {
    sessionStorage.setItem(CONVERSATIONS_KEY, stored);

    expect(readConversations(7)).toEqual([]);
  });

  it('AC-003.4 keeps a rating with its turn and reads it back', () => {
    const rated: Conversation = {
      ...CONVERSATION,
      turns: [{ question: 'VPN?', answer: ANSWER, rating: 'NOT_HELPFUL' }],
    };
    writeConversations(7, [rated]);

    expect(readConversations(7)).toEqual([rated]);
  });

  it('AC-003.4 reads a turn that has no rating', () => {
    sessionStorage.setItem(CONVERSATIONS_KEY, stored([CONVERSATION]));

    expect(readConversations(7)).toEqual([CONVERSATION]);
  });

  it.each(['GREAT', 1, null])(
    'AC-003.4 drops a stored turn with the unknown rating %j (edge 26)',
    (rating) => {
      const turn = { question: 'VPN?', answer: ANSWER, rating };
      sessionStorage.setItem(CONVERSATIONS_KEY, stored([{ ...CONVERSATION, turns: [turn] }]));

      expect(readConversations(7)).toEqual([]);
    },
  );

  it('AC-002.11 does not throw when storage is blocked (edge 14)', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });

    expect(readConversations(7)).toEqual([]);
    expect(() => writeConversations(7, [CONVERSATION])).not.toThrow();
  });
});
