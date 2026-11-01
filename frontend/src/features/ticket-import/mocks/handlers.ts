import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type { ImportPreviewResponse, ImportPreviewRowPage } from '@/shared/api';
import { getDownloadCsvTemplateMockHandler } from '@/shared/api/generated/import/import.msw';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { CSV_TEMPLATE, previewResponse, rowPage, seedImport, type MockPreview } from './data';

/** The PM's unconfirmed preview of the mock backend. An upload replaces it, like the real one (spec D9). */
let preview: MockPreview = seedImport();

/** Back to the starting preview — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  preview = seedImport();
}

/** Another user's, a confirmed or a deleted preview: 404 without a code, like the backend (AC-007.2). */
function isCurrent(previewId: string | readonly string[] | undefined): boolean {
  return previewId === String(preview.previewId);
}

/** MSW mock handlers of the "ticket-import" feature (guideline 10 §5). */
export const handlers: HttpHandler[] = [
  getDownloadCsvTemplateMockHandler(CSV_TEMPLATE),

  // In mock mode a file name containing "missing-columns" answers MSG29, one containing "empty" MSG04;
  // any other file gets the seed rows.
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
    return HttpResponse.json<ImportPreviewResponse>(previewResponse(preview));
  }),

  http.get('*/api/v1/imports/previews/:previewId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    if (!isCurrent(params.previewId)) return problem(404);
    return HttpResponse.json<ImportPreviewResponse>(previewResponse(preview));
  }),

  http.get('*/api/v1/imports/previews/:previewId/rows', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    if (!isCurrent(params.previewId)) return problem(404);
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page')) || 0;
    const size = Number(url.searchParams.get('size')) || undefined;
    return HttpResponse.json<ImportPreviewRowPage>(rowPage(preview.rows, page, size));
  }),
];
