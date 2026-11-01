import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type { ImportPreviewResponse } from '@/shared/api';
import { getDownloadCsvTemplateMockHandler } from '@/shared/api/generated/import/import.msw';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import { CSV_TEMPLATE, seedPreview } from './data';

/** The PM's unconfirmed preview of the mock backend. An upload replaces it, like the real one (spec D9). */
let preview: ImportPreviewResponse = seedPreview();

/** Back to the starting preview — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  preview = seedPreview();
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
    preview = seedPreview(
      preview.previewId + 1,
      typeof projectId === 'string' ? projectId : '',
      fileName,
    );
    return HttpResponse.json<ImportPreviewResponse>(preview);
  }),
];
