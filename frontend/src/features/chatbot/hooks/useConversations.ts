import { useState } from 'react';
import type { ChatTurn } from '../model';

/**
 * The conversation on screen, kept in memory. Local state: it belongs to this screen only (guideline 05 C8).
 * The session's conversation list and its storage come with KAN-72 (spec STORY-002).
 */
export function useConversations() {
  const [turns, setTurns] = useState<ChatTurn[]>([]);

  function addTurn(turn: ChatTurn) {
    setTurns((current) => [...current, turn]);
  }

  return { turns, addTurn };
}
