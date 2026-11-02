import {
  useGetSuggestionResult,
  useGetSuggestionResults,
  type GetSuggestionResultsParams,
} from '@/shared/api';

export function useSuggestionHistory(params: GetSuggestionResultsParams, enabled: boolean) {
  return useGetSuggestionResults(params, { query: { enabled } });
}

export function useSuggestionResult(issueKey: string) {
  return useGetSuggestionResult(issueKey, { query: { enabled: Boolean(issueKey), retry: false } });
}
