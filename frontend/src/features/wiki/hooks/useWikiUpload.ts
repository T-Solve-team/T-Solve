import { useQueryClient } from '@tanstack/react-query';
import {
  getListCompanyWideDocumentsQueryKey,
  getListWikiDocumentsQueryKey,
  getSearchWikiDocumentsQueryKey,
  useExtractWikiUploads,
  usePublishWikiDocuments,
} from '@/shared/api';
import { showMessage } from '@/shared/messages';

/** Extraction is stateless; publishing sends the originals again (W4). */
export function useWikiUpload(spaceId: string) {
  const client = useQueryClient();
  const extract = useExtractWikiUploads({ mutation: { meta: { handlesOwnErrors: true } } });
  const publish = usePublishWikiDocuments({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: async (documents) => {
        showMessage('MSG95', { count: documents.length });
        await Promise.all([
          client.invalidateQueries({ queryKey: getListWikiDocumentsQueryKey(spaceId) }),
          client.invalidateQueries({ queryKey: getListCompanyWideDocumentsQueryKey() }),
          client.invalidateQueries({ queryKey: getSearchWikiDocumentsQueryKey() }),
        ]);
      },
    },
  });
  return { extract, publish };
}
