import dayjs from 'dayjs';
import { useSearchParams } from 'react-router';
import { TicketSource, useSearchTickets } from '@/shared/api';
import { PAGE_SIZE } from '@/shared/lib';
import { useListSearchParams } from '@/shared/routing';

/** Results are ranked by relevance: the shared list params need a sort, but it is never sent (design). */
const RANKED = { field: 'relevance', order: 'desc' } as const;
/** The query is free text of at most 200 characters (spec AC-002.4), also when it comes from a link. */
export const QUERY_MAX = 200;

/** A `YYYY-MM-DD` day from the URL; anything else → undefined, so the API never gets an invalid date. */
function day(value: string | null): string | undefined {
  return value && dayjs(value).format('YYYY-MM-DD') === value ? value : undefined;
}

/**
 * 6.1 Search: the query, filters and page kept in the URL (spec AC-002.10), and the search itself, which runs only
 * with a non-blank query (decision Q3, AC-002.2). No placeholder data: every new search shows the loading state.
 */
export function useTicketSearch() {
  const list = useListSearchParams(RANKED);
  const [, setSearchParams] = useSearchParams();
  // A hand-edited link must not make every search a 400: cap the query, drop an empty Department and a reversed range.
  const q = list.getFilter('q')?.trim().slice(0, QUERY_MAX) ?? '';
  const departmentId = list.getFilter('departmentId') || undefined;
  const source = Object.values(TicketSource).find((s) => s === list.getFilter('source'));
  const from = day(list.getFilter('resolvedFrom'));
  const to = day(list.getFilter('resolvedTo'));
  const [resolvedFrom, resolvedTo] = from && to && from > to ? [] : [from, to];

  const results = useSearchTickets(
    { q, departmentId, source, resolvedFrom, resolvedTo, page: list.page - 1, size: PAGE_SIZE },
    { query: { enabled: q !== '' } },
  );

  return {
    q,
    departmentId,
    source,
    resolvedFrom,
    resolvedTo,
    page: list.page,
    results,
    setPage: list.setPage,
    /** null = the filter's default; the query itself is `q`. Back to page 1. */
    setFilter: list.setFilter,
    /** Both days at once (two `setFilter` calls in a row would keep only the last); null clears the range. */
    setResolved: (range: [from: string, to: string] | null) =>
      setSearchParams((current) => {
        const next = new URLSearchParams(current);
        next.delete('page');
        if (range) {
          next.set('resolvedFrom', range[0]);
          next.set('resolvedTo', range[1]);
        } else {
          next.delete('resolvedFrom');
          next.delete('resolvedTo');
        }
        return next;
      }),
  };
}
