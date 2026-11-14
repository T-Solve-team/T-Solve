---
module: jira-app
feature: issue-panel-ui
task: 8
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 8: Issue Panel (issue-panel-ui, KAN-126)

Scope: the commit `3c4aaa7c` (panel, test, manifest, hello-world removed) and the BUG-215 part of `a5a6d4ca`
(`IssuePanel.jsx`, `IssuePanel.test.jsx`, the BUG-215 row of `docs/bug-log.md`). Repo `default`. I judged the code at
HEAD `622c71e6` of `feat/KAN-126-jira-app-solution-form-v2`, after Task 9 removed the search and the 3.4 preview.
Files: `jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.jsx`, `panel/index.jsx`, `manifest.yml`,
`panel/IssuePanel.test.jsx`. Contract read: `JiraAppPanelController.java`, `JiraAppSolutionServiceImpl.panel`
(`:96-110`), `IssuePanelResponse.java`, `JiraReviewFeedbackController.java`, `tsolve-api.yaml` `IssuePanel` (`:3024-3045`).
Spec: `harnessclaude/artifacts/03-modules/jira-app/spec.md` (changelog of 14/11: search and STORY-009 removed).
Edge cases: `harnessclaude/artifacts/04-plans/jira-app/iteration-2/design.md` (rows 5 and 11, and the 14/11 Change).
Passes run: code-review (spec compliance, then quality). A separate security-review pass was not needed: the panel
adds no endpoint and sends no personal data; it only reads the existing panel and feedback endpoints.

## Summary

The panel meets every criterion that is left for STORY-001 and STORY-008 after the 14/11 change:
- MSG62 alone when the account is not linked (AC-008.1).
- MSG65 while the issue's ticket is Pending review (AC-008.2).
- MSG77 with "Continue draft", which opens the form with the Draft (AC-008.3).
- MSG64 with the manager's name and comment from `ticket-review`'s feedback endpoint (AC-008.7).
- "Write Solution" only while the issue is Done, the user may open the form and no ticket exists (AC-001.4).

The BUG-215 fix is correct. The button re-reads the issue's status before it opens the form, and the panel reloads on
`JIRA_ISSUE_CHANGED`. Both have a test with the bug id, and the bug's row is updated in the same commit.

No search box, results, MSG04, MSG76 or preview code is left in the panel, and a test checks it.

All 141 Forge tests pass, lint is clean, and the trace shows 9/9 criteria with a test. I found no blocking issue. The
findings below are minor robustness points, test gaps and two judgment calls.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Every remaining criterion is met (Stage 1 table). The panel calls the server only through `tsolveClient.js` (NF 3) and logs nothing. |
| Completeness | pass | The task's criteria (AC-001.4, AC-008.1 to AC-008.3, AC-008.7) each have a test that names them. The criteria removed on 14/11 (AC-008.4 to AC-008.6, AC-008.8, AC-009.x) are no longer in the spec and are not implemented. |
| Correctness | pass | `npx jest`: 12 suites, 141/141. `IssuePanel.test.jsx`: 9/9. `npm run lint` is clean. Maven not run (dispatcher rule; no backend file changed in this task). |
| Integration | pass | The panel reads `linked`, `ticketStatus`, `hasDraft`, `mayOpenForm`. These are exactly the fields of `IssuePanelResponse` and the `IssuePanel` schema, with no `canSearch` left. The feedback shape `{feedback: {code, params}}` matches `JiraReviewFeedbackController.java:54-56` and the client (`tsolveClient.js:141-147`). The manifest points the `jira:issuePanel` module at the new resource and the `tsolve-ui-endpoint` remote. |

### Stage 1 — Spec compliance

