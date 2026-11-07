import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type {
  FillSolutionRequest,
  ImportDraftSummary,
  ImportPreviewResponse,
  ImportPreviewRowPage,
  ImportPreviewRowResponse,
  ImportResultResponse,
  ImportType,
  JiraPullRequest,
  RemoveRowsRequest,
} from '@/shared/api';
import { getDownloadCsvTemplateMockHandler } from '@/shared/api/generated/import/import.msw';
import { MOCK_DELAY_MS, problem } from '@/shared/api/mocks';
import {
  CSV_TEMPLATE,
  CSV_TEMPLATE_WITHOUT_SOLUTION,
  previewResponse,
  rowPage,
  seedImport,
  seedJiraImport,
  type MockPreview,
} from './data';

const ITSUP_ID = '00000000-0000-4000-8000-000000000101';
const ITNET_ID = '00000000-0000-4000-8000-000000000102';

/** The PM's Drafts (AC-004.2): kept by a new upload or pull (AC-004.6). */
function seedDrafts(): MockPreview[] {
  return [
    {
      ...seedJiraImport(900, ITSUP_ID, '2026-10-01', '2026-10-15'),
      draftSavedAt: '2026-10-16T02:30:00Z',
    },
  ];
}

/** The PM's unconfirmed preview of the mock backend. An upload replaces it, like the real one (spec D9). */
let preview: MockPreview = seedImport();
/** A confirmed preview is gone: every later read answers 404 (AC-004.12). */
let isConfirmed = false;
let lastBatchId = 0;
let drafts: MockPreview[] = seedDrafts();

/** Back to the starting preview — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  preview = seedImport();
  isConfirmed = false;
  lastBatchId = 0;
  drafts = seedDrafts();
}

/** The open preview or one of the Drafts, for the read endpoints (a reopened Draft, AC-004.3). */
function findPreview(previewId: string | readonly string[] | undefined): MockPreview | undefined {
  if (isCurrent(previewId)) return preview;
  return drafts.find((draft) => String(draft.previewId) === previewId);
}

/** Tests: the starting preview becomes a file without Solutions (its Valid rows need a Solution). */
export function seedFileWithoutSolutions(): void {
  preview = seedImport(undefined, undefined, undefined, 'WITHOUT_SOLUTION');
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
  getDownloadCsvTemplateMockHandler(({ request }) =>
    new URL(request.url).searchParams.get('importType') === 'WITHOUT_SOLUTION'
      ? CSV_TEMPLATE_WITHOUT_SOLUTION
      : CSV_TEMPLATE,
  ),

  http.post('*/api/v1/imports/csv', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const form = await request.formData();
    const file = form.get('file');
    const projectId = form.get('projectId');
    const importType: ImportType =
      form.get('importType') === 'WITHOUT_SOLUTION' ? 'WITHOUT_SOLUTION' : 'WITH_SOLUTION';
    const fileName = file instanceof File ? file.name : '';
    if (fileName.includes('missing-columns')) {
      return problem(400, 'MSG29', { params: { column_names: 'root_cause, scope' } });
    }
    if (fileName.includes('empty')) return problem(400, 'MSG04');
    preview = seedImport(
      preview.previewId + 1,
      typeof projectId === 'string' ? projectId : '',
      fileName,
      importType,
    );
    isConfirmed = false;
    return HttpResponse.json<ImportPreviewResponse>(previewResponse(preview));
  }),

  // ITNET has no issue left without a Ticket: MSG04. Jira failures (MSG50) are set per test.
  http.post('*/api/v1/imports/jira', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const body = (await request.json()) as JiraPullRequest;
    if (!body.resolvedFrom || !body.resolvedTo) {
      return problem(400, 'MSG01', { params: { field_name: 'Resolved from' } });
    }
    if (body.projectId === ITNET_ID) return problem(404, 'MSG04');
    preview = seedJiraImport(
      preview.previewId + 1,
      body.projectId,
      body.resolvedFrom,
      body.resolvedTo,
    );
    isConfirmed = false;
    return HttpResponse.json<ImportPreviewResponse>(previewResponse(preview));
  }),

  http.get('*/api/v1/imports/drafts', async () => {
    await delay(MOCK_DELAY_MS);
    return HttpResponse.json<ImportDraftSummary[]>(
      drafts.map((draft) => ({
        previewId: draft.previewId,
        source: draft.source ?? 'CSV',
        projectId: draft.projectId,
        rowCount: draft.rows.filter((row) => row.status !== 'REMOVED').length,
        savedAt: draft.draftSavedAt ?? '',
      })),
    );
  }),

  http.delete('*/api/v1/imports/drafts/:previewId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    if (!drafts.some((draft) => String(draft.previewId) === params.previewId)) {
      return problem(404, 'MSG04');
    }
    drafts = drafts.filter((draft) => String(draft.previewId) !== params.previewId);
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('*/api/v1/imports/previews/:previewId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const found = findPreview(params.previewId);
    if (!found) return problem(404, 'MSG04');
    return HttpResponse.json<ImportPreviewResponse>(previewResponse(found));
  }),

  http.get('*/api/v1/imports/previews/:previewId/rows', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const found = findPreview(params.previewId);
    if (!found) return problem(404, 'MSG04');
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page')) || 0;
    const size = Number(url.searchParams.get('size')) || undefined;
    return HttpResponse.json<ImportPreviewRowPage>(rowPage(found.rows, page, size));
  }),

  http.get('*/api/v1/imports/previews/:previewId/rows/:rowNo', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const row = findPreview(params.previewId)?.rows.find((r) => String(r.rowNo) === params.rowNo);
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

  // Like the backend (AC-013.1 – AC-013.4): a copy in each row, which becomes Valid; all rows or none.
  http.post('*/api/v1/imports/previews/:previewId/fill-solution', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    if (!isCurrent(params.previewId)) return problem(404, 'MSG04');
    const { rowNumbers, ...solution } = (await request.json()) as FillSolutionRequest;
    const targets = preview.rows.filter((row) => rowNumbers.includes(row.rowNo));
    if (targets.length < new Set(rowNumbers).size) return problem(404, 'MSG04');
    if (
      preview.importType !== 'WITHOUT_SOLUTION' ||
      targets.some((row) => row.status === 'ERROR' || row.status === 'REMOVED')
    ) {
      return problem(409, 'MSG06');
    }
    const trim = (text: string | null | undefined) => text?.trim() || null;
    preview = {
      ...preview,
      rows: preview.rows.map((row) =>
        rowNumbers.includes(row.rowNo)
          ? {
              ...row,
              symptoms: trim(solution.symptoms),
              rootCause: trim(solution.rootCause),
              resolutionSteps: trim(solution.resolutionSteps),
              workaround: trim(solution.workaround),
              scope: trim(solution.scope),
              status: 'VALID',
            }
          : row,
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
