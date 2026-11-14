---
module: jira-app
feature: solution-forge-client
task: 6
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 6: Forge client, manifest and background script (solution-forge-client, KAN-126)

Scope: commits `215d7051`, `ccfee5d6` (the BUG-215 client part) and `a5a6d4ca` (BUG-212), repo `default`, judged on
HEAD `622c71e6` of `feat/KAN-126-jira-app-solution-form-v2`. Files: `jira-app/TSolve_JiraApp/src/frontend/api/tsolveClient.js`,
`src/frontend/background/watchDone.js`, `BackgroundApp.jsx`, `index.jsx`, `src/frontend/messages.js`, `manifest.yml`
and their tests.
Contract: `harnessclaude/artifacts/03-modules/jira-app/spec.md` (STORY-001, STORY-004; NF 1 to NF 4).
Edge cases: `harnessclaude/artifacts/04-plans/jira-app/iteration-2/design.md`, including the two "Change — 2026-11-14"
sections.
Passes run: run-tests (with trace), code-review (spec compliance, then quality). No security-review pass: the Forge
client holds no secret; it sends issue text only to the T-Solve remote through `invokeRemote`, and the backend
enforces authorization (Task 5).

## Summary

The Forge side of STORY-001 and STORY-004 does what the spec asks:

- **AC-001.1.** The background script asks `POST …/solution-form` and opens the form Modal only when the viewer's
  own change moves the issue into the Done category. Because of BUG-212 it catches the move three ways: the
  `JIRA_ISSUE_CHANGED` event, a load-time check (the viewer's status change in the last 60 s), and a 4 s poll while the
  view is visible. All three are tested.
- **AC-001.3 and AC-001.5.** The client opens only when the server says `mayOpen`. An issue that was already Done when
  the page loaded does not open the form, and neither does an old move or someone else's move.
- **AC-004.4.** Submit retries network errors, timeouts and 5xx three times (1 s, 2 s, 4 s), then gives MSG69. It
  never retries a 4xx. After a retry only a 409 **MSG31** counts as submitted, and a 409 MSG40 is given back (BUG-215).
- **NF 3.** All server calls go through the one client module, and nothing logs issue or Solution text.
- **Messages.** `messages.js` matches the SRS catalog word for word for all 13 codes.
- **Manifest.** It declares `jira:issueViewBackgroundScript`, the issue panel and the UI endpoint, with only the
  `read:jira-work` scope.

Jest passed 141/141 and lint was clean. The trace shows 9/9 criteria with a test.

I found no blocking issue. There are three Medium findings. One of them (Issue 1) is a narrow AC-001.1 gap that I
record as a judgment call instead of failing on it.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | **AC-001.1:** event path `BackgroundApp.jsx:68-82`, load path `:40-47`, poll path `:49-66`. All three end in `offerForm` (`:30-39`), which opens only when `form.mayOpen` is true. **AC-001.3:** a server `mayOpen:false` gives no dialog and no flag (`BackgroundApp.test.jsx:96-105`). **AC-001.5:** `movedIntoDone` needs a known previous category that is not Done (`watchDone.js:12-14`), and `recentlyMovedIntoDone` needs a recent move by the viewer (`:61-70`). The server's "ticket exists → no form" rule is in Task 5. **AC-004.3 and AC-004.4:** `tsolveClient.js:88-111`. **AC-004.2 and AC-005.1:** the flag after the Modal closes (`BackgroundApp.jsx:101-105`). See Issue 1 for a second move into Done in the same page load. |
| Completeness | pass | Every call the task lists is in `tsolveClient.js`: solution-form, solution, solution-draft, solution-rewrites, panel and review-feedback (`:73-147`). The Task 9 change (14/11) removed search and preview, so the "seven calls" are now five plus review-feedback. The manifest has the background script (`manifest.yml:9-14`), the issue panel and `tsolve-ui-endpoint` (`:35-36`). The design's `background/index.jsx` exists (it renders `BackgroundApp`), and the hello-world panel is gone. `messages.js` has the codes the current spec still shows. MSG41, MSG70 and MSG76 were dropped with the 14/11 changes, and MSG04 belonged to the removed search. |
| Correctness | pass | `npx jest`: 12 suites, 141/141. `npm run lint`: exit 0. The BUG-215 test passes (`solutionClient.test.js:75-82`), and so do the BUG-212 tests (`watchDone.test.js:193-254`, `BackgroundApp.test.jsx:106-169`). I did not run `mvn verify` (dispatcher rule), and this task changes no backend code. |
| Integration | pass | The paths, methods and bodies match the contract table in spec.md and the Task 5 controllers: `solution-form` POST {summary, description}, `solution` POST {summary, sections}, `solution-draft` PUT sections, `solution-rewrites` POST sections, `panel` GET and `review-feedback` GET → `feedback`. Error codes reach the UI from the backend's `ProblemDetail` `code` and `fieldErrors[0].field` (`tsolveClient.js:174-183`). A 5xx keeps only MSG68 (`:177`), which AC-007.4 needs. |

### Stage 1: Spec compliance

| AC | Status | Evidence |
|----|--------|----------|
| AC-001.1 | PASS (see Issue 1) | `BackgroundApp.jsx:30-82`; tests `BackgroundApp.test.jsx:67-75,130-135,106-114`, `watchDone.test.js:149-166` |
| AC-001.3 | PASS (client side) | `BackgroundApp.jsx:36`; test `BackgroundApp.test.jsx:96-105`. When the issue or T-Solve cannot be read, nothing opens (`:45-47,63-65,79-81`) |
| AC-001.5 | PASS | `watchDone.js:12-14,61-70`; tests `BackgroundApp.test.jsx:87-94,154-160`, `watchDone.test.js:235-238`. The "ticket exists" decision is the server's `mayOpen` (Task 5) |
| AC-004.3 | PASS | A first 409 is given back (`tsolveClient.js:99,102-103`; test `solutionClient.test.js:84-92`). After a retry only MSG31 counts as submitted (`:99-101`; tests `:67-82`) |
| AC-004.4 | PASS (see Issue 2) | `tsolveClient.js:15,35-38,95-110`; tests `solutionClient.test.js:50-65` (4 calls, delays 1/2/4 s, MSG69) and `:94-105` (no retry on 400) |
| NF 2 | PASS | Every call stops after 20 s with MSG06 / status 0 (`tsolveClient.js:153-166`), so a timeout is retried for submit |
| NF 3 | PASS | `invokeRemote` is used only in `tsolveClient.js`, and the client and the background script do not log |

EXTRA: the 4 s status poll while the issue view is visible (`BackgroundApp.jsx:14,49-67`). It is not in the task text
but is in BUG-212 and the commit, and it serves AC-001.1. Accepted.

## Traceability

```
  ✓ AC-001.1  jira-app/TSolve_JiraApp/src/frontend/background/BackgroundApp.test.jsx, jira-app/TSolve_JiraApp/src/frontend/background/watchDone.test.js
  ✓ AC-001.2  backend/src/test/java/vn/tsolve/jiraapp/service/impl/ConfigJiraWorkspaceLookupTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-001.3  jira-app/TSolve_JiraApp/src/frontend/background/BackgroundApp.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/ConfigJiraWorkspaceLookupTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImplTest.java
  ✓ AC-001.4  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-001.5  jira-app/TSolve_JiraApp/src/frontend/background/BackgroundApp.test.jsx, jira-app/TSolve_JiraApp/src/frontend/background/watchDone.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-004.1  jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-004.2  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-004.3  jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-004.4  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js
trace jira-app (STORY-001, STORY-004): 9/9 acceptance criteria have a test
```

## Issues

### Blocking

None.

### Non-blocking

1. **[Functionality, Medium. Judgment call] A second move into Done in the same page load never asks the server.**
   - `asked` is set once per effect run (`BackgroundApp.jsx:28,31-34`) and is never reset. After the first ask, the
     event handler and the poll both return early (`:50`, `:31`).
   - Scenario: the viewer moves the issue into Done, and the form opens. The viewer closes it (the Draft is kept,
     AC-005.1), reopens the issue and moves it into Done again without reloading.
   - AC-001.1 ("WHEN the issue … changes to a status of the Done status category, THE Jira App SHALL ask the server")
     is not met for that second move. The server would answer `mayOpen: true`, because a Draft exists and there is no
     ticket.
   - The same happens when the first ask fails (`openSolutionForm` throws): `asked` stays true, so no later move asks again.
   - The panel button (AC-001.4, MSG77) is still the way in, so no data is lost.
   - Fix: set `asked = false` when the category is seen leaving Done (in `check` and `onChange`, when
     `issue.category !== DONE`), and in a `finally` of a failed ask. Then add a test that moves Done → In Progress →
     Done in one render.
2. **[Correctness, Medium. Not verified on Forge] A response without a numeric `status` is not retried.**
   - `invokeRemote` resolves with the bridge's `error` object for a failed call
     (`node_modules/@forge/bridge/out/invoke-endpoint/invoke-endpoint.js`: `const response = { ...(success ? payload : error) }`).
     It does not reject.
   - If that object has no numeric `status` (for example on a network failure), `call` throws
     `TsolveError('MSG06', { status: undefined })` (`tsolveClient.js:167-170,178-182`).
   - `retryable` then returns false (`:36-38`), because `undefined === 0` and `undefined >= 500` are both false. Submit
     would give MSG06 at once instead of retrying and then giving MSG69 (AC-004.4).
   - The tests cover only a rejected promise and a numeric 5xx (`solutionClient.test.js:50-65`).
   - Fix: in `errorOf`, treat a non-numeric `response.status` as 0. Add a test where `invokeRemote` resolves with `{}`.
3. **[Robustness, Medium] The load check relies on the changelog being newest first.**
   - `readIssue` takes the first history that has a `status` item as "the newest status change"
     (`watchDone.js:44-46`). The comment at `:25` assumes Jira lists `expand=changelog` newest first.
   - If the order is ascending, `lastStatusChange` is the oldest status change. The load check then misfires: it opens
     nothing for the viewer's real move, or it wrongly trusts an old change that happens to be recent.
   - The live check of SAM1-8 and SAM1-9 suggests the order holds today. Still, the code does not depend on it for any
     good reason.
   - Fix: pick the status history with the greatest `created` value instead of the first one.
4. **[Docs, Low] Two doc comments describe the old flow.**
   - `submitSolution` is "Finish or Close" (`tsolveClient.js:84`), but since the 14/11 change Close no longer submits
     (AC-005.5).
   - `saveSolutionDraft` is "Save as draft" (`:113`), but that button is gone; Close or the dialog's close control
     calls it (AC-005.1).
   - Fix: update both comments.
5. **[UX, Low] The form can open again after Close if Jira reloads the script within 60 s.**
   - The load path offers the form whenever the viewer moved the issue into Done in the last 60 s
     (`BackgroundApp.jsx:40-43`, `watchDone.js:20,61-70`).
   - After Close, the issue has a Draft and no ticket, so `mayOpen` is still true. A reload of the issue view within the
     window opens the form a second time.
   - Two issue views open at the same moment (two tabs) can also both offer the form.
   - Acceptable, but worth knowing.
6. **[Test realism, Low] The test named AC-001.5 does not exercise the "reopened issue whose ticket exists" case.**
   - `BackgroundApp.test.jsx:87-94` checks an issue that was already Done when the page loaded.
   - The actual AC-001.5 case (the ticket exists, the issue is moved into Done again) runs through the server's
     `mayOpen: false`. The `AC-001.3` client test and the backend `JiraAppSolutionServiceImplTest` cover that.
   - Together they cover the criterion. A client test named for the reopen case would make that clearer.
7. **[Performance, Info] Submit can take up to about 87 s in the worst case.**
   - The worst case is 4 calls of 20 s each plus 7 s of waits (`tsolveClient.js:9,15`).
   - AC-004.4 allows it, and the form stays open with its content. Consider showing that a retry is in progress.
8. **[Config, Info. Not this task] The remote `baseUrl` is a personal ngrok URL.**
   - `manifest.yml:39` is `https://resigned-ladle-saggy.ngrok-free.dev`, from `b9e996f6` (01/11), and it is also on `main`.
   - It needs a per-environment value before any shared deployment.

## Assumptions made

- **Issue 1 is not blocking.** I read AC-001.1 as asking for every move into Done. The code asks only once per page
  load, and the panel button covers the gap. **Judgment call for the user:** accept "one automatic ask per load", or
  require the reset fix before Task 6 passes?
- **Issue 2 is not blocking.** I could not see the Forge bridge's `error` payload shape at run time; the dev-site check
  did not exercise the retry path. **Judgment call:** verify on the dev site (stop the backend, click Finish, expect
  MSG69 after about 7 s plus the attempts), or add the defensive fix now?
- **"Seven calls" in the task text.** I read it against the design's 14/11 Change sections. Search and preview were
  removed (Task 9), and the separate Save as draft button went with AC-005.5 (Task 10). So I treat five calls plus
  review-feedback as complete.
- **The BUG-212 poll and changelog read are in scope** as part of AC-001.1, even though they came after the task text.
- **The backend is not re-checked.** This task changes no backend code. I did not run `mvn verify` (dispatcher rule),
  so the backend tests listed in the trace are taken as green from Task 5's review and CI.

## Test Report

See `task-6-solution-forge-client-test-report.md`. Jest passed 12 suites and 141/141 tests, `npm run lint` was clean,
and the trace shows 9/9 criteria with a test.
