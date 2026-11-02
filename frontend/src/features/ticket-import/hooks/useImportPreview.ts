import { useQueryClient } from '@tanstack/react-query';
import {
  getGetImportPreviewQueryKey,
  getGetImportPreviewRowsQueryKey,
  useConfirmImport,
  useGetImportPreviewRows,
  useRemoveImportPreviewRows,
} from '@/shared/api';
import { PAGE_SIZE } from '@/shared/lib';
import { showMessage } from '@/shared/messages';

/** One page of the preview's rows, 20 per page; `page` counts from 1 like the table. */
export function useImportPreviewRows(previewId: number, page: number) {
  return useGetImportPreviewRows(previewId, { page: page - 1, size: PAGE_SIZE });
}

/** Remove (AC-009.3): refetches the Summary and every page of rows. Errors: global toast. */
export function useRemovePreviewRows(previewId: number) {
  const queryClient = useQueryClient();
  return useRemoveImportPreviewRows({
    mutation: {
      onSuccess: () =>
        Promise.all([
          queryClient.invalidateQueries({ queryKey: getGetImportPreviewQueryKey(previewId) }),
          // Without params the key is the prefix of every page's key.
          queryClient.invalidateQueries({ queryKey: getGetImportPreviewRowsQueryKey(previewId) }),
        ]),
    },
  });
}

/** Confirm (AC-009.6, AC-009.7): MSG32 on success; the page shows the error and stays. */
export function useConfirmPreview() {
  return useConfirmImport({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: (result) =>
        showMessage('MSG32', { created: result.created, skipped: result.skipped }),
    },
  });
}
