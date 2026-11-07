import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { getGetReviewQueueQueryKey, getGetReviewQueueTicketIdsQueryKey } from '@/shared/api';

const EVENTS_URL = '/api/v1/review-queue/events';

/**
 * Keeps the Review Queue fresh from the server-sent event stream. `connected` is true while the stream is
 * open; the queue uses a slow safety refetch then and falls back to 5 s polling when the stream fails.
 * EventSource is not fetch, so it lives here, same-origin, with the session cookie.
 */
export function useReviewQueueEvents(): { connected: boolean } {
  const queryClient = useQueryClient();
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    if (import.meta.env.DEV && import.meta.env.VITE_API_MOCKING === 'true') return;
    if (typeof EventSource === 'undefined') return;
    const source = new EventSource(EVENTS_URL, { withCredentials: true });
    const refresh = () => {
      void queryClient.invalidateQueries({ queryKey: getGetReviewQueueQueryKey() });
      void queryClient.invalidateQueries({ queryKey: getGetReviewQueueTicketIdsQueryKey() });
    };
    const onOpen = () => {
      setConnected(true);
      refresh();
    };
    const onError = () => setConnected(false);
    source.addEventListener('open', onOpen);
    source.addEventListener('queue-changed', refresh);
    source.addEventListener('error', onError);
    return () => {
      source.removeEventListener('open', onOpen);
      source.removeEventListener('queue-changed', refresh);
      source.removeEventListener('error', onError);
      source.close();
    };
  }, [queryClient]);

  return { connected };
}
