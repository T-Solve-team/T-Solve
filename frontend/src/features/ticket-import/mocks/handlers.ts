import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type {
  ImportPreviewResponse,
  ImportPreviewRowPage,
  ImportPreviewRowResponse,
  ImportResultResponse,
  RemoveRowsRequest,
} from '@/shared/api';
import { getDownloadCsvTemplateMockHandler } from '@/shared/api/generated/import/import.msw';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { CSV_TEMPLATE, previewResponse, rowPage, seedImport, type MockPreview } from './data';

/** The PM's unconfirmed preview of the mock backend. An upload replaces it, like the real one (spec D9). */
let preview: MockPreview = seedImport();
/** A confirmed preview is gone: every later read answers 404 (AC-004.12). */
let isConfirmed = false;
let lastBatchId = 0;

/** Back to the starting preview — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  preview = seedImport();
  isConfirmed = false;
  lastBatchId = 0;
}

/** Another user's, a confirmed or a deleted preview: 404 with MSG04, like the backend (AC-007.2). */
function isCurrent(previewId: string | readonly string[] | undefined): boolean {
  return !isConfirmed && previewId === String(preview.previewId);
}

/**
 * MSW mock handlers of the "ticket-import" feature (guideline 10 §5). In mock mode the uploaded file name picks
 * the business errors: "missing-columns" → MSG29 and "empty" → MSG04 on upload; "inactive-project" → MSG40 and
 * "import-fails" → MSG33 on Confirm. Any other file gets the seed rows.
 */
export const handlers: HttpHandler[] = [
  getDownloadCsvTemplateMockHandler(CSV_TEMPLATE),

  http.post('*/api/v1/imports/csv', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const form = await request.formData();
    const file = form.get('file');
    const projectId = form.get('projectId');
    const fileName = file instanceof File ? file.name : '';
    if (fileName.includes('missing-columns')) {
      return problem(400, 'MSG29', { params: { column_names: 'root_cause, scope' } });
    }
    if (fileName.includes('empty')) return problem(400, 'MSG04');
    preview = seedImport(
      preview.previewId + 1,
      typeof projectId === 'string' ? projectId : '',
      fileName,
    );
    isConfirmed = false;
    return HttpResponse.json<ImportPreviewResponse>(previewResponse(preview));
  }),

  http.get('*/api/v1/imports/previews/:previewId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    if (!isCurrent(params.previewId)) return problem(404, 'MSG04');
    return HttpResponse.json<ImportPreviewResponse>(previewResponse(preview));
  }),

  http.get('*/api/v1/imports/previews/:previewId/rows', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    if (!isCurrent(params.previewId)) return problem(404, 'MSG04');
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page')) || 0;
    const size = Number(url.searchParams.get('size')) || undefined;
    return HttpResponse.json<ImportPreviewRowPage>(rowPage(preview.rows, page, size));
  }),

  http.get('*/api/v1/imports/previews/:previewId/rows/:rowNo', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const row = isCurrent(params.previewId)
      ? preview.rows.find((r) => String(r.rowNo) === params.rowNo)
      : undefined;
    if (!row) return problem(404, 'MSG04');
    return HttpResponse.json<ImportPreviewRowResponse>(row);
  }),

  // Valid and Error rows become Removed; unknown row numbers are ignored (AC-003.1).
  http.post('*/api/v1/imports/previews/:previewId/remove-rows', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    if (!isCurrent(params.previewId)) return problem(404, 'MSG04');
    const { rowNumbers } = (await request.json()) as RemoveRowsRequest;
    preview = {
      ...preview,
      rows: preview.rows.map((row) =>
        rowNumbers.includes(row.rowNo) ? { ...row, status: 'REMOVED' } : row,
      ),
    };
    return HttpResponse.json<ImportPreviewResponse>(previewResponse(preview));
  }),

  http.post('*/api/v1/imports/previews/:previewId/confirm', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    if (!isCurrent(params.previewId)) return problem(404, 'MSG04');
    const { validCount, errorCount, removedCount } = previewResponse(preview);
    if (validCount === 0) return problem(409, 'MSG30', { params: { count: errorCount } });
    if (preview.fileName?.includes('inactive-project')) return problem(409, 'MSG40');
    if (preview.fileName?.includes('import-fails')) return problem(500, 'MSG33');
    isConfirmed = true;
    lastBatchId += 1;
    const created = validCount;
    const skipped = errorCount + removedCount;
    return HttpResponse.json<ImportResultResponse>({
      importBatchId: lastBatchId,
      created,
      skipped,
      message: { code: 'MSG32', params: { created, skipped } },
    });
  }),
];
