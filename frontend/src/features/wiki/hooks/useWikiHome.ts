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

  function selectFolder(nextFolderId: string | undefined) {
    setParams((current) => {
      const next = new URLSearchParams(current);
      if (nextFolderId) next.set('folder', nextFolderId);
      else next.delete('folder');
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
    companyWide,
    selectedSpace,
    spaceId,
    folderId,
    q,
    isSearching,
    folders,
    documents,
    results,
    selectFolder,
    search,
  };
}
