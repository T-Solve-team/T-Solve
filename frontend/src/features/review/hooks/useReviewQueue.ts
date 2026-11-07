import { keepPreviousData } from '@tanstack/react-query';
import { useGetReviewQueue, type GetReviewQueueParams } from '@/shared/api';

/** NFR: a new ticket shows up in the Review Queue within 5 seconds (decision FE-10). */
export const REVIEW_QUEUE_POLL_MS = 5_000;

/** One page of the Review Queue; polls every 5 s while the tab is visible, unless the event stream is live. */
export function useReviewQueue(
  params: GetReviewQueueParams,
  { live = false }: { live?: boolean } = {},
) {
  return useGetReviewQueue(params, {
    query: {
      refetchInterval: live ? false : REVIEW_QUEUE_POLL_MS, // TanStack Query pauses it while the tab is hidden
      placeholderData: keepPreviousData, // no flicker when the page, sort or filter changes
    },
  });
}
