import type { ChatbotAnswer, ChatbotTurn } from '@/shared/api';

/** One question of a conversation and the answer it got. */
export type ChatTurn = { question: string; answer: ChatbotAnswer };

/** SRS 12.1: a question has at most 1,000 characters. */
export const QUESTION_MAX_LENGTH = 1000;

/** Spec AC-002.5: a question is sent with at most the last 10 turns of its conversation. */
export const HISTORY_LIMIT = 10;

/** The earlier turns sent with a question: the last 10, oldest first, text only (NO_SOURCE → ''). */
export function toHistory(turns: readonly ChatTurn[]): ChatbotTurn[] {
  return turns.slice(-HISTORY_LIMIT).map(({ question, answer }) => ({
    question,
    answer: answer.outcome === 'ANSWERED' ? answer.text : '',
  }));
}

/** A conversation of the session; `startedAt` (ms) orders the list newest first (AC-002.3). */
export type Conversation = { id: string; startedAt: number; turns: ChatTurn[] };
