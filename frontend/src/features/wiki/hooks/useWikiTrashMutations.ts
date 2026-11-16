import { useQueryClient } from '@tanstack/react-query';
import {
  getGetWikiDocumentQueryKey,
  getListCompanyWideDocumentsQueryKey,
  getListWikiDocumentsQueryKey,
  getListWikiTrashQueryKey,
  getSearchWikiDocumentsQueryKey,
  useDeleteWikiDocumentPermanently,
  useRestoreWikiDocument,
  useTrashWikiDocument,
  type WikiDocumentDetail,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

/** Keep cached document views consistent across trash, restore and permanent deletion. */
export function useWikiTrashMutations(spaceId: string) {
  const client = useQueryClient();
  /** Drop the document's cached view so the next visit reads its new state. */
  function forgetDocument(documentId: string) {
    client.removeQueries({ queryKey: getGetWikiDocumentQueryKey(documentId), exact: true });
  }
  function refreshLists() {
    return Promise.all([
      client.invalidateQueries({ queryKey: getListWikiTrashQueryKey(spaceId) }),
      client.invalidateQueries({ queryKey: getListWikiDocumentsQueryKey(spaceId) }),
      client.invalidateQueries({ queryKey: getListCompanyWideDocumentsQueryKey() }),
      client.invalidateQueries({ queryKey: getSearchWikiDocumentsQueryKey() }),
    ]);
  }
  const trash = useTrashWikiDocument({
    mutation: {
      // The document page is still mounted here, and removing or refetching its query would refetch the page
      // being left. Mark the cached view as in trash and stale instead: no request now, and the next visit
      // shows the read-only view while it refetches (BUG-304).
      onSuccess: (_, { documentId }) => {
        showMessage('MSG101');
        const queryKey = getGetWikiDocumentQueryKey(documentId);
        client.setQueryData(
          queryKey,
          (old: WikiDocumentDetail | undefined) =>
            old && { ...old, inTrash: true, canDelete: false },
        );
        void client.invalidateQueries({ queryKey, exact: true, refetchType: 'none' });
        return refreshLists();
      },
    },
  });
  const restore = useRestoreWikiDocument({
    mutation: {
      onSuccess: (_, { documentId }) => {
        showMessage('MSG102');
        forgetDocument(documentId);
        return refreshLists();
      },
    },
  });
  const permanent = useDeleteWikiDocumentPermanently({
    mutation: {
      onSuccess: (_, { documentId }) => {
        showMessage('MSG05');
        forgetDocument(documentId);
        return refreshLists();
      },
    },
  });
  return { trash, restore, permanent };
}
