import { keepPreviousData } from '@tanstack/react-query';
import { useListUnavailableCitedDocuments } from '@/shared/api';

/**
 * The cited Wiki documents the user can no longer view (MSG110, UC-52). If the check fails, none — the links stay
 * (spec AC-002.10); query errors show no toast.
 */
export function useUnavailableDocuments(wikiDocumentIds: number[]): ReadonlySet<number> {
  const query = useListUnavailableCitedDocuments(
    { wikiDocumentIds },
    // keepPreviousData: while a follow-up's documents are checked, an MSG110 already shown must not turn into a link.
    { query: { enabled: wikiDocumentIds.length > 0, placeholderData: keepPreviousData } },
  );
  return new Set(query.data?.wikiDocumentIds ?? []);
}
