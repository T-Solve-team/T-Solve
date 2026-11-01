import { useGiveFeedback } from '@/shared/api';
import { showMessage } from '@/shared/messages';

/** Give Feedback (SRS 8.1, UC-28): saves the user's Feedback on a ticket, then MSG26. The popup shows errors. */
export function useSendFeedback() {
  return useGiveFeedback({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: () => showMessage('MSG26'),
    },
  });
}
