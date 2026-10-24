import { useRef } from 'react';
import { useRateChatbotAnswer, useRemoveChatbotAnswerRating } from '@/shared/api';
import { showMessage } from '@/shared/messages';
import type { ChatTurn } from '../model';

type Rating = ChatTurn['rating'];

/**
 * Save or remove the rating of an answer (UC-53, spec STORY-003). `setRating` runs once the server has answered 204;
 * any error but a 401 (the global handler signs the user out, MSG07) shows MSG06 and changes nothing (AC-003.5).
 */
export function useRateAnswer(
  setRating: (conversationId: string, answerId: string, rating: Rating) => void,
) {
  const options = { mutation: { meta: { handlesOwnErrors: true } } };
  const save = useRateChatbotAnswer(options);
  const remove = useRemoveChatbotAnswerRating(options);
  // Set at once, unlike isPending: a second click before the next render must not send again (edge 22).
  const busy = useRef(false);

  const callbacks = (conversationId: string, answerId: string, rating: Rating) => ({
    onSuccess: () => setRating(conversationId, answerId, rating),
    onError: (error: { status?: number }) => {
      if (error.status !== 401) showMessage('MSG06');
    },
    onSettled: () => {
      busy.current = false;
    },
  });

  /** `rating` undefined removes the rating. The conversation id is the one open at the click (edge 23). */
  function rate(conversationId: string, answerId: string, rating: Rating) {
    if (busy.current) return;
    busy.current = true;
    if (rating === undefined) {
      remove.mutate({ answerId }, callbacks(conversationId, answerId, rating));
    } else {
      save.mutate({ answerId, data: { rating } }, callbacks(conversationId, answerId, rating));
    }
  }

  return { rate, isPending: save.isPending || remove.isPending };
}
