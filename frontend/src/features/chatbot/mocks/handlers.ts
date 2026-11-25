import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type {
  ChatbotAnswer,
  ChatbotAsk,
  ChatbotConversation,
  ChatbotConversationSummaryPage,
  ChatbotRating,
  UnavailableDocuments,
} from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { getDevUser } from '@/shared/auth';
import { QUESTION_MAX_LENGTH } from '../model';
import { classifyMockQuestion, findMockAnswer, UNAVAILABLE_DOCUMENT_IDS } from './data';

const users = new Map<string | null, Map<string, ChatbotConversation>>();
function userConversations() {
  const user = getDevUser();
  let conversations = users.get(user);
  if (!conversations) {
    conversations = new Map<string, ChatbotConversation>();
    users.set(user, conversations);
  }
  return conversations;
}

/**
 * MSW mock handlers of the "chatbot" feature (guideline 10 §5). Hand-written handlers with realistic data go here;
 * endpoints without one fall back to orval's generated fake-data handlers (src/mocks/handlers.ts).
 */
export const handlers: HttpHandler[] = [
  http.post('*/api/v1/chatbot/answers', async ({ request }) => {
    const conversations = userConversations();
    await delay(MOCK_DELAY_MS);
    const { question, chatSessionId: asked } = (await request.json()) as ChatbotAsk;
    if (question.trim() === '') {
      return problem(400, 'MSG01', {
        fieldErrors: [{ field: 'question', code: 'MSG01', params: { field_name: 'Question' } }],
      });
    }
    if (question.length > QUESTION_MAX_LENGTH) {
      return problem(400, 'MSG02', {
        fieldErrors: [
          {
            field: 'question',
            code: 'MSG02',
            params: { field_name: 'Question', max_length: QUESTION_MAX_LENGTH },
          },
        ],
      });
    }
    if (asked && !conversations.has(asked)) return problem(404, 'MSG04');
    const chatSessionId = asked ?? crypto.randomUUID();
    const outcome = classifyMockQuestion(question);
    const match = findMockAnswer(question);
    const answer: ChatbotAnswer = {
      answerId: crypto.randomUUID(),
      outcome: outcome ?? (match ? 'ANSWERED' : 'NO_SOURCE'),
      text: outcome ? '' : (match?.text ?? ''),
      citations: outcome ? [] : structuredClone(match?.citations ?? []),
    };
    const conversation = conversations.get(chatSessionId) ?? {
      chatSessionId,
      title: question.trim(),
      startedAt: new Date().toISOString(),
      turns: [],
    };
    conversation.turns.push({ question: question.trim(), answer, rating: null });
    conversations.set(chatSessionId, conversation);
    return HttpResponse.json<ChatbotAnswer>({ ...answer, chatSessionId });
  }),

  http.get('*/api/v1/chatbot/conversations', async ({ request }) => {
    const conversations = userConversations();
    await delay(MOCK_DELAY_MS);
    const query = new URL(request.url).searchParams;
    const number = Math.max(0, Number(query.get('page') ?? 0) || 0);
    const size = Math.min(100, Math.max(1, Number(query.get('size') ?? 20) || 20));
    const summaries = [...conversations.values()]
      .reverse()
      .sort((a, b) => b.startedAt.localeCompare(a.startedAt))
      .map(({ chatSessionId, title, startedAt }) => ({ chatSessionId, title, startedAt }));
    return HttpResponse.json<ChatbotConversationSummaryPage>({
      content: summaries.slice(number * size, (number + 1) * size),
      page: {
        size,
        number,
        totalElements: summaries.length,
        totalPages: Math.ceil(summaries.length / size),
      },
    });
  }),

  http.get('*/api/v1/chatbot/conversations/:chatSessionId', async ({ params }) => {
    const conversations = userConversations();
    await delay(MOCK_DELAY_MS);
    const conversation = conversations.get(String(params.chatSessionId));
    return conversation ? HttpResponse.json(conversation) : problem(404, 'MSG04');
  }),

  http.delete('*/api/v1/chatbot/conversations/:chatSessionId', async ({ params }) => {
    const conversations = userConversations();
    await delay(MOCK_DELAY_MS);
    if (!conversations.delete(String(params.chatSessionId))) return problem(404, 'MSG04');
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('*/api/v1/chatbot/unavailable-documents', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const asked = new URL(request.url).searchParams.getAll('wikiDocumentIds');
    return HttpResponse.json<UnavailableDocuments>({
      wikiDocumentIds: asked.filter((id) => UNAVAILABLE_DOCUMENT_IDS.includes(id)),
    });
  }),

  http.put('*/api/v1/chatbot/answers/:answerId/rating', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const { rating } = (await request.json()) as ChatbotRating;
    setRating(String(params.answerId), rating);
    return new HttpResponse(null, { status: 204 });
  }),

  http.delete('*/api/v1/chatbot/answers/:answerId/rating', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    setRating(String(params.answerId), null);
    return new HttpResponse(null, { status: 204 });
  }),
];

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  users.clear();
}

function setRating(answerId: string, rating: ChatbotConversation['turns'][number]['rating']) {
  for (const conversation of userConversations().values()) {
    const turn = conversation.turns.find((turn) => turn.answer.answerId === answerId);
    if (turn) turn.rating = rating;
  }
}
