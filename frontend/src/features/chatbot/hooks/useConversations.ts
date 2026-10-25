import { useEffect, useState } from 'react';
import type { ChatTurn, Conversation } from '../model';
import { readConversations, writeConversations } from './chatbot-storage';

/**
 * The conversations of the session (spec STORY-002), kept in `sessionStorage` and in memory. Local state: it belongs
 * to this screen only (guideline 05 C8). The empty "New conversation" is `openId === null` and is never stored.
 */
export function useConversations(userId: string) {
  const [stored, setStored] = useState(() => readConversations(userId));
  const [openId, setOpenId] = useState<string | null>(null);

  useEffect(() => {
    if (stored.length > 0) writeConversations(userId, stored);
  }, [userId, stored]);

  const conversations = [...stored].sort((a, b) => b.startedAt - a.startedAt);
  const open = stored.find((conversation) => conversation.id === openId);

  /** Adds a turn to its conversation, which is created with its first answer. */
  function addTurn(conversationId: string, turn: ChatTurn) {
    setStored((current) =>
      current.some((c) => c.id === conversationId)
        ? current.map((c) => (c.id === conversationId ? { ...c, turns: [...c.turns, turn] } : c))
        : [
            ...current,
            { id: conversationId, startedAt: Date.now(), turns: [turn] } satisfies Conversation,
          ],
    );
    setOpenId(conversationId);
  }

  /** Sets or, with `undefined`, removes the rating of one answer; a conversation or answer that is gone is ignored. */
  function setRating(conversationId: string, answerId: string, rating: ChatTurn['rating']) {
    setStored((current) =>
      current.map((c) =>
        c.id === conversationId
          ? {
              ...c,
              turns: c.turns.map((turn) =>
                turn.answer.answerId === answerId ? { ...turn, rating } : turn,
              ),
            }
          : c,
      ),
    );
  }

  return {
    conversations,
    openId,
    turns: open?.turns ?? [],
    open: setOpenId,
    startNew: () => setOpenId(null),
    addTurn,
    setRating,
  };
}
