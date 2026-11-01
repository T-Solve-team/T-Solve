import dayjs from 'dayjs';
import { useCallback, useEffect } from 'react';
import { useSearchParams } from 'react-router';
import type { ProjectRef, TicketStatus } from '@/shared/api';

/** The statuses in the counters' order (1.2.1 (d)), also the order of the Status filter. */
export const SOLUTION_STATUSES: readonly TicketStatus[] = [
  'PENDING_REVIEW',
  'CHANGES_REQUESTED',
  'PUBLISHED',
  'REJECTED',
  'EXPIRED',
  'UNPUBLISHED',
];

export type SolutionFilterValues = {
  status?: TicketStatus;
  projectId?: string;
  from?: string;
  to?: string;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DAY = /^\d{4}-\d{2}-\d{2}$/;
const KEYS = ['status', 'projectId', 'from', 'to'] as const;

/** A real calendar day as YYYY-MM-DD ("2026-02-30" is not one). */
function validDay(value: string | null): value is string {
  return value !== null && DAY.test(value) && dayjs(value).format('YYYY-MM-DD') === value;
}

/**
 * The My Solutions filters, kept in the URL (AC-003.22). Invalid values are dropped from the request at once and
 * removed from the URL with history replace, with no message. A well-formed `projectId` that is not among the
 * summary's Projects is dropped once the summary has loaded, and kept while it loads or fails.
 */
export function useSolutionFilters(projects: ProjectRef[], summaryLoaded: boolean) {
  const [searchParams, setSearchParams] = useSearchParams();

  const rawStatus = searchParams.get('status');
  const rawProject = searchParams.get('projectId');
  const rawFrom = searchParams.get('from');
  const rawTo = searchParams.get('to');

  const status = SOLUTION_STATUSES.find((s) => s === rawStatus);
  const projectId =
    rawProject !== null &&
    UUID.test(rawProject) &&
    (!summaryLoaded || projects.some((p) => p.projectId === rawProject))
      ? rawProject
      : undefined;
  const from = validDay(rawFrom) ? rawFrom : undefined;
  const to = validDay(rawTo) ? rawTo : undefined;
  const reversed = from !== undefined && to !== undefined && to < from;
  const filters: SolutionFilterValues = {
    status,
    projectId,
    from: reversed ? undefined : from,
    to: reversed ? undefined : to,
  };

  const dirty = KEYS.some((key) => searchParams.has(key) && filters[key] === undefined);
  useEffect(() => {
    if (!dirty) return;
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        for (const key of KEYS) if (filters[key] === undefined) next.delete(key);
        return next;
      },
      { replace: true },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `dirty` and the parsed values change together
  }, [dirty, rawStatus, rawProject, rawFrom, rawTo, summaryLoaded, projects]);

  /** null = clear that filter. A change goes back to page 1, replacing the history entry. */
  const setFilters = useCallback(
    (changes: Partial<Record<(typeof KEYS)[number], string | null>>) =>
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current);
          for (const [name, value] of Object.entries(changes)) {
            if (value) next.set(name, value);
            else next.delete(name);
          }
          next.delete('page');
          return next;
        },
        { replace: true },
      ),
    [setSearchParams],
  );

  return { filters, setFilters };
}
