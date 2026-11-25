import { useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
  type ChatbotConversation,
  getGetChatbotConversationQueryKey,
  useRateChatbotAnswer,
  useRemoveChatbotAnswerRating,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';
import type { ChatTurn } from '../model';

type Rating = ChatTurn['rating'];

/** Write a confirmed rating to its own cached conversation, also after leaving the screen (BUG-008). */
export function useRateAnswer() {
  const client = useQueryClient();
  const busy = useRef(false);
  const target = useRef<{ conversationId: string; answerId: string; rating: Rating }>(null);
  const options = {
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: async () => {
        const t = target.current;
        if (!t) return;
        const key = getGetChatbotConversationQueryKey(t.conversationId);
        await client.cancelQueries({ queryKey: key });
        client.setQueryData<ChatbotConversation>(key, (current) =>
          current
            ? {
                ...current,
                turns: current.turns.map((turn) =>
                  turn.answer.answerId === t.answerId
                    ? { ...turn, rating: t.rating ?? null }
                    : turn,
                ),
              }
            : undefined,
        );
      },
      onError: (error: { status?: number }) => {
        if (error.status !== 401) showMessage('MSG06');
      },
      onSettled: () => {
        busy.current = false;
      },
    },
  };
  const save = useRateChatbotAnswer(options);
  const remove = useRemoveChatbotAnswerRating(options);

  function rate(conversationId: string, answerId: string, rating: Rating) {
    if (busy.current) return;
    busy.current = true;
    target.current = { conversationId, answerId, rating };
    if (rating === undefined) remove.mutate({ answerId });
    else save.mutate({ answerId, data: { rating } });
  }
  return { rate, isPending: save.isPending || remove.isPending };
}
