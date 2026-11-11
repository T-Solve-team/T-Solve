import { useQueryClient } from '@tanstack/react-query';
import {
  getGetImportPreviewQueryKey,
  getGetImportPreviewRowQueryKey,
  getGetImportPreviewRowsQueryKey,
  getListImportDraftsQueryKey,
  useConfirmImport,
  useFillImportPreviewSolution,
  useGetImportPreviewRows,
  useRemoveImportPreviewRows,
  useSaveImportDraft,
} from '@/shared/api';
import { PAGE_SIZE } from '@/shared/lib';
import { isMsgCode, showAcknowledgement, showMessage } from '@/shared/messages';

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

/**
 * Fill solution (AC-015.2): refetches the Summary and every page of rows. The popup handles its own errors, so a
 * rejected Solution stays in the form (AC-015.3).
 */
export function useFillPreviewSolution(previewId: number) {
  const queryClient = useQueryClient();
  return useFillImportPreviewSolution({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: (_, { data }) =>
        Promise.all([
          queryClient.invalidateQueries({ queryKey: getGetImportPreviewQueryKey(previewId) }),
          queryClient.invalidateQueries({ queryKey: getGetImportPreviewRowsQueryKey(previewId) }),
          // The popup reads one row: a saved row shows its Solution when opened again.
          ...data.rowNumbers.map((rowNo) =>
            queryClient.invalidateQueries({
              queryKey: getGetImportPreviewRowQueryKey(previewId, rowNo),
            }),
          ),
        ]),
    },
  });
}

/**
 * Confirm / Finish (AC-009.6, AC-009.7): MSG32 on success, and for Jira MSG38 with the skipped issues, which stays
 * until the user closes it (AC-003.2); the page shows the error and stays.
 */
export function useConfirmPreview() {
  return useConfirmImport({
    mutation: {
      meta: { handlesOwnErrors: true },
      onSuccess: ({ created, skipped, notice }) => {
        showMessage('MSG32', { created, skipped });
        if (notice && isMsgCode(notice.code)) void showAcknowledgement(notice.code, notice.params);
      },
    },
  });
}

/** Save as draft of a Jira preview (AC-004.1): MSG41; the Summary and the Drafts list are read again. Errors: global toast. */
export function useSaveDraft(previewId: number) {
  const queryClient = useQueryClient();
  return useSaveImportDraft({
    mutation: {
      onSuccess: () => {
        showMessage('MSG41');
        return Promise.all([
          queryClient.invalidateQueries({ queryKey: getGetImportPreviewQueryKey(previewId) }),
          queryClient.invalidateQueries({ queryKey: getListImportDraftsQueryKey() }),
        ]);
      },
    },
  });
}
