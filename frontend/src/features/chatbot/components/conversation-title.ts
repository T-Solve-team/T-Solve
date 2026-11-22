import type { ChatTurn } from '../model';

export const NEW_CONVERSATION_TITLE = 'New conversation';

/** AC-002.2, AC-002.15: use the first answer's title when available. */
export function getConversationTitle(turns: readonly ChatTurn[], pendingQuestion?: string): string {
  const first = turns[0];
  return first?.answer.title ?? first?.question ?? pendingQuestion ?? NEW_CONVERSATION_TITLE;
}
