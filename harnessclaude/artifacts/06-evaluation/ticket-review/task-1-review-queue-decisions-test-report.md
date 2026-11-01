# Test Report: ticket-review — Task 1: review-queue-decisions

**Date:** 2026-11-01
**Scope:** STORY-001–005 queue/contracts; detail UI callers and STORY-006 wait for Task 2.
**Commands:** frontend `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test -- --no-cache --maxWorkers=2 --reporter=json --outputFile=node_modules/.cache/kan24-full-final.json`, `npm run build`, `npm run generate:api`.

## Results
- Final full suite after BUG-084 synchronization: **1,064 total / 1,064 passed / 0 failed / 0 skipped**, 135/135 suites, **347.9 seconds**, exit 0. JSON: `frontend/node_modules/.cache/kan24-full-final.json`.
- Previous full suite: 1,064 total / 1,064 passed / 0 failed / 0 skipped, 402 seconds. Earlier full run: 1,063 passed / 1 failed (Reject removal wait); focused rerun: 9/10 (Request Changes clicked before eligibility). These failures are tracked as BUG-084, not suppressed.
- Focused review suite before synchronization: 52/52; independent mock reviewer: 31/31. After synchronization: queue 10/10.
- Fresh format/typecheck/lint/build after test synchronization: pass, exit 0. Lint has zero errors and one pre-existing `TicketListFilters.tsx` fast-refresh warning.
- Build: exit 0. API generation: exit 0 and `API_GENERATION_IDENTICAL` from before/after file-path + SHA-256 comparison. No generated file hand-edits.
- `git diff --check`: exit 0. No timeout increases, skip additions, disabled assertions, or global test/CI changes.

## Coverage
No coverage script or coverage threshold is adopted by frontend guideline 10. Meaningful criterion tests and browser checks are used; no percentage is claimed.

## Acceptance Criteria → Tests
`bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-001 STORY-002 STORY-003 STORY-004 STORY-005`: 25/25 named criteria.

| Criteria | Evidence |
|---|---|
| AC-001.1, 001.5 | Mock scope/auth matrix; queue default order/count; existing route guards remain in force |
| AC-001.2 | Department URL test; browser sort `sourceTicketId,asc` and page 2 URLs with changed rows |
| AC-001.3, 001.6 | Select-all/filter/Back/cached/deferred-response tests; browser 1 selected across sorting/paging and 55 selected across all remaining pages |
| AC-001.4 | Empty/error/retry/loading tests |
| AC-002.1–002.3, 002.5 | Single/bulk/cancel tests, self-authored managed mock approval, one history entry per successful Ticket |
| AC-003.1–004 | Required/raw-length/cancel UI tests, rejection state/reason, terminal rejected batch tests |
| AC-004.1–004 | Shared comment UI/mock tests, raw-length validation, MSG18-before-status guard; detail explanation deferred |
| AC-005.1–003 | UUID payload test, zero/duplicate/malformed/500/501 boundary, atomic missing/unmanaged/foreign/stale snapshots |
| AC-005.4–006 | Settled mutation invalidation, stale eligible retry, entered-value preservation, duplicate blocking, popup closing before deliberately blocked refetch |
| AC-005.7 | Actor/action/timestamp/ticket/reason/comment records; Solution/Evidence/Jira status/due-date preservation; failures preserve snapshots/history |

## Browser checks
Edge, real Vite/MSW, 1366 × 768: queue loaded; single Approve removed ticket; bulk confirmation cancelled while retaining selection; Request Changes removed both selected tickets; Reject displayed required Reason then removed a valid submission; sort URL/server rows changed, page 2 retained one selected ticket; Select all returned 55 ids after decisions; Department reset selection/page, Back kept zero selected. Keyboard submit was exercised. After a fixture reset, confirmed bulk approval removed both links, showed zero selected and total 56. Saved queue, validation and bulk-success screenshots under `artifacts/assets/ticket-review/`; temporary viewport override reset.

Browser checks do not simulate production authorization, durable audit or Jira/email delivery. Query failure/stale/refetch blocking is verified with deterministic MSW tests. Ticket Detail UI and narrow-width inspection are Task 2 work.

## Failures and warnings
- BUG-084 test synchronization: verified by queue 10/10 and final full suite 1,064/1,064. No business assertion or timeout was relaxed.
- Existing jsdom pseudo-element `getComputedStyle` notices occur; no new ignore/filter was added.
- Existing BUG-073–079 rows remain untouched; the passing full run did not reproduce them.

## User-action quick-change verification — 2026-11-01

Subsequent user request expanded PR #100 with a five-file quick fix for the users action-name regressions. This supersedes the previous note about leaving BUG-022/073/075–079 unchanged.

- Red: `users-action-name-red.json`, 12/16 passed; exact Save/Deactivate lookups fail with the default spinner. A fourth failure exposed cleanup after the intentionally failing pending-create assertion; its released mutation is now awaited in finally.
- Green: `users-action-name-green.json`, 167/167 for users + shared FormModal + site-state; independent modal/create rerun exits 0.
- Full: `npm run test -- --no-cache --reporter=json --outputFile=node_modules/.cache/users-action-full-green.json`, 1,066/1,066 passed, zero failed/skipped, exit 0, using the project default worker setting.
- Format/lint/typecheck/build: exit 0; lint retains the existing TicketListFilters fast-refresh warning. `npm run generate:api` plus `git diff --exit-code -- src/shared/api/generated`: exit 0.
- Review found no blocking issues. Explicit accessible names remain stable through pending and spinner exit; busy/disabled state, entered values, successful retry and single-request protection are asserted. No timeout increase or weakened checks.
- Original CI site-state failure remains BUG-085 Open: pre-fix local users/site run 165/165, shuffled site-state seed 42 11/11, current combined/full runs pass. A successful CI rerun is not treated as proof that this separate intermittent failure was fixed.

## Main integration verification — 2026-11-01

- Main `c4d0a30` adds profile, feedback, tracker and MSW aborted-fetch regression work. Seven metadata/document conflicts were resolved; source OpenAPI/generated auto-merges preserve both feature sets and regenerate without additional diff. Independent review confirms no lost main definitions or branch review requirements.
- Initial cold default-worker run: 802 passed, 6 failed, 309 pending; seven setup hooks timed out at the unchanged 10-second bound. Other failures were four ReviewQueuePage cases and two UserStatusPage handover cases. Evidence: `kan24-main-sync-full.json`. Logged as follow-up evidence on existing BUG-070, not hidden or converted to passing.
- Same source, full run with bounded parallelism: `npm run test -- --no-cache --maxWorkers=2 --reporter=json --outputFile=node_modules/.cache/kan24-main-sync-full-2workers.json` → **1,117 passed / 0 failed / 0 skipped**, 146/146 suites, 650 seconds, exit 0. All tests and timeout/CI settings remain intact.
- Fresh format, lint, typecheck, build and API generation: pass, with the existing lint warning only. Ticket-review spec check/diff passes and still matches the user approval.
- Unrelated baseline audit failures remain on main's platform spec; no platform approval/state was fabricated to bypass them. PR #100 could not start CI until the main conflicts were resolved; its resulting head will trigger the next run.
