import { keepPreviousData } from '@tanstack/react-query';
import { useGetReviewHistory, type GetReviewHistoryParams } from '@/shared/api';

export function useReviewHistory(params: GetReviewHistoryParams, enabled: boolean) {
  return useGetReviewHistory(params, { query: { enabled, placeholderData: keepPreviousData } });
}
