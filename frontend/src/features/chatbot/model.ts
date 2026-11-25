import type { ChatbotConversationTurn } from '@/shared/api';

/** One question of a conversation, the answer it got and the user's rating of it, if any (STORY-003). */
export type ChatTurn = Omit<ChatbotConversationTurn, 'rating'> & {
  rating?: NonNullable<ChatbotConversationTurn['rating']>;
};

/** SRS 12.1: a question has at most 1,000 characters. */
export const QUESTION_MAX_LENGTH = 1000;
