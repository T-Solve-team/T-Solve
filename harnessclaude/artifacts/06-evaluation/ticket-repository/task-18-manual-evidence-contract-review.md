---
module: ticket-repository
feature: manual-evidence-contract
task: 18
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 18: Evidence contract and schema

## Summary
Commit default@ef484f0b changes the contract of the two manual-entry writes to `multipart/form-data`. Part `entry` is
a `ManualTicketRequest` with `keepEvidenceIds`, and part `files` holds 0–5 binaries. Both writes gain a 503. The
`ManualTicketDraft` gains `evidence` (`ManualEvidence[]`, required). The commit also adds changeset `031-manual-entry-evidence`
(`evidence.storage_key` and table `import_preview_evidence`), the DTO records, the `TicketEvidence` /
`ImportPreviewEvidence` entities with a repository, and the regenerated client. The form and the MSW handlers are
adapted to send and read multipart. The ReviewStorageIT skip list gains 031.

All of this matches the spec's Interface Contracts "Evidence (Change 2026-11-14, D17)" and the Task 18 description.
Upload logic, the multipart controller and the Evidence UI belong to Tasks 19 and 20 and are not judged here. There
are no CRITICAL findings. **PASS.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `frontend/openapi/tsolve-api.yaml:1315-1335,1365-1385`: multipart with `entry` (required, `$ref ManualTicketRequest`, encoding `application/json`) and `files` (binary array, `maxItems: 5`); `'503'` on both operations (`:1355,1403`). `ManualTicketRequest.keepEvidenceIds` uuid[] or null (`:3084`). `ManualTicketDraft.required` includes `evidence` (`:3091`). `ManualEvidence {evidenceId uuid, fileName, fileSize int64}` (`:3125`). This is exactly what spec line 201 lists. |
| Completeness | pass | Every file in the Task 18 file list is present. `ManualEvidence` record, `ManualTicketRequest.keepEvidenceIds`, `ManualTicketDraft.evidence`; `getDraft` returns `List.of()` until Task 19 (`ManualTicketServiceImpl.java:208`). |
| Correctness | pass | Typecheck is clean. Vitest for ticket-import gives 123/123; one test failed on the first run under load and passed on the rerun (Informational 6). The orchestrator's backend unit tests and ITs report 0 failures (session log; Docker ITs were not re-run here, as instructed). |
| Integration | pass | `TicketEvidence` readers are unaffected: `TicketReviewMapper.evidence` maps only `id`, `fileName` and `fileSize` to `TicketReviewView.Evidence(id, filename, sizeBytes, url)`. MapStruct ignores the new source getters, and `TicketEvidenceRepository.findByTicketIdOrderById` is unchanged. The generated client and MSW match the YAML (`generate:api` reported no diff). |

### Schema (changeset 031)
- **New file:** it is a new changeset, not an edit of an applied one. 031 is the next free number on the branch and on
  `main` (last there: 030).
- **Rollback:** `DROP TABLE import_preview_evidence` and `ALTER TABLE evidence DROP COLUMN storage_key`. Both are
  correct, and the order does not matter.
- **`evidence.storage_key VARCHAR(100)` nullable:** this matches the design (review-module rows have no key). The key
  from `ObjectFileStorage.store` is a 36-character UUID (`ObjectFileStorage.java:95,103`), so 100 is enough.
- **`import_preview_evidence`:** it mirrors `evidence` from 008: `UUID PK DEFAULT gen_random_uuid()`, `file_name` 255,
  `file_type` 100, `file_size BIGINT` with `CHECK >= 0`. `storage_key NOT NULL` and
  `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` are added, with no `updated_at` (append-only, as AC-001.20 U3 allows).
  `preview_id BIGINT` has an FK to `import_preview(id)` with `ON DELETE CASCADE`, the same pattern as `import_preview_row`
  in 003. The `preview_id` index is there.
- **Cascade with the cleanup job:** the cascade deletes the rows when a Draft is deleted or published. `PreviewCleanupJob`
  and the owner bulk delete only touch non-Draft previews (`draftSavedAt is null`, `ImportPreviewRepository.java:25,29`).
  They therefore never cascade away Evidence rows without deleting the files. Deleting the files on Draft delete or
  publish is Task 19 (AC-006.19).

