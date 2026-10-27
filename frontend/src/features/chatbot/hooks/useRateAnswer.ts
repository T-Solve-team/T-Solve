import { useEffect, useRef } from 'react';
import { useRateChatbotAnswer, useRemoveChatbotAnswerRating } from '@/shared/api';
import { showMessage } from '@/shared/messages';
import type { ChatTurn } from '../model';
import { writeRating } from './chatbot-storage';

type Rating = ChatTurn['rating'];

/**
 * Save or remove the rating of an answer (UC-53, spec STORY-003). `setRating` runs once the server has answered 204;
 * any error but a 401 (the global handler signs the user out, MSG07) shows MSG06 and changes nothing (AC-003.5).
 */
export function useRateAnswer(
  userId: string,
  setRating: (conversationId: string, answerId: string, rating: Rating) => void,
) {
  // Set at once, unlike isPending: a second click before the next render must not send again (edge 22).
  const busy = useRef(false);
  const target = useRef<{ conversationId: string; answerId: string; rating: Rating }>(null);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  // A hook option, not a `mutate` callback: it also runs when the user left the screen while the request ran. The
  // screen's state is gone then, so the rating goes straight to the session storage (BUG-008).
  const options = {
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: () => {
        const t = target.current;
        if (t === null) return;
        if (mounted.current) setRating(t.conversationId, t.answerId, t.rating);
        else writeRating(userId, t.conversationId, t.answerId, t.rating);
      },
    },
  };
  const save = useRateChatbotAnswer(options);
  const remove = useRemoveChatbotAnswerRating(options);

  const callbacks = {
    onError: (error: { status?: number }) => {
      if (error.status !== 401) showMessage('MSG06');
    },
    onSettled: () => {
      busy.current = false;
    },
  };

  /** `rating` undefined removes the rating. The conversation id is the one open at the click (edge 23). */
  function rate(conversationId: string, answerId: string, rating: Rating) {
    if (busy.current) return;
    busy.current = true;
    target.current = { conversationId, answerId, rating };
    if (rating === undefined) {
      remove.mutate({ answerId }, callbacks);
    } else {
      save.mutate({ answerId, data: { rating } }, callbacks);
    }
  }

  return { rate, isPending: save.isPending || remove.isPending };
}
