import type { ChatbotAnswer, ChatbotCitation } from '@/shared/api';
import type { Conversation } from '../model';

/** Spec AC-002.7: under the `tsolve.session.` prefix, so logout and a global 401 remove it (fe-foundation). */
export const CONVERSATIONS_KEY = 'tsolve.session.chatbot.conversations';

const VERSION = 1;

/** The fields the citation list renders; a stored citation without them would crash it. */
function isCitation(value: unknown): value is ChatbotCitation {
  const c = value as Record<string, unknown> | null;
  if (typeof c !== 'object' || c === null || typeof c.number !== 'number') return false;
  if (c.type === 'WIKI_DOCUMENT') {
    return typeof c.wikiDocumentId === 'number' && typeof c.title === 'string';
  }
  return c.type === 'TICKET_SOLUTION' && typeof c.solution === 'object' && c.solution !== null;
}

function isAnswer(value: unknown): value is ChatbotAnswer {
  const answer = value as Partial<ChatbotAnswer> | null;
  return (
    typeof answer === 'object' &&
    answer !== null &&
    typeof answer.answerId === 'string' &&
    typeof answer.text === 'string' &&
    (answer.outcome === 'ANSWERED' || answer.outcome === 'NO_SOURCE') &&
    Array.isArray(answer.citations) &&
    answer.citations.every(isCitation)
  );
}

function isConversation(value: unknown): value is Conversation {
  const c = value as Partial<Conversation> | null;
  return (
    typeof c === 'object' &&
    c !== null &&
    typeof c.id === 'string' &&
    typeof c.startedAt === 'number' &&
    Array.isArray(c.turns) &&
    c.turns.length > 0 &&
    c.turns.every(
      (turn) =>
        typeof turn === 'object' &&
        turn !== null &&
        typeof turn.question === 'string' &&
        isAnswer(turn.answer),
    )
  );
}

/** The stored conversations of this user; another user's, broken, older or blocked data gives [] (AC-002.8). */
export function readConversations(userId: number): Conversation[] {
  try {
    const raw = sessionStorage.getItem(CONVERSATIONS_KEY);
    if (raw === null) return [];
    const stored: unknown = JSON.parse(raw);
    const data = stored as { version?: unknown; userId?: unknown; conversations?: unknown } | null;
    if (
      data?.version === VERSION &&
      data.userId === userId &&
      Array.isArray(data.conversations) &&
      data.conversations.every(isConversation)
    ) {
      return data.conversations;
    }
    sessionStorage.removeItem(CONVERSATIONS_KEY);
  } catch {
    // Storage blocked or JSON broken: start empty.
  }
  return [];
}

/** Keeps the conversations; when storage is blocked they stay in memory only (AC-002.11). */
export function writeConversations(userId: number, conversations: readonly Conversation[]): void {
  try {
    sessionStorage.setItem(
      CONVERSATIONS_KEY,
      JSON.stringify({ version: VERSION, userId, conversations }),
    );
  } catch {
    // Storage blocked or full.
  }
}
