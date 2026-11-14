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
  ManualDraftResponse,
  ManualEntryRequest,
  ManualEntryResponse,
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
/** The PM's Manual Drafts (STORY-007); none at the start. */
let manualDrafts: ManualDraftResponse[] = [];
let lastManualId = 950;

/** Back to the starting preview — called after every test (src/test/setup.ts). */
export function resetMockData(): void {
  preview = seedImport();
  isConfirmed = false;
  lastBatchId = 0;
  drafts = seedDrafts();
  manualDrafts = [];
  lastManualId = 950;
}

/** The `entry` part and the `files` parts of a manual entry request. */
async function readManualEntry(request: Request) {
  const form = await request.formData();
  const entry = form.get('entry');
  return {
    entry: JSON.parse(typeof entry === 'string' ? entry : '{}') as ManualEntryRequest,
    files: form.getAll('files').filter((file): file is File => file instanceof File),
  };
}

/** The Manual Draft a request names, or undefined when it names none. A missing one answers 404. */
function namedManualDraft(entry: ManualEntryRequest): ManualDraftResponse | undefined | null {
  if (entry.draftId === undefined || entry.draftId === null) return undefined;
  return manualDrafts.find((draft) => draft.previewId === entry.draftId) ?? null;
}

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
    const isManual = manualDrafts.some((draft) => String(draft.previewId) === params.previewId);
    if (!isManual && !drafts.some((draft) => String(draft.previewId) === params.previewId)) {
      return problem(404, 'MSG04');
    }
    drafts = drafts.filter((draft) => String(draft.previewId) !== params.previewId);
    manualDrafts = manualDrafts.filter((draft) => String(draft.previewId) !== params.previewId);
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

  // Manual entry (UC-22): Ticket ID "T-TAKEN" answers MSG31 and "T-FAIL" MSG33.
  http.post('*/api/v1/imports/manual', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const { entry } = await readManualEntry(request);
    if (namedManualDraft(entry) === null) return problem(404, 'MSG04');
    if (entry.ticketId === 'T-TAKEN') {
      const params = { ticket_id: entry.ticketId };
      return problem(409, 'MSG31', {
        params,
        fieldErrors: [{ field: 'ticketId', code: 'MSG31', params }],
      });
    }
    if (entry.ticketId === 'T-FAIL') return problem(500, 'MSG33');
    manualDrafts = manualDrafts.filter((draft) => draft.previewId !== entry.draftId);
    return HttpResponse.json<ManualEntryResponse>(
      {
        ticketId: '00000000-0000-4000-8000-000000000a01',
        message: { code: 'MSG32', params: { created: 1, skipped: 0 } },
      },
      { status: 201 },
    );
  }),

  http.post('*/api/v1/imports/manual/drafts', async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const { entry, files } = await readManualEntry(request);
    if (!entry.projectId) {
      return problem(400, 'MSG01', {
        params: { field_name: 'projectId' },
        fieldErrors: [{ field: 'projectId', code: 'MSG01', params: { field_name: 'Project' } }],
      });
    }
    const before = namedManualDraft(entry);
    if (before === null) return problem(404, 'MSG04');
    const keep = entry.keepEvidenceIds;
    const kept = (before?.evidence ?? []).filter((file) => !keep || keep.includes(file.id));
    const added = files.map((file, index) => ({
      id: `00000000-0000-4000-8000-${String(Date.now() + index)
        .padStart(12, '0')
        .slice(-12)}`,
      fileName: file.name,
      fileType: file.name.slice(file.name.lastIndexOf('.') + 1).toLowerCase(),
      fileSize: file.size,
    }));
    const saved: ManualDraftResponse = {
      previewId: before?.previewId ?? ++lastManualId,
      projectId: entry.projectId,
      ticketId: entry.ticketId ?? null,
      title: entry.title ?? null,
      description: entry.description ?? null,
      resolvedDate: entry.resolvedDate ?? null,
      symptoms: entry.symptoms ?? null,
      rootCause: entry.rootCause ?? null,
      resolutionSteps: entry.resolutionSteps ?? null,
      workaround: entry.workaround ?? null,
      scope: entry.scope ?? null,
      evidence: [...kept, ...added],
      savedAt: new Date().toISOString(),
    };
    manualDrafts = [saved, ...manualDrafts.filter((draft) => draft.previewId !== saved.previewId)];
    return HttpResponse.json<DraftSavedResponse>({
      previewId: saved.previewId,
      savedAt: saved.savedAt,
      message: { code: 'MSG41', params: {} },
    });
  }),

  http.get('*/api/v1/imports/manual/drafts/:previewId', async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const found = manualDrafts.find((draft) => String(draft.previewId) === params.previewId);
    return found ? HttpResponse.json<ManualDraftResponse>(found) : problem(404, 'MSG04');
  }),
];
