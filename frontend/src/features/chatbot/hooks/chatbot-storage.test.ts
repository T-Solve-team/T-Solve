import type { ChatbotAnswer } from '@/shared/api';
import type { Conversation } from '../model';
import { CONVERSATIONS_KEY, readConversations, writeConversations } from './chatbot-storage';

const ANSWER: ChatbotAnswer = {
  answerId: '00000000-0000-4000-8000-000000000001',
  outcome: 'ANSWERED',
  text: 'Use the VPN client [1].',
  citations: [],
};

const USER_A = '00000000-0000-4000-8000-000000000003';
const USER_B = '00000000-0000-4000-8000-000000000004';
const DOCUMENT_ID = '00000000-0000-4000-8000-000000009001';

const CONVERSATION: Conversation = {
  id: 'c1',
  startedAt: 1,
  turns: [{ question: 'VPN?', answer: ANSWER }],
};

const stored = (conversations: unknown[]) =>
  JSON.stringify({ version: 2, userId: USER_A, conversations });

/** The conversation with the given (possibly malformed) citations on its answer. */
const withCitations = (citations: unknown[]) => ({
  ...CONVERSATION,
  turns: [{ question: 'VPN?', answer: { ...ANSWER, citations } }],
});

describe('chatbot-storage', () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it('AC-002.7 keeps the conversations under a tsolve.session.chatbot. key as version 2 (AC-002.12) and reads them back', () => {
    writeConversations(USER_A, [CONVERSATION]);

    expect(CONVERSATIONS_KEY.startsWith('tsolve.session.chatbot.')).toBe(true);
    expect(JSON.parse(sessionStorage.getItem(CONVERSATIONS_KEY) ?? '')).toEqual({
      version: 2,
      userId: USER_A,
      conversations: [CONVERSATION],
    });
    expect(readConversations(USER_A)).toEqual([CONVERSATION]);
  });

  it('AC-002.12 drops a version 1 value', () => {
    sessionStorage.setItem(
      CONVERSATIONS_KEY,
      JSON.stringify({ version: 1, userId: USER_A, conversations: [CONVERSATION] }),
    );

    expect(readConversations(USER_A)).toEqual([]);
    expect(sessionStorage.getItem(CONVERSATIONS_KEY)).toBeNull();
  });

  it.each([
    ['a version 2 value', { version: 2, userId: 7, conversations: [CONVERSATION] }],
    [
      'the version 1 value of the old shape',
      { version: 1, userId: 7, conversations: [CONVERSATION] },
    ],
  ])('AC-002.12 drops a numeric user id in %s', (_what, value) => {
    sessionStorage.setItem(CONVERSATIONS_KEY, JSON.stringify(value));

    expect(readConversations(USER_A)).toEqual([]);
    expect(sessionStorage.getItem(CONVERSATIONS_KEY)).toBeNull();
  });

  it.each([
    ['a number', 9001],
    ['null', null],
    ['missing', undefined],
    ['an object', {}],
  ])('AC-002.12 drops a citation whose Wiki document id is %s', (_what, wikiDocumentId) => {
    const citation = { number: 1, type: 'WIKI_DOCUMENT', wikiDocumentId, title: 'VPN' };
    sessionStorage.setItem(CONVERSATIONS_KEY, stored([withCitations([citation])]));

    expect(readConversations(USER_A)).toEqual([]);
    expect(sessionStorage.getItem(CONVERSATIONS_KEY)).toBeNull();
  });

  it('AC-002.12 keeps a citation whose Wiki document id is a UUID string', () => {
    const citation = {
      number: 1,
      type: 'WIKI_DOCUMENT',
      wikiDocumentId: DOCUMENT_ID,
      title: 'VPN',
    };
    sessionStorage.setItem(CONVERSATIONS_KEY, stored([withCitations([citation])]));

    expect(readConversations(USER_A)).toEqual([withCitations([citation])]);
  });

  it('AC-002.12 keeps a ticket-solution citation in a version 2 value', () => {
    const citation = {
      number: 1,
      type: 'TICKET_SOLUTION',
      solution: {
        symptoms: 's',
        rootCause: 'r',
        resolutionSteps: 'x',
        workaround: null,
        scopeOfApplicability: 'a',
      },
    };
    sessionStorage.setItem(CONVERSATIONS_KEY, stored([withCitations([citation])]));

    expect(readConversations(USER_A)).toEqual([withCitations([citation])]);
  });

  it('AC-002.8 drops conversations that belong to another user (edge 12)', () => {
    writeConversations(USER_A, [CONVERSATION]);

    expect(readConversations(USER_B)).toEqual([]);
    expect(sessionStorage.getItem(CONVERSATIONS_KEY)).toBeNull();
  });

  it.each([
    ['broken JSON', '{not json'],
    ['an older version', JSON.stringify({ version: 0, userId: USER_A, conversations: [] })],
    ['a wrong shape', JSON.stringify({ version: 2, userId: USER_A, conversations: [{ id: 1 }] })],
    ['a conversation without turns', stored([{ ...CONVERSATION, turns: [] }])],
    [
      'a citation of a wrong shape',
      stored([withCitations([{ number: 1, type: 'WIKI_DOCUMENT' }])]),
    ],
    ['a citation of an unknown type', stored([withCitations([{ number: 1, type: 'OTHER' }])])],
  ])('AC-002.8 drops %s (edge 13)', (_what, stored) => {
    sessionStorage.setItem(CONVERSATIONS_KEY, stored);

    expect(readConversations(USER_A)).toEqual([]);
  });

  it('AC-003.4 keeps a rating with its turn and reads it back', () => {
    const rated: Conversation = {
      ...CONVERSATION,
      turns: [{ question: 'VPN?', answer: ANSWER, rating: 'NOT_HELPFUL' }],
    };
    writeConversations(USER_A, [rated]);

    expect(readConversations(USER_A)).toEqual([rated]);
  });

  it('AC-003.4 reads a turn that has no rating', () => {
    sessionStorage.setItem(CONVERSATIONS_KEY, stored([CONVERSATION]));

    expect(readConversations(USER_A)).toEqual([CONVERSATION]);
  });

  it.each(['GREAT', 1, null])(
    'AC-003.4 drops a stored turn with the unknown rating %j (edge 26)',
    (rating) => {
      const turn = { question: 'VPN?', answer: ANSWER, rating };
      sessionStorage.setItem(CONVERSATIONS_KEY, stored([{ ...CONVERSATION, turns: [turn] }]));

      expect(readConversations(USER_A)).toEqual([]);
    },
  );

  it('AC-002.11 does not throw when storage is blocked (edge 14)', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });

    expect(readConversations(USER_A)).toEqual([]);
    expect(() => writeConversations(USER_A, [CONVERSATION])).not.toThrow();
  });
});
