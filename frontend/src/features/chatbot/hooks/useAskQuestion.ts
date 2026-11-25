import { useQueryClient } from '@tanstack/react-query';
import {
  type ChatbotConversation,
  getGetChatbotConversationQueryKey,
  getListChatbotConversationsQueryKey,
  useAskChatbot,
} from '@/shared/api';

/** Ask the Knowledge Chatbot; field errors and MSG06 are handled by the question form. */
export function useAskQuestion() {
  const client = useQueryClient();
  return useAskChatbot({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: async (answer, { data: { question } }) => {
        if (!answer.chatSessionId) return;
        const key = getGetChatbotConversationQueryKey(answer.chatSessionId);
        await client.cancelQueries({ queryKey: key });
        // Show the confirmed answer immediately, including if the refresh is slow or unavailable.
        client.setQueryData<ChatbotConversation>(key, (current) => ({
          chatSessionId: answer.chatSessionId ?? '',
          title: current?.title ?? question,
          startedAt: current?.startedAt ?? new Date().toISOString(),
          turns: [...(current?.turns ?? []), { question, answer, rating: null }],
        }));
        void client.invalidateQueries({ queryKey: getListChatbotConversationsQueryKey() });
        void client.invalidateQueries({ queryKey: key });
      },
    },
  });
}