| AC | Verdict | Evidence |
|----|---------|----------|
| AC-001.4 | PASS | `IssuePanel.jsx:112-118` shows the button only when `!hasDraft && mayOpenForm && done`. `mayOpenForm` = member and no ticket of any source (`JiraAppSolutionServiceImpl.java:103,108`). `done` comes from the viewer's `requestJira` status read (`:30,87`). The click re-checks Done (`:57-61`). Tests: `IssuePanel.test.jsx:85-95`, and the BUG-215 tests at `:97-116`. |
| AC-008.1 | PASS | `IssuePanel.jsx:83-85` renders only `<Text>` MSG62. Test `:52-57`. |
| AC-008.2 | PASS | `IssuePanel.jsx:90-94`. Test `:59-64`, which also checks that the button is hidden. |
| AC-008.3 | PASS | `IssuePanel.jsx:100-111` shows MSG77 and "Continue draft". `openForm` skips the Done check when there is a Draft (`:58`), and the server answers with the Draft (`origin: DRAFT`). Backend `hasDraft` requires member and no ticket (`JiraAppSolutionServiceImpl.java:104-107`). Test `:74-83` checks that the modal opens with the Draft's Symptoms. |
| AC-008.7 | PASS | `IssuePanel.jsx:33` reads the feedback only for `CHANGES_REQUESTED`, and `:95-99` renders `message(code, params)`. `ticket-review` filters by writer (`inWriterIssue`, `JiraReviewFeedbackController.java:43`). Test `:66-72` checks the filled MSG64 text. |
| Spec change 14/11 | PASS | There is no search or preview code in `panel/`, and `TicketPreviewModal.jsx` is gone. Test `:118-123`. |
| Design edge case 5 (board drag) | PASS | The panel button is the way in when no popup comes (AC-001.4). |
| Design edge case 11 | PASS (see Issue 4) | Another writer's change request gives `feedback: null`, so the panel shows no MSG64. |

## Traceability

```
  ✓ AC-001.1  jira-app/TSolve_JiraApp/src/frontend/background/BackgroundApp.test.jsx, jira-app/TSolve_JiraApp/src/frontend/background/watchDone.test.js
  ✓ AC-001.2  backend/src/test/java/vn/tsolve/jiraapp/service/impl/ConfigJiraWorkspaceLookupTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-001.3  jira-app/TSolve_JiraApp/src/frontend/background/BackgroundApp.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/ConfigJiraWorkspaceLookupTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImplTest.java
  ✓ AC-001.4  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-001.5  jira-app/TSolve_JiraApp/src/frontend/background/BackgroundApp.test.jsx, jira-app/TSolve_JiraApp/src/frontend/background/watchDone.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-008.1  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessImplTest.java
  ✓ AC-008.2  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-008.3  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✓ AC-008.7  jira-app/TSolve_JiraApp/src/frontend/panel/IssuePanel.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraIssueAccessReviewAdapterTest.java
trace jira-app (STORY-001, STORY-008): 9/9 acceptance criteria have a test
```

## Issues

### Blocking

None.

### Informational

1. **[Robustness, Low] A failed Jira status read hides MSG62.**
   - `load` waits for the panel state and the Jira issue read together (`IssuePanel.jsx:30`). If `requestJira` fails
     (a viewer without browse permission on a field, a Jira hiccup), the panel shows MSG06 instead of MSG62 for an
     unlinked account. In the same case a linked one loses MSG65, MSG64 and MSG77.
   - AC-008.1 says "MSG62 and nothing else" when not linked.
   - Fix: read the issue only when `state.linked` is true, or use `Promise.allSettled` and treat a failed issue read as
     "not Done".
2. **[Robustness, Low] One failed click replaces the whole panel with MSG06.**
   - `openForm`'s `catch` sets `failed` (`IssuePanel.jsx:68-70`), and `:73-79` then renders only MSG06. MSG77 and the
     status disappear until a later `load` succeeds. Per BUG-214, `JIRA_ISSUE_CHANGED` does not always arrive, so in
     practice that means a page reload.
   - Fix: show the error next to the button (local state) and keep the panel.
