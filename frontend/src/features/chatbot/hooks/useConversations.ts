import { useState } from 'react';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import {
  getGetChatbotConversationQueryKey,
  getListChatbotConversationsQueryKey,
  useDeleteChatbotConversation,
  useGetChatbotConversation,
  listChatbotConversations,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

const PAGE_SIZE = 10; // KAN-382: "Show more" adds 10 (AC-002.3, AC-002.16)

/** Server conversations stay in the query cache; null selects the unsaved empty conversation. */
export function useConversations() {
  const client = useQueryClient();
  const [openId, setOpenId] = useState<string | null>(null);
  // AC-002.16: pages append; an ask or a delete invalidates this key's prefix, so every loaded page is refetched.
  const list = useInfiniteQuery({
    queryKey: getListChatbotConversationsQueryKey({ size: PAGE_SIZE }),
    queryFn: ({ pageParam, signal }) =>
      listChatbotConversations({ page: pageParam, size: PAGE_SIZE }, { signal }),
    initialPageParam: 0,
    getNextPageParam: ({ page }) =>
      page.number + 1 < page.totalPages ? page.number + 1 : undefined,
  });
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
    conversations: list.data?.pages.flatMap((page) => page.content) ?? [],
    loadMore: async () => {
      if (!(await list.fetchNextPage()).isFetchNextPageError) return;
      showMessage('MSG06');
    },
    turns:
      conversation.data?.turns.map((turn) => ({ ...turn, rating: turn.rating ?? undefined })) ?? [],
    open: setOpenId,
    startNew: () => setOpenId(null),
  };
}
