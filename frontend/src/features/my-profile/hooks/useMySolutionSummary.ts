import { useGetMySolutionSummary } from '@/shared/api';

/**
 * The status counts and the Projects of the user's Solutions. Loaded once per visit (no refetch on focus or paging),
 * no retry; its error stays apart from the table's (AC-003.21, AC-003.14). `projects` feeds Task 21's Project filter.
 */
export function useMySolutionSummary() {
  const query = useGetMySolutionSummary({
    query: { retry: false, staleTime: Infinity, refetchOnWindowFocus: false },
  });
  return { ...query, projects: query.data?.projects ?? [] };
}
