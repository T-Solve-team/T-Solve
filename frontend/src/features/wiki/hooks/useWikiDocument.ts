import { getGetWikiDocumentUrl, useGetWikiDocument } from '@/shared/api';

export function useWikiDocument(documentId: string) {
  return useGetWikiDocument(documentId, { query: { enabled: documentId !== '' } });
}

/** Browser download keeps the session cookie and streams the original file (W19, Task 6). */
export function wikiDocumentFileUrl(documentId: string): string {
  return `${getGetWikiDocumentUrl(documentId)}/file`;
}
