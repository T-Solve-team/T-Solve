# Task 3: Expired Tickets (KAN-158)

**Module:** ticket-review
**Assignee:** Phạm Tùng Dương
**Iteration:** 1
**Feature:** expired-tickets
**Status:** implementation verified by focused checks; parent full verification pending

## Session 2026-11-01

### Working on: expired-tickets

- Implemented STORY-007 / AC-007.1–007.8 on feat/KAN-158-review-expired-tickets based on explicitly authorized KAN-24 a4500fb.
- Added proposed generated GET expired-tickets / ticket-ids and POST reviews/send-back contracts. New expiry metadata schemas retain UUID strings and existing page/size/sort shapes. No production backend endpoints implemented.
- Extended the existing reviewStore with 24 expired fixtures, previous status, expiry timestamps, current per-Project review ranges and SEND_BACK history. Mock mutations validate the entire unique 1–500 UUID batch before writes; scope and status errors leave tickets/history unchanged.
- Recalculate review due dates from the Vietnam send-back calendar date plus current Project range (GBR-10), preserving Solution and Evidence.
- Completed the existing Expired Tickets page with Department URL state, sortable paginated table, inherited Ticket Detail links, cross-page/all-filtered selection, freshness reconciliation, selection limits, pending guards and catalog error/retry/empty behavior. Generated query keys invalidate affected Expired, queue, detail and Ticket List data without delaying successful mutation feedback.
- Reused current review table/toolbar styles and shared UI. No route/app/shared runtime edits or new dependencies. No KAN-159/108/154 implementation, commits or pushes.

### TDD evidence

RED: node node_modules/vitest/vitest.mjs run src/features/review/pages/ExpiredTicketsPage.test.tsx — both initial tests failed by assertion: Unable to find role=table, against the existing ScreenPlaceholder (session54929, exit1). No implementation existed at that point.

GREEN: node node_modules/vitest/vitest.mjs run src/features/review/pages/ExpiredTicketsPage.test.tsx src/features/review/mocks/expired-handlers.test.ts --reporter=json --outputFile=../.tmp-kan158-tests.json — 12/12 tests passed (8 page, 4 mock), session47522 exit0. Temporary JSON report inspected for exact pass/fail counts, then removed. Covers managed scope, URL/filter/sort/paging, all/individual cross-page selection, browser Back reset, oversized/duplicate/pending submission, failed mutation retained selection, conflict pruning, success plus failed refetch, empty/error/retry, strict batch rejection and no partial writes, foreign/unmanaged/role failures, current range/calendar rollover, content/history and downstream queue/detail outcomes.

Typecheck: node node_modules/typescript/bin/tsc -b --noEmit — exit0.
Scoped lint: node node_modules/eslint/bin/eslint.js src/features/review/pages/ExpiredTicketsPage* src/features/review/hooks/useExpired* src/features/review/mocks/expired-handlers* — exit0 (session9629).
Formatting: changed feature files and OpenAPI formatted with the repository's Prettier; orval regenerated after the final contract descriptions.

Tests emit the existing jsdom getComputedStyle pseudo-element warning; no runtime-error suppression, timeout inflation or skipped test was introduced. Test setup issues were corrected by waiting for initial requests to settle before replacing handlers, wrapping external cache/router changes in act and distinguishing the success toast from the list-error alert. Eligibility no longer redundantly refetches an already-accepted list version immediately after Select all.

### Remaining verification / next up

Parent performs fresh full frontend checks, deterministic API generation, browser QA and trace, then records final evaluation. Browser/full-suite outcomes must be reported separately rather than inferred from focused checks. User commit checkpoint applies before KAN-159; proposed code message: feat(review): add expired ticket workflow (KAN-158), body STORY-007 and AI co-author trailer. Preserve unrelated local BUG-087 and research/registry work outside this feature's scoped commit.

### Debug investigation: parent full-suite failures

