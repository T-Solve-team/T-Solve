---
module: jira-app
feature: solution-form-ui
task: 7
attempt: 1
iteration: 2
date: 2026-11-14
reviewer: harnessclaude-reviewer
---

# Test Report: jira-app — Task 7: Solution Form and AI Rewrite Preview (solution-form-ui, KAN-126)

Branch `feat/KAN-126-jira-app-solution-form-v2`, HEAD `622c71e6` (repo `default`, monorepo). Commits in scope: `4b34ca3c`
(form, rewrite preview, Done watcher) and the form changes in `ffebe905` (Close keeps a Draft). Run by the reviewer on
2026-11-14. The working tree had one unrelated change (`jira-app/TSolve_JiraApp/package-lock.json`), left untouched.

## Results

| Check | Command (directory) | Result |
|-------|---------------------|--------|
| Forge app tests | `npx jest --verbose` (`jira-app/TSolve_JiraApp`) | PASS. 12 suites, 141 tests, 0 failures |
| — SolutionFormModal.test.jsx | form, `doneFlag`, `validateSections` | 18/18 |
| — BackgroundApp.test.jsx | Done watcher (event, load check, poll check) | 10/10 |
| — solutionClient.test.js | client calls used by the form (Task 6 file) | 12/12 |
| Forge app ESLint | `npm run lint` (`jira-app/TSolve_JiraApp`) | PASS, exit 0, no findings |
| Traceability | `bun harnessclaude/hooks/sdd.ts trace jira-app STORY-002 STORY-004 STORY-005 STORY-007` | 17/18. AC-005.3 shows ✗ (tests outside the module's trace paths, see below) |

## Not run

- **`mvn verify`**: not run, at the dispatcher's instruction (CI runs it; other reviewers run in parallel). The task
  changes no backend code in `4b34ca3c`; the backend part of `ffebe905` belongs to Task 10 (`close-keeps-draft`).
- **`forge lint`** and the dev-site check: not run by the reviewer. The `ffebe905` commit message reports a dev-site
  check of Close, Escape, Continue draft and Finish (5.8.0, 5.9.0); this cannot be verified from the code.
- **Coverage**: no coverage tool is set up for the Forge app (Jest runs without `--coverage`). Traceability is used
  instead.

## Traceability

```
  ✓ AC-002.1  SolutionFormModal.test.jsx, solutionClient.test.js, JiraAppSolutionServiceImplTest.java, SolutionFormAiImplTest.java
  ✓ AC-002.2  SolutionFormModal.test.jsx, watchDone.test.js, JiraAppSolutionServiceImplTest.java, SolutionFormAiImplTest.java
  ✓ AC-002.3  SolutionFormModal.test.jsx
  ✓ AC-002.4  SolutionFormModal.test.jsx
  ✓ AC-002.5  SolutionFormModal.test.jsx, solutionClient.test.js, JiraAppSolutionControllerTest.java
  ✓ AC-004.1  solutionClient.test.js, JiraAppSolutionServiceImplTest.java, JiraAppSolutionControllerTest.java
  ✓ AC-004.2  SolutionFormModal.test.jsx
  ✓ AC-004.3  solutionClient.test.js, JiraAppSolutionControllerTest.java
  ✓ AC-004.4  SolutionFormModal.test.jsx, solutionClient.test.js
  ✓ AC-005.1  SolutionFormModal.test.jsx, solutionClient.test.js, JiraAppSolutionServiceImplTest.java, JiraAppSolutionControllerTest.java
  ✓ AC-005.2  JiraAppSolutionServiceImplTest.java
  ✗ AC-005.3  no test under this module's paths
  ✓ AC-005.4  SolutionFormModal.test.jsx
  ✓ AC-005.5  SolutionFormModal.test.jsx
  ✓ AC-007.1  SolutionFormModal.test.jsx, JiraAppSolutionServiceImplTest.java, SolutionFormAiImplTest.java
  ✓ AC-007.2  SolutionFormModal.test.jsx
  ✓ AC-007.3  SolutionFormModal.test.jsx
  ✓ AC-007.4  SolutionFormModal.test.jsx, solutionClient.test.js, SolutionFormAiImplTest.java, JiraAppSolutionControllerTest.java
trace jira-app (STORY-002, STORY-004, STORY-005, STORY-007): 17/18 acceptance criteria have a test
```

AC-005.3 (server: a Draft is shown only to its creator) is a Task 2 criterion. Its tests are
`backend/src/test/java/vn/tsolve/ticket/service/impl/SolutionDraftServiceImplTest.java` (`returnsOnlyTheCreatorsOwnDraft_AC_005_3`)
and `backend/src/test/java/vn/tsolve/ticket/SolutionFormTicketIT.java:165` (`keepsOneDraftPerCreatorAndIssue_AC_005_1_AC_005_3`),
under `ticket/**`, which is not in jira-app's trace paths. The Task 2 and Task 5 reviews accepted it on the same basis.

## Forge test names for this task (Jest, verbose)

Every test passed.

SolutionFormModal:
- AC-002.1 shows the five sections filled with the AI draft
- AC-002.2 shows MSG67 when the form opens with the issue text
- AC-002.3 shows MSG01 under the first empty required section and sends nothing
- AC-002.4 shows MSG02 over 10,000 characters and sends nothing
- AC-004.2 submits the Solution and closes the form
- AC-004.4 shows MSG69 and keeps the content when T-Solve stays unreachable
- AC-002.5 shows a server MSG01 under its section
- AC-005.1 saves the Draft when Close is clicked and closes the form
- AC-005.1 saves the Draft when the dialog itself is closed
- AC-005.1 keeps what was typed, not what the form opened with
- AC-005.4 shows MSG06 and keeps the form open with its content when saving fails
- AC-005.5 offers no Save as draft button, asks nothing on Close and never submits on Close
- AC-007.1 AC-007.2 shows the rewrite next to the Solution and Accept replaces it
- AC-007.3 keeps the Solution on Discard
- AC-007.4 shows MSG68 and leaves the Solution unchanged when the rewrite fails
- doneFlag: AC-004.2 AC-005.1 shows MSG66 after Finish and "Saved as draft" after Close
- validateSections: AC-002.3 accepts an empty Workaround; AC-002.4 counts an emoji once

BackgroundApp:
- AC-001.1 opens the form when the viewer moves the issue into Done; AC-001.1 ignores a change made by someone else
- AC-001.5 does not open for an issue that was already Done; AC-001.3 does not open when the server says the form may not open
- BUG-212: opens on reload right after the viewer's move; not on load for an old move; not on load for someone else's move;
  notices the move on its own check; opens nothing for someone else's move; asks only once when event and check both see it