3. **[Robustness, Low] No busy guard and no ordering on loads.**
   - "Write Solution" and "Continue draft" can be clicked twice while `openSolutionForm` runs (`IssuePanel.jsx:54-71`).
     Each click may ask the server for an AI draft (up to 18 s each).
   - Several `load()` calls (mount, `JIRA_ISSUE_CHANGED`, after `onDone`) can resolve out of order, so an older
     answer can overwrite a newer one.
   - Fix: disable the buttons while opening, and drop the answer of a `load` that is not the latest.
4. **[Judgment call] Some ticket states show an empty panel.**
   - The panel shows text only for `PENDING_REVIEW` and, through the feedback, for `CHANGES_REQUESTED` written by the
     viewer. For a ticket that is Published, Rejected or another status, and for another writer's change request, the
     panel body is empty (`IssuePanel.jsx:88-131`).
   - Design edge case 11 says "panel shows status only", but nothing is shown. The spec has no criterion for those
     states, so this does not block.
   - See Assumptions.
5. **[Test gaps, Low] Some paths have no test.**
   - No test covers the panel's MSG06 on a load failure, a failed or `null` feedback for `CHANGES_REQUESTED`
     (`:33` `.catch(() => null)`), `openSolutionForm` answering `mayOpen: false` (`:65-67`, which reloads), or
     `onDone` showing the flag and reloading (`:124-128`).
   - AC-008.1's "nothing else" is checked only as "no button".
6. **[Behaviour, Low] The BUG-215 reload depends on an event that BUG-214 says may not arrive.**
   - The panel's reload on `JIRA_ISSUE_CHANGED` (`IssuePanel.jsx:45-52`) uses the same event that BUG-214 found
     never reaching the background script.
   - If the panel does not get it either, a stale "Write Solution" stays visible until it is clicked. The click then
     re-checks the status and hides it (`:57-61`), so no form opens on a non-Done issue.
   - That keeps AC-001.4's intent, and the dev-site check (5.7.0, SAM1-99) passed. Mentioned only so the team knows
     the click check is the real guard.
7. **[Contract wording, Low; not this task's file] The schema text still says "Solution Form ticket".**
   - `tsolve-api.yaml:3036` and `:3044` say "the issue's Solution Form ticket". The server (`tickets.findStatus(issueKey)`)
     and the spec (13/11 changelog, "the issue's ticket", any source) mean a ticket of any source.
   - Fix: reword both descriptions with the next contract change.
8. **[Config, not this task] `manifest.yml:39` commits a personal ngrok `baseUrl`.**
   - It came in with KAN-22 (`b9e996f6`). Task 8 did not change it, but every deploy from this branch points at that
     tunnel. Out of this review's scope; mentioned for the team.

## Assumptions made

- **Empty panel for other ticket states (Issue 4).** The spec defines panel text only for not linked, Pending review,
  Changes requested (own ticket), Draft and the open-form button. I treated an empty panel for Published, Rejected and
  another writer's change request as compliant. **Judgment call for the user:** is an empty panel acceptable there, or
  should the panel show the ticket's status (as design edge case 11 says)? The latter is a spec change.
- **MSG77 only for members.** The server sets `hasDraft` only for members (`JiraAppSolutionServiceImpl.java:104`), so
  a user who lost membership no longer sees their Draft. AC-008.3 does not mention membership. I read this as
  intended, because a non-member cannot submit (AC-001.2). **Judgment call:** confirm.
- **"Continue draft" ignores the Done status.** With a Draft, the button opens the form even if the issue has left
  Done (`IssuePanel.jsx:58`). AC-008.3 has no Done condition, so I read this as correct.
- **The backend is not re-verified.** I did not run Maven (dispatcher rule). Task 8 changed no backend file, and the
  panel contract was checked by reading. The working tree had an uncommitted `package-lock.json` change that is not
  part of this feature. The jest run used that tree.

## Test Report

See task-8-issue-panel-ui-test-report.md
