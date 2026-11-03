import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  getGetReviewQueueTicketIdsQueryKey,
  getReviewQueueTicketIds,
  useGetReviewQueueTicketIds,
} from '@/shared/api';

/**
 * "Select all" takes every ticket in the queue, not only the page shown (SRS 7.1). Returns a function that
 * loads all ticket ids for the current Department filter.
 */
export function useQueueTicketIds(
  departmentId: string | undefined,
  enabled: boolean,
  queueVersion: number,
) {
  const queryClient = useQueryClient();
  const params = useMemo(() => (departmentId ? { departmentId } : undefined), [departmentId]);
  const [accepted, setAccepted] = useState<{
    departmentId: string | undefined;
    version: number;
  } | null>(null);
  const fetchIds = useCallback(
    async ({ signal }: { signal: AbortSignal }) => {
      const result = await getReviewQueueTicketIds(params, { signal });
      if (!signal.aborted) setAccepted({ departmentId, version: queueVersion });
      return result;
    },
    [departmentId, params, queueVersion],
  );
  const query = useGetReviewQueueTicketIds(params, {
    query: { enabled, staleTime: Infinity, queryFn: fetchIds },
  });
  const { refetch } = query;
  // Reuse the page poll instead of a second unconditional full-id polling interval.
  useEffect(() => {
    if (enabled && queueVersion) void refetch();
  }, [enabled, queueVersion, refetch]);
  const loadAll = useCallback(async () => {
    const result = await queryClient.fetchQuery({
      queryKey: getGetReviewQueueTicketIdsQueryKey(params),
      queryFn: fetchIds,
      staleTime: 0, // always the queue as it is now
    });
    return result.ticketIds;
  }, [queryClient, params, fetchIds]);
  return {
    ...query,
    loadAll,
    isFresh:
      query.isSuccess &&
      !query.isFetching &&
      accepted?.departmentId === departmentId &&
      accepted?.version === queueVersion,
  };
}
