# Test Report: dashboard-audit — Task 1: audit-log

**Date:** 2026-10-31
**Branch:** feat/KAN-153-dashboard-audit-view-filter
**Scope:** frontend-only KAN-153; user-authorized code commit `85a8aeb`.

## Results

| Check | Result | Evidence |
|-------|--------|----------|
| npm run format:check | PASS | All matched files use Prettier code style |
| npm run lint | PASS | 0 errors, 1 existing warning in TicketListFilters.tsx |
| npm run typecheck | PASS | tsc -b --noEmit, exit 0 |
| Complete frontend suite | PASS | 52 files, 965 passed, 0 failed, 0 skipped; 563.94 seconds |
| Feature-only suite | PASS | 3 files, 33 tests passed |
| npm run build | PASS | Production bundle built successfully; 1.40 seconds bundling |
| npm run generate:api | PASS | Second generation: 0 changed files, SHA256 comparison over all generated files |
| sdd.ts check dashboard-audit | PASS | 1 story, 17 acceptance criteria, OK |
| sdd.ts trace dashboard-audit STORY-001 | PASS | 17/17 criteria have tests |
| git diff --check | PASS | No whitespace errors |
| npm audit --omit=dev --json | PASS | 0 vulnerabilities |
| npm audit --json | BASELINE ISSUE | 19 moderate + 2 high development-toolchain advisories, BUG-069 |

## Execution Details

Project npm commands used D:/node.js on PATH. The successful complete suite used the project's Vitest
configuration with `node node_modules/vitest/vitest.mjs run --no-cache --maxWorkers=2 --reporter=json
--outputFile=node_modules/.cache/kan153-results.json`. Two workers change scheduling only; all tests run
with their existing timeouts. TEMP/TMP point into ignored node_modules/.cache/kan153-temp because the
constrained process environment initially could not read Vitest's temporary transformed modules.

The initial project `npm run test -- --no-cache` invocation exited 1 with review-flow failures. Its
compressed output was not recoverable through RTK recall. The complete diagnostic rerun above preserved
JSON and stderr under ignored node_modules/.cache and passed unchanged source/tests. This result
difference is logged as BUG-070; no failing test was removed, weakened or skipped.

The expected initial AC-001.1 red test failed on the placeholder screen. Later AC-001.5 exposed the
descending-default sort defect (BUG-068); the local page handler correction made the regression pass.

## Coverage

No coverage command or coverage threshold is configured for this frontend (guideline 10). Behavior
coverage is tracked by the spec criterion tests, including negative role, invalid date, cross-Workspace,
error/retry and pagination cases. No percentage threshold is invented.

## Acceptance Criteria → Tests

`trace dashboard-audit (STORY-001): 17/17 acceptance criteria have a test`.

- AC-001.1–AC-001.10, AC-001.12–AC-001.16: pages/AuditLogPage.test.tsx.
- AC-001.8–AC-001.11, AC-001.15–AC-001.17: mocks/handlers.test.ts.
- AC-001.14–AC-001.16: hooks/audit-filters.test.ts.
- Exact criterion/evidence mapping: task-1-audit-log-review.md.

## Browser Verification

Mock preview at http://127.0.0.1:5174/audit-log:
- Admin sees the five-column table, 20-row first page, totals and filters.
- Email search FORMER, action Import and inclusive 01/10/2026 date range show exactly one entry at 23:59.
- The date pair is written atomically as from/to calendar strings in the URL.
- Direct Employee navigation to /audit-log shows 403/MSG08.
- Filtered screen browser error log: empty.
- At the default 1280px viewport, controls fit and the table provides horizontal scrolling.
- Screenshot: artifacts/assets/dashboard-audit/kan-153-filtered.jpg.

## Notes

The test environment emits existing jsdom getComputedStyle pseudo-element warnings; the final suite has
no failures. No backend tests were run because no backend source changed. Backend implementation and
production tenant isolation are outside this approved slice. Package manifests/lockfile are unchanged.

## PR #92 main integration — 2026-10-31

Integrated main 4cf05949; the only manual resolution retains both parents in events.jsonl.
All 414 lines parse as JSON; each parent's complete event set is present. Frontend files are unchanged
from the previously verified db7d46c tree; backend files match origin/main exactly. Fresh Audit Log run:
33/33 tests passed, 0 skipped. Fresh format, lint (same existing warning), typecheck, build and API
regeneration pass; no generated-file drift. Spec approval is unchanged and 17/17 trace still passes.
