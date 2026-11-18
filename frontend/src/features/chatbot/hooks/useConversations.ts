import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetChatbotConversationQueryKey,
  getListChatbotConversationsQueryKey,
  useDeleteChatbotConversation,
  useGetChatbotConversation,
  useListChatbotConversations,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

/** Server conversations stay in the query cache; null selects the unsaved empty conversation. */
export function useConversations() {
  const client = useQueryClient();
  const [openId, setOpenId] = useState<string | null>(null);
  const list = useListChatbotConversations();
  const conversation = useGetChatbotConversation(openId ?? '', {
    query: { enabled: openId !== null, staleTime: 0 },
  });
  const remove = useDeleteChatbotConversation({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: async (_, { chatSessionId }) => {
        if (openId === chatSessionId) setOpenId(null);
        client.removeQueries({ queryKey: getGetChatbotConversationQueryKey(chatSessionId) });
        await client.invalidateQueries({ queryKey: getListChatbotConversationsQueryKey() });
      },
      onError: (error) => {
        if (error.status !== 401) showMessage('MSG06');
      },
    },
  });

  return {
    list,
    conversation,
    remove,
    openId,
    conversations: list.data ?? [],
    turns:
      conversation.data?.turns.map((turn) => ({ ...turn, rating: turn.rating ?? undefined })) ?? [],
    open: setOpenId,
    startNew: () => setOpenId(null),
  };
}
