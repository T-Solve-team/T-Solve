import type { ChatbotAnswer, ChatbotRating } from '@/shared/api';

/** One question of a conversation, the answer it got and the user's rating of it, if any (STORY-003). */
export type ChatTurn = {
  question: string;
  answer: ChatbotAnswer;
  rating?: ChatbotRating['rating'];
};

/** SRS 12.1: a question has at most 1,000 characters. */
export const QUESTION_MAX_LENGTH = 1000;

/** A conversation of the session; `startedAt` (ms) orders the list newest first (AC-002.3). */
export type Conversation = { id: string; startedAt: number; turns: ChatTurn[] };