Parent reported 1146/1148 tests passing with AC-006.5 Evidence and AC-002.2/.5 bulk confirmation failing at their default async assertions. Read HarnessClaude and Addy root-cause debugging guidance before edits. Compared fixture changes: 24 appended EXPIRED UUID300+ records do not alter the existing 58 pending records or first detail record. No detail renderer or original pending-decision logic was changed by KAN-158. Full focused run of ReviewQueuePage.test.tsx and ReviewTicketDetail.test.tsx exits0 (session58966), without source/test changes. Separate focused reproduction of the two failing cases exits0 (ff75a6): Evidence case787ms, bulk case3039ms total interaction. Root cause is not proven; full-suite scheduling/load and queue polling/eligibility during the modal remain hypotheses. No timeout increase, weakened assertion, skipped test or speculative production fix was made. Parent must disclose observed failures and gather isolated full-suite evidence before claiming full verification.

Trimmed only redundant EOF whitespace in OpenAPI after generation; diff --check now passes with no semantic contract change.

### Follow-up: deterministic regression-test synchronization (BUG-089 / BUG-090)

Parent authorized narrow test-synchronization fixes within the currently approved module's original criteria after observing two full-suite failures. No production code or timeout changed. AC-006.5 now awaits actual router navigation plus generated /me and Ticket Detail queries in act, then applies the same synchronous filename/size/safe-reference assertions. An intermediate detail-only ensure exposed the missing route/session prerequisite by immediately asserting against the still-empty DOM; the final version waits all actual prerequisites without seeding data. AC-002.2/.5 now awaits actual eligibility refetch, confirms synchronously, and waits actual history/mutation settlement before unchanged success/count assertions. Both observed test defects logged as BUG-089/090 with named regressions.

Final focused verification: complete ReviewTicketDetail.test.tsx plus ReviewQueuePage.test.tsx, session53042 exit0, JSON29/29 passed. BUG-089 case618ms and BUG-09011567ms total interactions during the parent's concurrent full run. Scoped ESLint0 errors. Source/tests frozen pending parent full-suite outcome. Original unsafe-link and rejected/approval/cancellation checks remain intact; no fixture, mock, production, skipped-test or test-timeout changes were used to obtain this pass.

### Parent verification follow-up: BUG-091

The next full suite passed the repaired KAN-24 tests but failed the new cross-page Expired test: 1147/1148 passed, EXP-303 was not yet rendered after router.navigate. Router navigation does not await feature query completion. Parent awaited the real active Expired list refetch by its generated key inside act after page navigation and browser Back, then retained the original link and selection assertions. No production behavior, mock records, timeout values or assertions were weakened. Independent reviewer found no weakening. Focused and final full results are recorded in the companion test report.

BUG-091 refinement: the active-query-refetch approach still failed in the next full suite (1147/1148); its accessible DOM showed page-one links EXP-323 through EXP-304, proving the active observer could still be the previous page. The test now preloads genuine page-two data through generated ensureQueryData/MSW before navigating, then retains all individual/off-page and browser-Back assertions. Server-side pagination remains independently asserted by the mock contract test. Final focused page file exits0; independent review confirms no fake data or weakening. Production and timeout values remain unchanged. Final full suite uses ignored frontend/.vitest/tmp for TEMP/TMP because one escalated test launch's automatic approval review timed out; no security gate or spec approval was bypassed.

### Test action-cost investigation and synchronization follow-up

Parent standard-host full JSON reported five failures: one Expired recovery wait and four original ReviewQueuePage tests (bulk confirm, Reject required validation, RequestChanges and SelectAll). This task addressed only ReviewQueuePage; parent owns the Expired wait fix. Measured unchanged focused RequestChanges test body around10.4s and full failure15073ms; diagnostic timer confirmed per-character Comment typing alone1643ms in a focused run. The temporary diagnostic exception was removed before verification. No production cause was established.

Replaced userEvent hover/pointer checkbox and modal click sequences with enabled-guarded native fireEvent.click; replaced Reason/Comment per-character loops with native fireEvent.change carrying identical strings. These tests assert form/selection outcomes, not keyboard-specific behavior; real React handlers, MSW requests, validation, confirmation/cancellation, history and original toast/count assertions remain. Added real mutation settlement waits for RequestChanges and actual eligibility completion for SelectAll. Existing Department dropdown interaction still uses userEvent.

