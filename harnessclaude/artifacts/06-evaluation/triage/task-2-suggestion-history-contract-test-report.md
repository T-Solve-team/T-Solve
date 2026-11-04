# Test Report: triage — Task 2: suggestion-history-contract

**Date:** 2026-11-04
**Code state tested:** `default` repo at `bef5072` (Task 2 commit). HEAD `fd2c1f6` changes harness files only. Tasks 1 and 2 share this code state, and this is the same run as `task-1-suggestion-history-test-report.md`.
**Commands** (from `frontend/` unless noted): `npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run build` · `npm run generate:api` + `git status --porcelain src/shared/api/generated` · `npx vitest run src/features/triage src/app/router/route-table.test.tsx` · `npx vitest run --maxWorkers=2` · root: `bun harnessclaude/hooks/sdd.ts trace triage STORY-001 STORY-002`

## Results

| Check | Exit | Key output |
|-------|------|------------|
| `npm run format:check` | 0 | `All matched files use Prettier code style!` |
| `npm run lint` | 0 | `✖ 1 problem (0 errors, 1 warning)`; the warning is in `src/features/tickets/components/TicketListFilters.tsx:23`, not triage |
| `npm run typecheck` | 0 | no output |
| `npm run build` | 0 | `✓ built in 3.48s` |
| `npm run generate:api` | 0 | the regenerated client has no diff: `git status --porcelain src/shared/api/generated` is empty |
| Targeted run, default workers (×2) | 1 | `Tests 71 passed \| 8 skipped (79)` both times. `history-filters.test.ts` setup hook timed out |
| `history-filters.test.ts` alone | 0 | `Tests 8 passed (8)` |
| Targeted run, `--maxWorkers=2` | 0 | `Test Files 4 passed (4)` · `Tests 79 passed (79)` |
| Full suite `npx vitest run --maxWorkers=2` | 1 | `Test Files 1 failed \| 70 passed (71)` · `Tests 1317 passed \| 2 skipped (1319)` · 1045.86s. The failed file is `src/app/dev/mock-mode.test.tsx` (setup hook timeout). It passes 2/2 alone |
| `sdd.ts trace triage STORY-001 STORY-002` | 0 | `20/20 acceptance criteria have a test` |

- Typecheck: pass · Lint: pass · Build: pass · Deterministic generation: pass.

### Contract change checks (2026-11-04)
- `openapi/tsolve-api.yaml`: `SuggestionProject {projectId (uuid), key}` is required in both the summary and the detail. `issueSummary` is gone from the schemas, generated models, mocks and UI. `grep -rn issueSummary frontend/src` finds nothing in triage or the generated client. `jiraIssueUrl` has type `[string, 'null']`, and the generated model is `jiraIssueUrl: string | null`.
- The ids keep the session id type (`format: uuid`, same as `ProjectMembership.projectId`), as the spec's third changelog row requires.

## Coverage

| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line/branch coverage | not measured (no coverage script) | none set. NFR-1 only needs an AC-named test for every criterion |

## Acceptance Criteria → Tests

```
trace triage (STORY-001, STORY-002): 20/20 acceptance criteria have a test
```
The per-AC file list is identical to the Task 1 report. Trace checks test names only. The test named AC-001.11 at `mocks/handlers.test.ts:69` no longer checks the Workspace clause: the string it asserts on at line 77 was removed from the mock data by `bef5072`. See the review, Issue 1.

## Failures

### `src/features/triage/hooks/history-filters.test.ts`: whole suite (targeted run, default workers, 2 of 2 runs)
- Error: `Error: Hook timed out in 10000ms.` at `src/test/setup.ts:103:1`. 8 tests skipped, 0 failed.
- Cause: load in the shared test setup, which dynamically imports every mock handler. Triage code is not the cause. The file passes alone and with 2 workers.
- Suggested fix: change the shared setup (FE Lead) and log the bug in `docs/bug-log.md`.

### `src/app/dev/mock-mode.test.tsx`: whole suite (full run, 2 workers)
- Same setup hook timeout. Not a triage file. Passes 2/2 alone.

## Notes
- No triage assertion failed in any run.
- Task 2 left `mocks/handlers.test.ts` unchanged, though `tasks.md` Task 2 lists AC-001.11 among the tests to update first.
- Not independently verified: the browser check at 1366 × 768.
