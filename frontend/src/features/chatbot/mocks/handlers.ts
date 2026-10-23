import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type { ChatbotAnswer, ChatbotQuestion } from '@/shared/api';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { QUESTION_MAX_LENGTH } from '../model';
import { findMockAnswer } from './data';

/**
 * MSW mock handlers of the "chatbot" feature (guideline 10 §5). Hand-written handlers with realistic data go here;
 * endpoints without one fall back to orval's generated fake-data handlers (src/mocks/handlers.ts).
 */
export const handlers: HttpHandler[] = [
  http.post('*/api/v1/chatbot/answers', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const { question } = (await request.json()) as ChatbotQuestion;
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
    const match = findMockAnswer(question);
    return HttpResponse.json<ChatbotAnswer>({
      answerId: crypto.randomUUID(),
      outcome: match ? 'ANSWERED' : 'NO_SOURCE',
      text: match?.text ?? '',
      citations: match?.citations ?? [],
    });
  }),
];

/** Restores this feature's mock data to its starting state — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  // The answers never change; the unavailable Wiki documents arrive with the citations (KAN-73).
}
