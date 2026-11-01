import { useGetImportPreviewRows } from '@/shared/api';
import { PAGE_SIZE } from '@/shared/lib';

/** One page of the preview's rows, 20 per page; `page` counts from 1 like the table. */
export function useImportPreviewRows(previewId: number, page: number) {
  return useGetImportPreviewRows(previewId, { page: page - 1, size: PAGE_SIZE });
}
