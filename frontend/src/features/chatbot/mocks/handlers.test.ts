import {
  askChatbot,
  isApiError,
  listUnavailableCitedDocuments,
  rateChatbotAnswer,
  removeChatbotAnswerRating,
  type ChatbotAnswer,
} from '@/shared/api';
import { MOCK_ANSWERS, UNAVAILABLE_DOCUMENT_IDS } from './data';
import { resetMockData } from './handlers';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const DOC_9001 = '00000000-0000-4000-8000-000000009001';
const DOC_9003 = '00000000-0000-4000-8000-000000009003';
const DOC_UNKNOWN = '00000000-0000-4000-8000-000000000042';

function ask(question: string): Promise<ChatbotAnswer> {
  return askChatbot({ question, history: [] });
}

async function askError(question: string) {
  try {
    await ask(question);
  } catch (error) {
    if (isApiError(error)) return error;
  }
  throw new Error('expected an ApiError');
}

describe('chatbot mock handlers', () => {
  it.each([
    'How do I connect to the VPN from home?',
    'Leave policy for public holidays',
    'EXPENSE claim',
  ])(
    'AC-004.1 answers "%s" with numbered markers, a Wiki and a ticket Solution citation',
    async (question) => {
      const answer = await ask(question);

      expect(answer.outcome).toBe('ANSWERED');
      expect(answer.text).toMatch(/\[1\]/);
      expect(answer.citations.map((c) => c.number)).toEqual([1, 2]);
      expect(answer.citations.map((c) => c.type)).toEqual(['WIKI_DOCUMENT', 'TICKET_SOLUTION']);
    },
  );

  it('AC-004.2 answers NO_SOURCE with no citations when no keyword matches', async () => {
    const answer = await ask('Who won the football match yesterday?');

    expect(answer).toMatchObject({ outcome: 'NO_SOURCE', text: '', citations: [] });
  });

  it('AC-004.3 answers 400 with MSG01 on question when the question is blank', async () => {
    const error = await askError('   ');

    expect(error.status).toBe(400);
    expect(error.fieldErrors).toEqual([
      { field: 'question', code: 'MSG01', params: { field_name: 'Question' } },
    ]);
  });

  it('AC-004.3 answers 400 with MSG02 on question when the question is longer than 1,000 characters', async () => {
    const error = await askError('a'.repeat(1001));

    expect(error.status).toBe(400);
    expect(error.fieldErrors).toEqual([
      { field: 'question', code: 'MSG02', params: { field_name: 'Question', max_length: 1000 } },
    ]);
  });

  it('AC-004.4 lists only the asked Wiki documents that are marked unavailable', async () => {
    const result = await listUnavailableCitedDocuments({
      wikiDocumentIds: [DOC_9001, DOC_9003, DOC_UNKNOWN],
    });

    expect(result.wikiDocumentIds).toEqual([DOC_9003]);
  });

  it('AC-002.12 serves Wiki citation ids as UUID strings', () => {
    const wikiIds = MOCK_ANSWERS.flatMap((a) => a.citations).flatMap((c) =>
      c.type === 'WIKI_DOCUMENT' ? [c.wikiDocumentId] : [],
    );

    expect(wikiIds.length).toBeGreaterThan(0);
    for (const id of [...wikiIds, ...UNAVAILABLE_DOCUMENT_IDS]) {
      expect(typeof id).toBe('string');
      expect(id).toMatch(UUID);
    }
  });

  it('AC-004.6 answers 204 when asked to save a rating', async () => {
    await expect(
      rateChatbotAnswer('00000000-0000-4000-8000-000000000001', { rating: 'HELPFUL' }),
    ).resolves.toBeUndefined();
  });

  it('AC-004.6 answers 204 when asked to remove a rating', async () => {
    await expect(
      removeChatbotAnswerRating('00000000-0000-4000-8000-000000000001'),
    ).resolves.toBeUndefined();
  });

  it('AC-004.5 answers its initial data after resetMockData()', async () => {
    resetMockData();

    expect(await ask('VPN')).toMatchObject({ outcome: 'ANSWERED' });
    expect(
      (await listUnavailableCitedDocuments({ wikiDocumentIds: [DOC_9001, DOC_UNKNOWN, DOC_9003] }))
        .wikiDocumentIds,
    ).toEqual([DOC_9003]);
  });
});
