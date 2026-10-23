import { useAskChatbot } from '@/shared/api';

/**
 * Ask the Knowledge Chatbot (UC-51). The caller shows the errors (spec AC-001.13, AC-001.14): field errors under the
 * question box, MSG06 for everything else — the global handler would show the server's code instead.
 */
export function useAskQuestion() {
  return useAskChatbot({ mutation: { meta: { handlesOwnErrors: true } } });
}
