# Test Report: ticket-review — Task 2: review-ticket-detail

**Date:** 2026-11-01
**Scope:** STORY-006 and Ticket Detail callers of STORY-002–005, approved frontend/MSW slice.
**Commands:** In frontend: `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run test -- --no-cache --maxWorkers=2 --reporter=json --outputFile=node_modules/.cache/kan24-detail-full.json`, `npm run build`, `npm run generate:api` and `git diff --exit-code -- src/shared/api/generated`.

## Results
- New-PR base verification: after #100 merged, transferred the approved slice to `feat/KAN-24-ticket-review-detail` from main `81d64dd`. Main adds only BUG-086 renumbering in two existing test names and related docs relative to the previously tested base; application behavior is identical. `npm run test -- src/features/review --maxWorkers=2 --reporter=json --outputFile=node_modules/.cache/kan24-detail-new-main-review.json`: **71/71 passed, zero failed/skipped, exit 0**. Code commit `47c2355` passed gitleaks, staged lint/format and commitlint hooks. Full-suite evidence below predates only that label/documentation renumbering; no redundant full run was made.
- Final full rerun after the BUG-084 test correction and exact restoration of the mutation experiment: **1,136/1,136 passed, 0 failed, 0 skipped, 148/148 reported suites across 63 files, 581.4 seconds, exit 0**. Command uses the same flags with outputFile `node_modules/.cache/kan24-detail-full-final.json`. All users files, ReviewQueuePage and ReviewTicketDetail pass.
- First full suite: 1,135 passed / 1 failed / 0 skipped. Existing ReviewQueuePage BUG-084 Request Changes could not find its dialog. An unchanged focused rerun passed 10/10; it remains a reported failure, not a clean full-run result. Corrected the enabled-check/activation race in the test helper; final full rerun is required before passing status or commit.
- Format, lint, typecheck and build: exit 0. Lint reports one existing fast-refresh warning in TicketListFilters.tsx and zero errors.
- API regeneration: exit 0, with no generated diff. Task 2 makes no OpenAPI or generated-client changes.
- Focused implementation tests: 23/23 (19 detail cases plus four existing shared decision-form cases). Evidence: `frontend/node_modules/.cache/kan24-detail-final.json`. Initial placeholder red run: 0/14.
- Root focused queue + detail after BUG-084 correction: 29/29, exit 0 (`kan24-detail-queue-green.json`). No runtime change: the helper checks enabled and fires one click synchronously, preventing the real five-second eligibility refresh from entering between userEvent hover and activation. The existing bug row is updated in this feature's scope.
- Mutation check: inverted the Pending review status condition; both selected nonpending tests failed on exposed Approve controls. Exact original bytes restored and SHA-256 matched; both selected tests then passed. JSON: `kan24-detail-mutant.json` and `kan24-detail-mutant-restored.json`. Their 17 filtered tests are intentional test-name filtering, with no source skips.
- `git diff --check`: exit 0. No timeout, skip, dependency, global test or CI configuration changes.
- Full run uses two workers because the previously recorded cold default-worker overload/setup failures are tracked under existing BUG-070. The unchanged post-main users subset already passed 154/154 with default workers before this feature. No claim is made that BUG-070 or the earlier intermittent CI BUG-085 is fixed by this feature.

## Coverage
The frontend has no adopted coverage script or percentage threshold. Named behavior tests and browser evidence are used; no percentage is claimed.

## Acceptance Criteria → Tests
`bun harnessclaude/hooks/sdd.ts trace ticket-review`: **31/31** named criteria. `sdd.ts check ticket-review`: six stories, 31 criteria, OK; approved spec remains unchanged.

| Criteria | Tests |
|----------|-------|
| AC-006.1 | Confirmed read-only Ticket, Solution and Evidence fields. |
| AC-006.2 | Pending request, invalid id without query, missing id, catalog error and successful retry. |
| AC-006.3, AC-002.3 | Managing PM self-approval without confirmation; nonpending actions absent; existing mock suite rejects forged/unmanaged decisions. |
| AC-006.4, AC-004.4 | Imported Published MSG18 accessible explanation without decisions. |
| AC-006.5 | Escaped filenames, readable size, relative/HTTPS references, no executable/protocol-relative links. |
| AC-006.6 | Unpublished content concealed from another reader; author sees own content; queue navigation absent for reader. |
| AC-003.1–003.3, AC-004.1–004.3 | Shared required form/cancel/success/retry tests; existing forms cover whitespace/raw length boundaries. |
| AC-005.4–005.6 | Stale 404/409 refresh, retained input through failed query refresh, duplicate-send prevention and success independent of query failure. |
| AC-005.7 | Immutable Solution/Evidence/Jira status/due date; history actor/action/time and no failed-request history. |
| AC-001–005 queue/backend proposal scope | Existing Task 1 tests remain included in the full suite. |

