import { keepPreviousData } from '@tanstack/react-query';
import { useGetKnowledgeDashboard, type GetKnowledgeDashboardParams } from '@/shared/api';

export function useKnowledgeDashboard(params: GetKnowledgeDashboardParams, enabled: boolean) {
  return useGetKnowledgeDashboard(params, {
    query: {
      enabled,
      // Retain filter options in the query cache while the next filter state is loading.
      placeholderData: keepPreviousData,
      retry: false,
      refetchOnWindowFocus: false,
    },
  });
}