### Entities
- `ImportPreviewEvidence`: every column is mapped with matching names and nullability. The key is
  `@GeneratedValue(UUID)` + `@Column(name = "import_preview_evidence_id")`, as backend AGENTS.md §7 and AC-001.23 require.
  `@Immutable` still allows insert and delete, which is all D20 needs.
- `TicketEvidence`: gains `@GeneratedValue(UUID)`, `file_type`, `upload_date` (Java default `Instant.now()`, not
  updatable), `storage_key` and the factory `of(...)`. These are additions only; the existing getters are unchanged.

### ReviewStorageIT
The change adds `031-manual-entry-evidence` to both skip regexes, and is minimal and correct:
- **BUG_145 replay:** the "main before Review" changelog has no `008-review` and so no `evidence` table, so 031's
  `ALTER TABLE evidence` would fail there.
- **BUG_140 replay:** an older database must not hold a changeset newer than itself.

This follows the same pattern used for 030.

## Traceability
`sdd.ts trace ticket-repository STORY-006`: 14/22. AC-006.1…AC-006.14 are traced. AC-006.15…AC-006.22 are missing,
as expected: Task 18 is a contract and schema task with no AC of its own, and those criteria belong to Task 19
(backend, AC-006.15…AC-006.21) and Task 20 (web app, AC-006.22, AC-006.18 on screen). The trace also prints the
warning "AC-008.1 in tests but not in spec". That is pre-existing and not from this commit.

## Issues

CRITICAL: none.

Informational:
1. **Intermediate break.** Between this commit and Task 19, the real app cannot save. `ManualEntryForm` now sends
   multipart, but `ManualTicketController.java:43,53` still takes `@RequestBody` JSON, so the real backend would answer
   415. The plan accepts this ("the form may send an empty `files` for now"). It is harmless while the branch is
   unmerged, but Task 19 must land before any merge or demo.
2. **Part `entry` has no Content-Type.** The YAML says `encoding.entry.contentType: application/json`, but orval appends
   `entry` as a plain `JSON.stringify` string with no part Content-Type
   (`frontend/src/shared/api/generated/import/import.ts`, `formData.append('entry', JSON.stringify(...))`). With the
   design's `@RequestPart("entry") ManualTicketRequest`, Spring would answer 415. Task 19 must take
   `@RequestPart("entry") String` and parse it with ObjectMapper, as the session log already notes, or accept
   `text/plain` / `application/octet-stream`. A `@WebMvcTest` or IT should post through a real `FormData`-shaped request
   (a part without a content type) to pin this down.
3. **Operation summaries are stale.** The YAML summaries and 400 descriptions do not yet mention MSG34 or MSG72
   (400, `fieldErrors` on `files`) or MSG06 (503) (`tsolve-api.yaml:1310-1314,1360-1363,1388`). For
   `saveManualTicketDraft`, the 400 still says "No Project (MSG01 on projectId)" only. Suggested fix, in Task 19 or 20:
   add them to the summaries.
4. **`TicketEvidenceRepository` cannot save yet.** It extends `Repository` with one finder and no `save`. Task 19 will
   need `save` / `saveAll`, which is outside Task 18's file list.
5. **No row policy on the new table.** `import_preview_evidence` has no AC-001.29 row policy. No tenant table in the
   changelog has one yet, and the convention guard test (AC-001.22, AC-001.31) does not exist in the code base yet. The
   table otherwise follows AC-001.20, so this is not a finding against this task. It joins the platform's backlog with
   the other tables.
6. **Flaky test.** On the first `vitest run src/features/ticket-import`, 1 of 123 tests failed after 206 s, under load
   from the typecheck that ran just before it. An immediate rerun passed 123/123. This looks like a timing flake in a
   ticket-import test, not this commit's code. The failing test's name was not captured.

## Assumptions made
- Task 18 is judged against the spec's Interface Contracts and backend AGENTS.md §7. It owns no AC, so the missing
  AC-006.15…AC-006.22 tests are not a FAIL here (dispatcher's scope).
- The broken interim state between Tasks 18 and 19 (Informational 1) is accepted because the plan explicitly allows it.
  Judgment call for the user: whether Tasks 18 and 19 should be pushed together.
- The missing RLS row policy (Informational 5) is not counted against this task, because AC-001.29 is not yet built for
  any table.

## Test Report
See `task-18-manual-evidence-contract-test-report.md`.