## Browser verification
- Edge with real MSW fixtures: PM sees pending read-only detail and all three decisions. Single Approve transitions to Published without bulk confirmation; completed actions disappear. Reject requires Reason, then transitions to Rejected. Request Changes submitted with Tab/Enter transitions to Changes requested and renders the saved comment.
- Imported CSV Published detail displays MSG18 without decision buttons. Ticket Resolver requesting another author's unpublished ticket sees MSG04 with no Solution/comment or queue navigation; their own unpublished ticket remains readable without decisions. Existing Employee route guard remains unchanged.
- Desktop at 1366 × 768 and narrow at 760 × 768 inspected visually. Narrow document scroll width equals client width (745 px); content wraps with no horizontal overflow. Temporary viewport override reset. No captured application console errors.
- Screenshots: `artifacts/assets/ticket-review/kan-24-detail-pending.png`, `kan-24-detail-approved.png`, `kan-24-detail-changes.png`, `kan-24-detail-narrow.png`. The changes screenshot uses the original browser size; later desktop and narrow checks use the stated dimensions.
- Browser control lost its debugger connection once during a viewport/action batch. A fresh tab in the same selected browser recovered; subsequent native accessibility actions verified all stated outcomes. This is an automation limitation, not an application failure.

## Notes
- Local commands use the available Node v22.16.0. The repository targets Node 24 (`.nvmrc`); CI is the separate check on that runtime and is being watched by the user.
- Final run has 13 tests over five seconds (full app/AntD page interactions under two-worker execution); no timeout was increased. Existing jsdom pseudo-element getComputedStyle warnings remain diagnostic noise, with zero test failures.
- Review: no critical/informational findings from the independent reviewer; root inspected source/tests and ran project checks independently.
- Workspace audit retains incoming main's two platform-spec failures (invalid spec and changed approval) plus 26 existing warnings about branch naming, older reviews and unrelated artifacts. They are outside ticket-review and its approval remains valid.
- Graphify refreshed after the queue-test correction to 6,969 nodes / 14,800 edges; four SQL files omitted due to unavailable tree_sitter_sql, unrelated to this frontend slice.
- Backend integration, durable audit/history, real Jira/email delivery and Evidence binary downloads remain unverified/out of scope. No new bug has been demonstrated in this feature.

## PR #102 FE Lead follow-up — 2026-11-01

- Review: https://github.com/T-Solve-team/T-Solve/pull/102#pullrequestreview-5727559560. User approved the exact three-file quick change at 10:23:01Z. No acceptance criterion, timeout, lint rule or CI configuration changed.
- BUG-089 failing-first run `pr102-detail-red.json`: 2 passed / 1 failed / 19 intentionally filtered; the old detached Approve element never becomes disabled because refetch removes its toolbar. Corrected `canDecide` (render permission/status) versus `canSubmit` (freshness/interaction); final focused run `pr102-detail-green.json`: 22/22 passed.
- Added AC-006.3 unmanaged PM-author case through the real mock and AC-006.1/006.5 empty-Evidence MSG04 case. Removing the manager check makes the new permission test fail; restored exact file bytes/SHA-256 and the same test passes (`pr102-permission-mutant.json`, `pr102-permission-restored.json`). Existing pending-approval test guards the intentionally retained stable Approve accessible name. Conditional popup isOpen simplified.
- Users default-worker rechecks: **154/154**, zero failed/skipped under Node22.16.0 and Node24.22.1; an additional Node24 run after a clean local dependency installation also passes **154/154** (`pr102-users-node24-local-deps.json`). Users/FormModal/setup have no diff against main81d64dd. Original accessible-name fix is already merged in #100; no new users fix is claimed. Bug rows now record the external remaining-failure report, successful rechecks, lack of supplied reproduction details, and pending tester confirmation; they are not Closed.
- Environment correction: a temporary dependency junction to the original checkout produced275 false boundary lint errors. Replaced it with `npm ci --offline` (424 packages, unchanged lockfile). Stopped the preliminary full-suite attempt before completion, so it is not evidence of a pass or application failure. Fresh local-dependency format/lint/typecheck pass (zero errors, one existing warning); build passes and API regeneration leaves no generated diff.
- Final full Node24.22.1 suite on local dependencies: **1,139/1,139 passed, zero failed/skipped, 148/148 reported suites, exit0**, using `--no-cache --maxWorkers=2 --reporter=json --outputFile=node_modules/.cache/pr102-full-final-node24.json`. Reported wall duration is1,766 seconds; one tool wait itself showed an extended wall-time gap, so no performance conclusion is drawn. All users and detail cases pass. No further test rerun is required without a source change.
- Independent read-only reviewer: no critical/informational findings after exact diff/spec/Graphify review. Browser still disabled in Work settings; no new browser/visual check is claimed. Graphify refreshed after source changes:6,920 nodes /14,746 edges; four SQL files remain omitted due to the known missing parser.

