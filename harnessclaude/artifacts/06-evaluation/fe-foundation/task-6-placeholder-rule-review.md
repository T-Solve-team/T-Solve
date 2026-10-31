---
module: fe-foundation
feature: placeholder-rule
task: 6
verdict: pass
attempt: 1
date: 2026-10-23
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: fe-foundation — Task 6: Placeholders only while a screen is not built

## Summary
Change request on a completed module: AC-005.3 now holds only WHILE a placeholder screen is not built, and the 12.1 row
leaves the placeholder test because `chatbot` builds 12.1 in `a5c323a` on the same branch. The reworded criterion is
EARS (state-driven `WHILE … THE web app SHALL …`) and testable. The test still proves AC-005.3 for each of the six
screens that are still placeholders. 12.1 is a real page now. No other fe-foundation criterion broke. The router and
mocks tests pass (76/76) and trace is 48/48. Three MINOR findings, none blocking.

Reviewed: `git show 507955c` (the full diff: one test line removed, one comment added, the spec, tasks and iteration
artifacts), the page file of every remaining placeholder screen, `KnowledgeChatbotPage.tsx`, the `guards.test.tsx`
landing test and `routes.test.tsx`.

### Stage 1 — Spec compliance (STORY-005)

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-005.1 screen table with paths and titles | PASS | `route-table.test.tsx:67-72`; 12.1 stays in `SCREENS` (`:40`), still routed at `/chatbot` (`features/chatbot/routes.ts:6-9`) |
| 2 | AC-005.2 popups have no path | PASS | `route-table.test.tsx:83-90`, unchanged |
| 3 | AC-005.3 (reworded) placeholder WHILE not built | PASS | `route-table.test.tsx:93-110` renders each of 2.1, 10.1, 11.1, 11.2, 11.4, 11.7 for an allowed role and checks the h1 title and the `"<n> <title> — not built yet"` text. Each page still renders `ScreenPlaceholder`: `workspace/pages/WorkspaceSetupPage.tsx:5`, `triage/pages/TriageHistoryPage.tsx:5`, `wiki/pages/WikiHomePage.tsx:5`, `wiki/pages/WikiDocumentPage.tsx:5`, `wiki/pages/UploadDocumentsPage.tsx:5`, `wiki/pages/SpaceSettingsPage.tsx:5` |
| 4 | AC-005.4 triage/wiki/chatbot registered | PASS | `route-table.test.tsx:113-117`, `src/mocks/handlers.test.ts` |
| 5 | AC-005.5 404 | PASS | `routes.test.tsx`, unchanged |

12.1 is not a placeholder any more: `features/chatbot/pages/KnowledgeChatbotPage.tsx:13-51` renders
`ConversationThread` and `QuestionBox` and does not import `ScreenPlaceholder`. A grep for `ScreenPlaceholder` finds no
use under `features/chatbot`. So the removed row matches the WHILE condition.

Other criteria that touch 12.1 still hold. `guards.test.tsx:103-117` (AC-003.1) still finds the h1 "Knowledge Chatbot"
for `employee` at `/chatbot`. That h1 comes from the shell (`app/layout/AppShell.tsx:29`), not from the page, so it does
not depend on the placeholder. The menu tests (`menu-config.test.ts`, `routes.test.tsx:55`) only use the label.

EARS check of the new text (`spec.md:141-143`): the trigger "WHEN … opens it" became the state "WHILE a placeholder
screen is not built by its module", and the response is unchanged. The criterion is testable: whether a screen is built
shows in its page file (it renders `ScreenPlaceholder` or not), and the test checks the rendered placeholder text. The
Changelog row (`spec.md:14`) names the story, the change, the reason and the approver.

### Stage 2 — Quality
No CRITICAL findings. The code change is one deleted test row and one comment. It reaches no other caller, and the
`SCREENS` table and AC-002.5 rows for 12.1 are kept on purpose.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Each of the 6 remaining placeholder screens is proven to show its number and title. 12.1 shows the built chatbot, which the reworded AC-005.3 allows. |
| Completeness | pass | AC-005.1–AC-005.5 each have a test that checks them. Nothing EXTRA. |
| Correctness | pass | `npx vitest run src/app/router src/mocks`: 5 files, 76/76 passed. No CRITICAL findings. |
| Integration | pass | The chatbot route, menu and AC-003.1 landing are unaffected (guards test passes). AC-005.4 registration still passes. |

## Traceability
```
  ✓ AC-005.1  frontend/src/app/router/route-table.test.tsx
  ✓ AC-005.2  frontend/src/app/router/route-table.test.tsx
  ✓ AC-005.3  frontend/src/app/router/route-table.test.tsx
  ✓ AC-005.4  frontend/src/mocks/handlers.test.ts, frontend/src/app/router/route-table.test.tsx
  ✓ AC-005.5  frontend/src/app/router/routes.test.tsx
trace fe-foundation (STORY-005): 5/5 acceptance criteria have a test
```
Whole module: `trace fe-foundation: 48/48 acceptance criteria have a test`.

## Issues
Verdict pass. These findings are informational:

1. MINOR — `frontend/src/app/router/route-table.test.tsx:92-100`: the list of screens still under AC-005.3 is kept by
   hand, apart from the spec list (`spec.md:142-143`, which still names 12.1). The test is self-correcting in one
   direction only. A module that builds its screen but forgets to remove its row gets a failing test. But nothing checks
   that the test rows match the spec's placeholder set minus the built screens, so a row deleted too early would pass
   silently. Suggested fix (optional): keep it as it is. Or, when a later module removes a row, also assert that the
   page file of that screen no longer renders `ScreenPlaceholder`.
2. MINOR — `harnessclaude/artifacts/03-modules/fe-foundation/spec.md:141-143`: "not built by its module" is defined
   only by the code. A reader of the spec cannot tell which of the seven screens are still placeholders without reading
   the test. Suggested fix (optional, spec change): none needed now. If the list grows confusing, note in the Changelog
   which screen each later module took over, as the 2026-10-23 row does for 12.1.
3. MINOR — commit `507955c` holds the spec, `tasks.md` and `iteration-1.md` edits together with the code (test) change.
   The project rule is "one feature per commit (plus its harness commit)", and the harness session log is a separate
   commit (`23a71bf`). Keeping the approved spec change with the test that implements it is defensible. Flagged only so
   the user can confirm this split is what they want. No fix needed.

## Assumptions made
- Removing the 12.1 row from the AC-005.3 test without adding a negative test ("12.1 shows no placeholder") is enough.
  The reworded criterion only says what happens WHILE a screen is not built. It is silent about built screens, and
  12.1's real behaviour is covered by the chatbot module's own tests (`KnowledgeChatbotPage.test.tsx`, not run here).
  Open for the user: should fe-foundation also assert that built screens no longer show the placeholder?
- The run was limited, as dispatched, to `npx vitest run src/app/router src/mocks` (no full suite, format, lint,
  typecheck, build or `generate:api`). The diff changes one test line and one comment in a `.test.tsx` file, so a lint
  or format break is the only plausible miss. The session log reports those checks clean, but this review did not verify
  that.
- The feature status was not changed (`sdd.ts feature fe-foundation placeholder-rule passing`). The dispatcher allowed
  only the review files to be written, so the main session does this.

## Test Report
See `task-6-placeholder-rule-test-report.md`. `npx vitest run src/app/router src/mocks` in `frontend/` gave 5 test files
and 76 tests passed, 0 failed, in 9.37s. Trace: STORY-005 5/5, module 48/48. Coverage is not measured (no script).