Final focused command: node node_modules/vitest/vitest.mjs run src/features/review/pages/ReviewQueuePage.test.tsx --reporter=json --outputFile=.vitest/kan158-queue-native.json —10/10 pass (session49160). Final focused timings: bulk7829ms; Reject6188ms; RequestChanges6059ms; SelectAll4223ms. This is focused evidence only; it is not a full-suite pass. Scoped ESLint and tsc -b --noEmit exit0 (session14203). Notes added to existing BUG-084/090 rows; no skipped assertion, deadline increase, worker/config change, dependency or production edit. Source/tests frozen for parent standard full verification.

### Final parent verification — 2026-11-01

Full standard-host suite on frozen final source: node node_modules/vitest/vitest.mjs run --reporter=json --outputFile=.vitest/kan158-final-verification.json — exit0, 1148/1148 passed, 0 failed/pending, 152 suites across65 files, 286383ms. Parent also verified final format:check, whole lint (zero errors, one existing TicketListFilters warning), typecheck, build, deterministic API generation, 8/8 criterion trace and independent review. No assertions, timeout limits, workers or CI settings were weakened. Final code graph:7074 nodes/14716 edges. Detailed diagnostic history and test-only BUG089–092 repairs are in the test report and bug log.

User preference: subsequent verification is automated tests/CLI only, with no manual browser experiments unless explicitly requested. KAN-158 remains In Progress on Jira pending the user's feature commit agreement. No commit/push/merge/PR has been performed. Proposed code commit: feat(review): add expired ticket workflow (KAN-158), body STORY-007 plus AI co-author trailer; harness evidence is a separate commit. Do not start KAN-159 before this checkpoint. Preserve unrelated BUG087/088, registry and research edits outside scoped staging.

### Approved feature commit

User approved commit and PR creation. Code commit: default@09af635. Pre-commit secret scan, lint-staged and commitlint passed without bypasses. Feature moved through evaluating to passing using sdd.ts; module remains implementing for subsequent review features. PR targets the explicitly authorized KAN-24 dependency branch. KAN-159 starts only after this code/harness checkpoint.


## PR #103 integration review — 2026-11-01

User requested latest main integration, conflict resolution, final KAN-158 scope audit and inclusion of two existing-flow fixes where safe. Base main a5b5a53 is now an ancestor of this branch. Integration commits: cd4e449 (main merge), 521a060 (queue eligibility), 8abd654 (account-status test synchronization). All ten conflicts resolved. Main's ce1e400 Ticket Detail production code is preserved exactly; KAN-238 My Solutions has no diff from main. Historical Evidence synchronization bug is renamed BUG-096 to preserve main's toolbar BUG-089.

Independent final source review: AC-007.1–007.8 PASS in approved frontend/proposed API/MSW scope. The audit caught BUG-095: default descending expiry header trapped descending and mock dates sorted lexically across offsets. Both meaningful regressions failed before the minimal local corrections and passed afterward. Existing review/users/detail focused cases passed; initial integrated run103/104, then corrected new UI test to await real generated query and render, targeted BUG-0952/2 pass. No production change to user account logic, no shared runtime changes, no timeout/worker/config or assertion weakening, no browser tests.

Final static evidence: format, lint (0errors, one existing TicketListFilters warning), typecheck and build pass. Orval131 generated-file hashes unchanged. Graphify7164nodes/14877edges, four SQL files skipped due known missing parser. Final standard full frontend test run is pending in node_modules/.cache/pr103-final-verification.json; do not infer all-green verification from focused tests.

GitHub connector update permission returns403, so PR title/body cannot be edited through that connector. Proposed title: feat(review): add expired ticket workflow (KAN-158). Keep backend persistence/JOB-02/SSE outside this frontend PR and disclose test outcome before merge.

### Final full verification

Standard whole frontend run:1280 total,1278 passed,2 failed,0 pending; exit1. Both failures outside this PR delta: KnowledgeChatbotPage AC-002.9 timeout (BUG097) and MySolutionsSection edge63 SUP140 missing (BUG098). Source for both features matches origin/main exactly. All review, expiry and account-status cases pass. No global-green or merge-ready claim. User authorized push and conflict resolution; publish branch with this limitation disclosed. GitHub metadata write403 remains.
