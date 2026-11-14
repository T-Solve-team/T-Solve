---
module: jira-app
feature: close-keeps-draft
task: 10
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 10: Close keeps a Draft; no empty ticket (close-keeps-draft, KAN-126)

Scope: commit `ffebe905` (repo `default`), reviewed on HEAD `622c71e6` of `feat/KAN-126-jira-app-solution-form-v2`.
None of the reviewed files changed between `ffebe905` and `622c71e6` (`git diff --stat` is empty for `jira-app/`,
`backend/.../jiraapp`, `SolutionFormTicketServiceImpl.java` and `SolutionSections.java`). The merge from `main`
(`3cbab3db`) brought in other modules' code only. The working tree has one unrelated change
(`jira-app/TSolve_JiraApp/package-lock.json`).
Contract: `harnessclaude/artifacts/03-modules/jira-app/spec.md` STORY-005 (AC-005.1 to AC-005.5) and the two changelog
rows of 2026-11-14. Edge cases: `harnessclaude/artifacts/04-plans/jira-app/iteration-2/design.md`, "Change — 2026-11-14:
Close keeps a Draft".
Passes run: code-review (spec compliance, then quality). There was no separate security-review: the change removes a
code path (all-empty submission) and adds no new endpoint, input or permission.

## Summary

The change does what the reworded STORY-005 asks.
- **Jira App:** the form has Rewrite with AI, Close and Finish only. Close and the Modal's own `onClose` (X, Escape)
  both call `close()`, which `PUT`s the current sections as the Draft. On success the form closes and the caller shows
  the flag "Saved as draft". On failure the form shows MSG06 and stays open with its content.
- **Server:** `submit` now always runs `requireSections`. An all-empty Solution gets 400 MSG01, and no ticket is
  inserted and no Draft is deleted. No client can put an empty ticket in the Review Queue any more.
- **Clean-up:** MSG41 and MSG70 are gone from `messages.js`. `SolutionSections.isEmpty` and the AC-006.2 tests are
  removed. No reference to AC-006 / MSG70 is left in the Jira App or the jiraapp backend.
- **Tests:** jest 141/141 and lint are clean. The targeted backend tests passed: 31 tests in 4 classes.

No blocking issue. Three low-severity documentation drifts and one UX judgment call are listed below.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-005.1: `SolutionFormModal.jsx:103-116` (`close`), wired to the Close button (`:180`) and to `Modal onClose` (`:138`). It sends the live `sections` state, so edits made after opening are kept. The flag comes from `doneFlag` (`:46-54`, title `LABELS.savedAsDraft`), and both callers use it (`BackgroundApp.jsx:104`, `IssuePanel.jsx:126`). The panel reloads after close (`IssuePanel.jsx:127`), so MSG77 appears. AC-005.4: the `catch` at `:111-115` shows MSG06, returns to the form step and keeps `sections`. AC-005.5: there is no Save as draft button, and close never calls `submit`. The server check is unconditional (`SolutionFormTicketServiceImpl.java:47-48`). |
| Completeness | pass | Every criterion of STORY-005 has a test that names it (Traceability). The trace marks AC-005.3 as missing, but it belongs to Task 2 and is tested outside the module's trace paths: `SolutionDraftServiceImplTest.java:62` and `SolutionFormTicketIT.java:165`. The server half of AC-005.5 is tested in `SolutionFormTicketServiceImplTest.java:107` (also outside the trace paths). |
| Correctness | pass | jest: 12 suites, 141 tests, all passed. `npm run lint` is clean. Backend targeted run: `SolutionFormTicketServiceImplTest` 8/8, `SolutionSectionsTest` 2/2, `JiraAppSolutionServiceImplTest` 12/12, `JiraAppSolutionControllerTest` 9/9. Re-entrancy: `close` returns early while `busy` (`:104-106`). An Escape during a Finish or a rewrite in flight therefore neither saves nor closes, and a double Escape does not send two PUTs. |
| Integration | pass | `tsolve-api.yaml:1041-1044` (submitSolution) no longer describes "all empty = Close" and names 400 MSG01 / AC-005.5. The committed generated client (`frontend/src/shared/api/generated/jira-app/jira-app.ts`) changes only the two summaries, which matches the YAML. No other caller of `SolutionFormTicketService.submit` relied on the empty path. In `SolutionFormTicketIT`, only `drafts.save` uses partial sections (`:105`, `:167-168`), and the one submission (`:200`) is complete. |

### Stage 1 — Spec compliance

| AC | Implementation | Test | Result |
|----|----------------|------|--------|
| AC-005.1 (Close button) | `SolutionFormModal.jsx:103-116`, `:180` | `SolutionFormModal.test.jsx:102` | PASS |
| AC-005.1 (dialog close control) | `Modal onClose={close}` `:138` | `SolutionFormModal.test.jsx:113` (through the test double `test/forge-react.jsx:48-57`); the session log records a live check of Escape on the dev site | PASS |
| AC-005.1 (stores what was typed, unvalidated) | `saveSolutionDraft(issueKey, sections)` `:109`; the server stores without validation (`JiraAppSolutionServiceImpl.java:83-89`) | `SolutionFormModal.test.jsx:123`, `JiraAppSolutionControllerTest.java:142` | PASS |
| AC-005.1 (flag "Saved as draft") | `doneFlag` `:46-54` | `SolutionFormModal.test.jsx:192` | PASS |
| AC-005.4 | `:111-115` | `SolutionFormModal.test.jsx:133` | PASS |
| AC-005.5 (no button, no ticket on close) | footer `:176-186` | `SolutionFormModal.test.jsx:144` | PASS |
| AC-005.5 (server never stores an empty Solution) | `SolutionFormTicketServiceImpl.java:47-48` | `SolutionFormTicketServiceImplTest.java:107` (MSG01, no insert, no Draft delete) | PASS |
| STORY-006 retired | MSG41/MSG70 removed from `messages.js`; `SolutionSections.isEmpty` removed | `SolutionSectionsTest` renamed to AC-002.5 | PASS |

