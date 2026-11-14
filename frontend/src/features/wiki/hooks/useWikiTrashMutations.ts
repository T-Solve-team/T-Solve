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
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

/** Keep cached document views consistent across trash, restore and permanent deletion. */
export function useWikiTrashMutations(spaceId: string) {
  const client = useQueryClient();
  function refresh(documentId: string) {
    return Promise.all([
      client.invalidateQueries({ queryKey: getListWikiTrashQueryKey(spaceId) }),
      client.invalidateQueries({ queryKey: getListWikiDocumentsQueryKey(spaceId) }),
      client.invalidateQueries({ queryKey: getListCompanyWideDocumentsQueryKey() }),
      client.invalidateQueries({ queryKey: getSearchWikiDocumentsQueryKey() }),
      client.invalidateQueries({ queryKey: getGetWikiDocumentQueryKey(documentId) }),
    ]);
  }
  const trash = useTrashWikiDocument({
    mutation: {
      onSuccess: (_, { documentId }) => {
        showMessage('MSG101');
        return refresh(documentId);
      },
    },
  });
  const restore = useRestoreWikiDocument({
    mutation: {
      onSuccess: (_, { documentId }) => {
        showMessage('MSG102');
        return refresh(documentId);
      },
    },
  });
  const permanent = useDeleteWikiDocumentPermanently({
    mutation: {
      onSuccess: (_, { documentId }) => {
        showMessage('MSG05');
        return refresh(documentId);
      },
    },
  });
  return { trash, restore, permanent };
}
