import { delay, http, HttpResponse, type HttpHandler } from 'msw';
import type {
  DraftSavedResponse,
  FillSolutionRequest,
  ImportDraftSummary,
  ImportPreviewResponse,
  ImportPreviewRowPage,
  ImportPreviewRowResponse,
  ImportResultResponse,
  ImportType,
  JiraPullRequest,
  ManualTicketDraft,
  ManualTicketRequest,
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
/** The PM's Manual Drafts (AC-006.9), listed with the other Drafts. */
let manualDrafts: ManualTicketDraft[] = [];
let lastManualDraftId = 800;

/** Back to the starting preview — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  preview = seedImport();
  isConfirmed = false;
  lastBatchId = 0;
  drafts = seedDrafts();
  manualDrafts = [];
  lastManualDraftId = 800;
}

/** A Ticket ID the mock repository already holds: MSG31 on a manual save (AC-006.7). */
const TAKEN_TICKET_ID = 'ITSUP-0871';

/** The manual entry fields Save requires (AC-006.3), with their labels. */
const MANUAL_REQUIRED = [
  ['projectId', 'Project'],
  ['ticketId', 'Ticket ID'],
  ['title', 'Title'],
  ['description', 'Description'],
  ['symptoms', 'Symptoms'],
  ['rootCause', 'Root cause'],
  ['resolutionSteps', 'Resolution steps'],
  ['scope', 'Scope'],
] as const;

/** The open preview or one of the Drafts, for the read endpoints (a reopened Draft, AC-004.3). */
function findPreview(previewId: string | readonly string[] | undefined): MockPreview | undefined {
  if (isCurrent(previewId)) return preview;
  return drafts.find((draft) => String(draft.previewId) === previewId);
}

/** Saves a changed preview where it lives: the open preview or one of the Drafts. */
function savePreview(next: MockPreview): void {
  if (next.previewId === preview.previewId) preview = next;
  else drafts = drafts.map((draft) => (draft.previewId === next.previewId ? next : draft));
}

/** A new upload or pull replaces the open preview but keeps it when it is a Draft (AC-004.6). */
function replacePreview(next: MockPreview): void {
  if (!isConfirmed && preview.draftSavedAt) drafts = [preview, ...drafts];
  preview = next;
  isConfirmed = false;
}

/** Tests: the starting preview becomes a Jira pull of three Needs Solution issues, 2026-10-01 to 2026-10-15. */
export function seedJiraPreview(): void {
  preview = seedJiraImport(1, ITSUP_ID, '2026-10-01', '2026-10-15');
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
    replacePreview(
      seedImport(
        preview.previewId + 1,
        typeof projectId === 'string' ? projectId : '',
        fileName,
        importType,
      ),
    );
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
    replacePreview(
      seedJiraImport(preview.previewId + 1, body.projectId, body.resolvedFrom, body.resolvedTo),
    );
    return HttpResponse.json<ImportPreviewResponse>(previewResponse(preview));
  }),

  http.get('*/api/v1/imports/drafts', async () => {
    await delay(MOCK_DELAY_MS);
    const saved = !isConfirmed && preview.draftSavedAt ? [preview, ...drafts] : drafts;
    return HttpResponse.json<ImportDraftSummary[]>([
      ...manualDrafts.map((draft) => ({
        previewId: draft.previewId,
        source: 'MANUAL' as const,
        projectId: draft.projectId,
        rowCount: 1,
        savedAt: draft.savedAt,
      })),
      ...saved.map((draft) => ({
        previewId: draft.previewId,
        source: draft.source ?? 'CSV',
        projectId: draft.projectId,
        rowCount: draft.rows.filter((row) => row.status !== 'REMOVED').length,
        savedAt: draft.draftSavedAt ?? '',
      })),
    ]);
  }),

  http.delete('*/api/v1/imports/drafts/:previewId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const isDraft = (draft: { previewId: number }) => String(draft.previewId) === params.previewId;
    if (!drafts.some(isDraft) && !manualDrafts.some(isDraft)) return problem(404, 'MSG04');
    drafts = drafts.filter((draft) => !isDraft(draft));
    manualDrafts = manualDrafts.filter((draft) => !isDraft(draft));
    return new HttpResponse(null, { status: 204 });
  }),

  // Manual entry (AC-006.1, AC-006.3, AC-006.7, AC-006.12): every missing field at once; the taken Ticket ID → MSG31.
  http.post('*/api/v1/imports/manual', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const body = (await request.json()) as ManualTicketRequest;
    const missing = MANUAL_REQUIRED.filter(([field]) => !body[field]?.trim());
    if (missing.length > 0) {
      return problem(400, 'MSG01', {
        fieldErrors: missing.map(([field, label]) => ({
          field,
          code: 'MSG01',
          params: { field_name: label },
        })),
      });
    }
    const ticketId = body.ticketId?.trim();
    if (ticketId === TAKEN_TICKET_ID) {
      const params = { ticket_id: ticketId };
      return problem(409, 'MSG31', {
        params,
        fieldErrors: [{ field: 'ticketId', code: 'MSG31', params }],
      });
    }
    manualDrafts = manualDrafts.filter((draft) => draft.previewId !== body.draftId);
    lastBatchId += 1;
    return HttpResponse.json<ImportResultResponse>(
      {
        importBatchId: lastBatchId,
        created: 1,
        skipped: 0,
        message: { code: 'MSG32', params: { created: 1, skipped: 0 } },
      },
      { status: 201 },
    );
  }),

  // Save as draft (AC-006.9, AC-006.10): only the Project is checked; a draftId rewrites that Draft.
  http.post('*/api/v1/imports/manual/drafts', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const { draftId, ...fields } = (await request.json()) as ManualTicketRequest;
    if (!fields.projectId) {
      return problem(400, 'MSG01', {
        fieldErrors: [{ field: 'projectId', code: 'MSG01', params: { field_name: 'Project' } }],
      });
    }
    if (draftId && !manualDrafts.some((draft) => draft.previewId === draftId)) {
      return problem(404, 'MSG04');
    }
    const previewId = draftId ?? (lastManualDraftId += 1);
    const savedAt = new Date().toISOString();
    manualDrafts = [
      { ...fields, previewId, savedAt },
      ...manualDrafts.filter((draft) => draft.previewId !== previewId),
    ];
    return HttpResponse.json<DraftSavedResponse>({
      previewId,
      savedAt,
      message: { code: 'MSG41', params: {} },
    });
  }),

  http.get('*/api/v1/imports/manual/drafts/:previewId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const found = manualDrafts.find((draft) => String(draft.previewId) === params.previewId);
    if (!found) return problem(404, 'MSG04');
    return HttpResponse.json<ManualTicketDraft>(found);
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
    const found = findPreview(params.previewId);
    if (!found) return problem(404, 'MSG04');
    const { rowNumbers } = (await request.json()) as RemoveRowsRequest;
    const next: MockPreview = {
      ...found,
      rows: found.rows.map((row) =>
        rowNumbers.includes(row.rowNo) ? { ...row, status: 'REMOVED' } : row,
      ),
    };
    savePreview(next);
    return HttpResponse.json<ImportPreviewResponse>(previewResponse(next));
  }),

  // Like the backend (AC-013.1 – AC-013.4): a copy in each row, which becomes Valid; all rows or none.
  http.post('*/api/v1/imports/previews/:previewId/fill-solution', async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const found = findPreview(params.previewId);
    if (!found) return problem(404, 'MSG04');
    const { rowNumbers, ...solution } = (await request.json()) as FillSolutionRequest;
    const targets = found.rows.filter((row) => rowNumbers.includes(row.rowNo));
    if (targets.length < new Set(rowNumbers).size) return problem(404, 'MSG04');
    if (
      found.importType !== 'WITHOUT_SOLUTION' ||
      targets.some((row) => row.status === 'ERROR' || row.status === 'REMOVED')
    ) {
      return problem(409, 'MSG06');
    }
    const trim = (text: string | null | undefined) => text?.trim() || null;
    const next: MockPreview = {
      ...found,
      rows: found.rows.map((row) =>
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
    savePreview(next);
    return HttpResponse.json<ImportPreviewResponse>(previewResponse(next));
  }),

  // Save as draft of a Jira preview (AC-004.1, AC-004.7): no validation; a CSV preview has no Draft (404).
  http.post('*/api/v1/imports/previews/:previewId/draft', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const found = findPreview(params.previewId);
    if (found?.source !== 'JIRA_IMPORT') return problem(404, 'MSG04');
    const savedAt = new Date().toISOString();
    savePreview({ ...found, draftSavedAt: savedAt });
    return HttpResponse.json<DraftSavedResponse>({
      previewId: found.previewId,
      savedAt,
      message: { code: 'MSG41', params: {} },
    });
  }),

  http.post('*/api/v1/imports/previews/:previewId/confirm', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const found = findPreview(params.previewId);
    if (!found) return problem(404, 'MSG04');
    const { validCount, errorCount, removedCount } = previewResponse(found);
    if (validCount === 0) return problem(409, 'MSG30', { params: { count: errorCount } });
    if (found.fileName?.includes('inactive-project')) return problem(409, 'MSG40');
    if (found.fileName?.includes('import-fails')) return problem(500, 'MSG33');
    // A finished Draft is deleted with its preview (AC-003.10).
    if (found === preview) isConfirmed = true;
    else drafts = drafts.filter((draft) => draft !== found);
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
