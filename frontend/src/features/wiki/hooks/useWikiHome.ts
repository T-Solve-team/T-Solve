import { useEffect } from 'react';
import { useSearchParams } from 'react-router';
import {
  useListCompanyWideDocuments,
  useListWikiDocuments,
  useListWikiFolders,
  useListWikiSpaces,
  useSearchWikiDocuments,
} from '@/shared/api';

export const WIKI_QUERY_MAX = 200;

/** URL selections are the source of truth; a stale Space link cannot trigger a private browse. */
export function useWikiHome() {
  const [params, setParams] = useSearchParams();
  const spaces = useListWikiSpaces();
  const companyWide = useListCompanyWideDocuments();
  const spaceId = params.get('space') ?? '';
  const folderId = params.get('folder') ?? undefined;
  const q = params.get('q') ?? '';
  const selectedSpace = spaces.data?.find((space) => space.spaceId === spaceId);
  const canBrowse = spaces.isSuccess && selectedSpace !== undefined;
  const folders = useListWikiFolders(spaceId, { query: { enabled: canBrowse } });
  const documents = useListWikiDocuments(spaceId, { folderId }, { query: { enabled: canBrowse } });
  const isSearching = q.trim() !== '' && q.length <= WIKI_QUERY_MAX;
  const results = useSearchWikiDocuments({ q }, { query: { enabled: isSearching } });
  // AC-002.22: "Company-wide" is current while the URL names no viewable Space.
  const isCompanyWide = !selectedSpace && (spaceId === '' || !spaces.isPending);
  const list = isSearching ? results : selectedSpace ? documents : companyWide;
  const isListPending = list.isPending || (!isSearching && !selectedSpace && !isCompanyWide);
  const rows = isListPending ? undefined : list.data;
  // AC-002.24: `?doc=` when it is a row of the list, otherwise the first row.
  const previewId =
    rows?.find((row) => row.documentId === params.get('doc'))?.documentId ?? rows?.[0]?.documentId;

  useEffect(() => {
    if (
      !canBrowse ||
      !folderId ||
      documents.error?.status !== 404 ||
      documents.error.code !== 'MSG04'
    )
      return;
    // Replace the stale link so Back does not return to the missing folder.
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.delete('folder');
        return next;
      },
      { replace: true },
    );
  }, [canBrowse, folderId, documents.error, setParams]);

  function selectFolder(nextFolderId: string | undefined) {
    setParams((current) => {
      const next = new URLSearchParams(current);
      if (nextFolderId) next.set('folder', nextFolderId);
      else next.delete('folder');
      // AC-002.17: a navigator selection drops the search and the previewed document.
      next.delete('q');
      next.delete('doc');
      return next;
    });
  }

  function search(value: string) {
    if (value.length > WIKI_QUERY_MAX) return;
    setParams((current) => {
      const next = new URLSearchParams(current);
      if (value.trim()) next.set('q', value);
      else next.delete('q');
      return next;
    });
  }

  return {
    spaces,
    selectedSpace,
    spaceId,
    folderId,
    q,
    isSearching,
    isCompanyWide,
    folders,
    list,
    isListPending,
    rows,
    previewId,
    selectFolder,
    search,
  };
}