## Trace output

```text
  ✓ AC-001.1  frontend/src/features/review/pages/ReviewQueuePage.test.tsx, frontend/src/features/review/mocks/handlers.test.ts
  ✓ AC-001.2  frontend/src/features/review/pages/ReviewQueuePage.test.tsx
  ✓ AC-001.3  frontend/src/features/review/pages/ReviewQueuePage.test.tsx, frontend/src/features/review/pages/ReviewQueueRecovery.test.tsx
  ✓ AC-001.4  frontend/src/features/review/pages/ReviewQueuePage.test.tsx
  ✓ AC-001.5  frontend/src/features/review/mocks/handlers.test.ts
  ✓ AC-001.6  frontend/src/features/review/pages/ReviewQueueRecovery.test.tsx
  ✓ AC-002.1  frontend/src/features/review/pages/ReviewQueuePage.test.tsx, frontend/src/features/review/mocks/handlers.test.ts
  ✓ AC-002.2  frontend/src/features/review/pages/ReviewQueuePage.test.tsx
  ✓ AC-002.3  frontend/src/features/review/pages/ReviewQueuePage.test.tsx, frontend/src/features/review/mocks/handlers.test.ts, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-002.5  frontend/src/features/review/pages/ReviewQueuePage.test.tsx
  ✓ AC-003.1  frontend/src/features/review/pages/ReviewQueuePage.test.tsx, frontend/src/features/review/components/DecisionForms.test.tsx, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-003.2  frontend/src/features/review/pages/ReviewQueuePage.test.tsx, frontend/src/features/review/mocks/handlers.test.ts, frontend/src/features/review/components/DecisionForms.test.tsx, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-003.3  frontend/src/features/review/pages/ReviewQueuePage.test.tsx, frontend/src/features/review/mocks/handlers.test.ts, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-003.4  frontend/src/features/review/mocks/handlers.test.ts
  ✓ AC-004.1  frontend/src/features/review/pages/ReviewQueuePage.test.tsx, frontend/src/features/review/components/DecisionForms.test.tsx, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-004.2  frontend/src/features/review/mocks/handlers.test.ts, frontend/src/features/review/components/DecisionForms.test.tsx, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-004.3  frontend/src/features/review/pages/ReviewQueuePage.test.tsx, frontend/src/features/review/mocks/handlers.test.ts, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-004.4  frontend/src/features/review/mocks/handlers.test.ts, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-005.1  frontend/src/features/review/pages/ReviewQueueRecovery.test.tsx, frontend/src/features/review/mocks/handlers.test.ts
  ✓ AC-005.2  frontend/src/features/review/pages/ReviewQueueRecovery.test.tsx, frontend/src/features/review/mocks/handlers.test.ts
  ✓ AC-005.3  frontend/src/features/review/mocks/handlers.test.ts
  ✓ AC-005.4  frontend/src/features/review/pages/ReviewQueueRecovery.test.tsx, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-005.5  frontend/src/features/review/pages/ReviewQueueRecovery.test.tsx, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-005.6  frontend/src/features/review/pages/ReviewQueueRecovery.test.tsx, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-005.7  frontend/src/features/review/mocks/handlers.test.ts, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-006.1  frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-006.2  frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-006.3  frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-006.4  frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-006.5  frontend/src/features/review/components/ReviewTicketDetail.test.tsx
  ✓ AC-006.6  frontend/src/features/review/mocks/handlers.test.ts, frontend/src/features/review/components/ReviewTicketDetail.test.tsx
trace ticket-review: 31/31 acceptance criteria have a test
```