## Traceability

```
  ✓ AC-005.1  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-005.2  backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✗ AC-005.3  no test under this module's paths (jira-app/**, backend/src/main/java/vn/tsolve/jiraapp/**, backend/src/test/java/vn/tsolve/jiraapp/**, frontend/openapi/**, frontend/src/shared/api/generated/**, docs/bug-log.md) — tests elsewhere? add their folder to paths mentions it
  ✓ AC-005.4  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-005.5  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
trace jira-app (STORY-005): 4/5 acceptance criteria have a test
```

AC-005.3 is not changed by this task. It is tested in
`backend/src/test/java/vn/tsolve/ticket/service/impl/SolutionDraftServiceImplTest.java:62` (`returnsOnlyTheCreatorsOwnDraft_AC_005_3`)
and `backend/src/test/java/vn/tsolve/ticket/SolutionFormTicketIT.java:165`, which the module's trace paths do not cover.

## Issues

### Blocking

None.

### Non-blocking

1. **[UX, Medium — judgment call] A persistent Draft-save failure traps the user in the form.**
   - Every way out of the Modal (Close, X, Escape, blanket click) goes through `close()` (`SolutionFormModal.jsx:138`,
     `:180`), and `close()` keeps the form open whenever the PUT fails (`:111-115`).
   - So if the backend is down, or the server answers 403 MSG08 / MSG62 because membership or the link changed while
     the form was open, the user cannot dismiss the form. They can only reload the Jira page, which loses the typed
     text anyway.
   - `saveSolutionDraft` (`api/tsolveClient.js:114-121`) has no retry, unlike `submitSolution`.
   - This follows AC-005.4 to the letter ("keep the form open with its content"), so it does not block.
   - Possible fix (needs a spec decision): after a failed save, let a second close discard and close (for example with
     the MSG06 notice saying so), or retry the PUT like submit does.
2. **[Docs, Low] The saveSolutionDraft contract still names MSG41.**
   - `frontend/openapi/tsolve-api.yaml:1094` says "(MSG41, STORY-005)", and the generated `jira-app.ts` summary repeats it.
   - The later 2026-11-14 changelog row replaces MSG41 with the Jira App label "Saved as draft" (AC-005.1).
   - Fix: drop "MSG41" from the summary and regenerate.
3. **[Docs, Low] A stale comment remains.** `jira-app/TSolve_JiraApp/src/frontend/api/tsolveClient.js:113` still reads
   "Save as draft (AC-005.1)". Fix: "Keeps the Draft when the form is closed (AC-005.1)".
4. **[Docs, Low] The design and the task still say MSG41.**
   - `design.md` "Change — 2026-11-14" and `tasks.md` Task 10 both say the flag is MSG41.
   - The spec (changelog row 2 of 14/11) supersedes that, and the code follows the spec.
   - Fix: align the plan text when the plan is next edited.
5. **[Behaviour, Low — observation] Every dismissal of an auto-opened form stores a Draft.**
   - When the form opens on Done with an AI draft and the user just presses Escape, a Draft holding the AI text is
     stored, and the panel then shows MSG77.
   - The spec asks for exactly this ("store the five sections … without validation"), so it is not an issue. It is
     noted for the separate Draft-reminder issue, whose digest will include such untouched AI Drafts.
6. **[Test realism, Low] The dialog-close test uses the hand-written UI Kit double.**
   - `SolutionFormModal.test.jsx:113` exercises the hand-written double (`test/forge-react.jsx:48-57`), not Forge's
     Modal.
   - The real Escape and blanket paths are covered only by the live check recorded in the session log.

## Assumptions made

- **The trapped-form case (Issue 1) is accepted** because AC-005.4 says to keep the form open on failure.
  **Judgment call for the user:** keep it, or change the spec so that a second close after a failed save discards and
  closes (or the save is retried)?
- **"Saved as draft" instead of MSG41.** The two 2026-11-14 changelog rows disagree. I took the later row and the
  current AC-005.1 text as the contract, not the earlier row or the design and task text that still say MSG41.
- **AC-005.3 counts as tested** by the Task 2 tests outside the module's trace paths. It is not a MISSING finding for
  this task.
- **Backend correctness is judged on the targeted test run only** (dispatcher rule: no `mvn verify`, so no Spotless,
  ITs or JaCoCo). `SolutionFormTicketIT` was read but not run (Testcontainers). By reading, it has no all-empty
  submission that the new check would break.
- **The generated client was not regenerated.** Running `npm run generate:api` rewrites the tracked generated folder,
  and I may not edit code. The committed diff matches the YAML change line for line.

## Test Report

See task-10-close-keeps-draft-test-report.md
