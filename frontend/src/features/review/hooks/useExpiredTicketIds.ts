import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  getGetExpiredTicketIdsQueryKey,
  getExpiredTicketIds,
  useGetExpiredTicketIds,
} from '@/shared/api';

/**
 * "Select all" takes every Expired ticket, not only the page shown (SRS 7.5). Returns a function that
 * loads all ticket ids for the current Department filter.
 */
export function useExpiredTicketIds(
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
      const result = await getExpiredTicketIds(params, { signal });
      if (!signal.aborted) setAccepted({ departmentId, version: queueVersion });
      return result;
    },
    [departmentId, params, queueVersion],
  );
  const query = useGetExpiredTicketIds(params, {
    query: { enabled, staleTime: Infinity, queryFn: fetchIds },
  });
  const { refetch } = query;
  // Reconcile only when the list has newer data; selecting all already accepted this version.
  useEffect(() => {
    if (enabled && queueVersion && accepted?.version !== queueVersion) void refetch();
  }, [enabled, queueVersion, accepted?.version, refetch]);
  const loadAll = useCallback(async () => {
    const result = await queryClient.fetchQuery({
      queryKey: getGetExpiredTicketIdsQueryKey(params),
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
