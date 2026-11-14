---
module: jira-app
feature: solution-form-ui
task: 7
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 7: Solution Form and AI Rewrite Preview (solution-form-ui, KAN-126)

Scope: commit `4b34ca3c` and the form changes in `ffebe905`, judged on HEAD `622c71e6` of
`feat/KAN-126-jira-app-solution-form-v2` (repo `default`). Files: `jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.jsx`,
`form/sections.js`, `background/BackgroundApp.jsx`, their tests, and `test/forge-react.jsx`. The client calls they use
(`api/tsolveClient.js`) were read for behaviour but belong to Task 6.
Contract: `harnessclaude/artifacts/03-modules/jira-app/spec.md` (approved, including the 14/11 rows: Close keeps a
Draft, the "Saved as draft" flag, STORY-006 retired). Edge cases: `harnessclaude/artifacts/04-plans/jira-app/iteration-2/design.md`
(including "Change — 2026-11-14: Close keeps a Draft").
Passes run: code-review (spec compliance, then quality). No security-review pass: this task adds no endpoint, no
authorization rule and no storage; the form sends only what the user typed to FIT-authenticated endpoints reviewed in Task 5.

## Summary

The form meets the spec as it stands after the 14/11 change. Every Jest test passes (12 suites, 141 tests) and ESLint
is clean. Every criterion of STORY-002, STORY-004, STORY-005 and STORY-007 has a test that names it; the one ✗ in the
trace, AC-005.3, is a server criterion of Task 2 whose tests live under `backend/src/test/java/vn/tsolve/ticket/**`,
outside the module's trace paths (accepted the same way in the Task 2 and Task 5 reviews).

What the code does, checked against the spec:
- **AC-002.1, AC-002.2, AC-005.2:** the form shows whatever sections the server returned (Draft, AI draft or issue
  prefill) and the MSG67 warning when the server sends it.
- **AC-002.3, AC-002.4:** Finish checks the four required sections (trimmed) and the 10,000-character total counted in
  code points (`sections.js:22-24`), the same count as the server (`SolutionSections.length()`), and sends nothing on a
  failure. A server MSG01 is shown under the section it names; the server's field ids match `SECTIONS`.
- **AC-004.2, AC-004.4:** success calls `onDone('submitted')` and the caller shows MSG66 as a Jira flag; MSG69 from the
  client's 3 retries keeps the form and its content.
- **AC-005.1, AC-005.4, AC-005.5:** both the Close button and the Modal's own close control (`onClose`) call `close`,
  which `PUT`s the current content as the Draft, closes the form and shows the "Saved as draft" flag; a failure shows
  MSG06 and keeps the form open with its content. There is no Save as draft button, no MSG70, and Close never calls
  `submit`.
- **AC-007.1 to AC-007.4:** the rewrite step shows the current and rewritten text section by section; Accept replaces
  the sections, Discard keeps them, and any rewrite failure (including the client's 20 s timeout) shows MSG68 with the
  Solution unchanged.

No CRITICAL finding, no failing test and no criterion of this task without a test, so the verdict is **pass**. Two
MEDIUM items (issues 1 and 2) are worth fixing but break no criterion as written.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.1 to AC-002.5, AC-004.2, AC-004.4, AC-005.1, AC-005.2, AC-005.4, AC-005.5, AC-007.1 to AC-007.4 behave as specified. Every 4xx other than MSG01/MSG02 is shown as MSG06 (issue 1). |
| Completeness | pass | Trace 17/18; AC-005.3 is Task 2's, tested outside the trace paths. The UI side of AC-005.2 has no test named for it (issue 4). The task text in `tasks.md` still describes the 11/11 form (issue 7). |
| Correctness | pass | 141/141 Jest tests, ESLint clean. Design edge case 4 (10,000 accepted, 10,001 refused, emoji counted once) is tested; edge case 2 (double Finish) is handled by the client and server, with a small UI race left (issue 2). |
| Integration | pass | The form calls only `tsolveClient.js` (NF 3). Request bodies match `SolutionSections` and `SubmitSolutionRequest`; MSG texts match `frontend/src/shared/messages/catalog.ts`. `BackgroundApp` and `IssuePanel` both use `SolutionFormModal` and `doneFlag`. |

## Traceability

```
$ bun harnessclaude/hooks/sdd.ts trace jira-app STORY-002 STORY-004 STORY-005 STORY-007
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
  ✗ AC-005.3  no test under this module's paths (jira-app/**, backend/src/main/java/vn/tsolve/jiraapp/**, backend/src/test/java/vn/tsolve/jiraapp/**, frontend/openapi/**, frontend/src/shared/api/generated/**, docs/bug-log.md) — tests elsewhere? add their folder to paths mentions it
  ✓ AC-005.4  SolutionFormModal.test.jsx
  ✓ AC-005.5  SolutionFormModal.test.jsx
  ✓ AC-007.1  SolutionFormModal.test.jsx, JiraAppSolutionServiceImplTest.java, SolutionFormAiImplTest.java
  ✓ AC-007.2  SolutionFormModal.test.jsx
  ✓ AC-007.3  SolutionFormModal.test.jsx
  ✓ AC-007.4  SolutionFormModal.test.jsx, solutionClient.test.js, SolutionFormAiImplTest.java, JiraAppSolutionControllerTest.java
trace jira-app (STORY-002, STORY-004, STORY-005, STORY-007): 17/18 acceptance criteria have a test
```

AC-005.3 is tested by `backend/src/test/java/vn/tsolve/ticket/service/impl/SolutionDraftServiceImplTest.java`
(`returnsOnlyTheCreatorsOwnDraft_AC_005_3`) and `backend/src/test/java/vn/tsolve/ticket/SolutionFormTicketIT.java:165`
(`keepsOneDraftPerCreatorAndIssue_AC_005_1_AC_005_3`, which checks that another user gets no Draft). It is not a Task 7
criterion.

## Issues

1. **MEDIUM — every refusal other than MSG01/MSG02 on Finish is shown as MSG06.**
   - **Where:** `jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.jsx:85`.
   - **Actual:** `code === 'MSG69' ? 'MSG69' : 'MSG06'`. A first-try 409 MSG31 (the issue's ticket was created meanwhile,
     e.g. by a Jira import or another member, AC-004.3), 409 MSG40 (Project no longer active) and 403 MSG62/MSG08 all
     show "Something went wrong. Please try again later." and keep the form open. The user retries and gets the same
     result each time; the content can still be kept by Close.
   - **Expected:** the spec defines the server answer for AC-004.3 but says nothing about what the form then shows, so
     this is not a criterion breach (see Assumption 2). The generic text is misleading for a refusal that will never
     succeed on retry.
   - **Suggested fix:** show the server's code when the client kept it (`failure.status` 4xx and the code is in
     `messages.js`), or treat 409 MSG31 as "already submitted" and close with a flag. If the user wants a specific
     behaviour, add it to the spec first.

2. **MEDIUM — Finish and Close can both run when clicked in quick succession.**
   - **Where:** `SolutionFormModal.jsx:93-98` (`finish` has no busy guard) and `:103-106` (`close` reads `busy` from
     the render, not from a ref).
   - **Scenario:** the user clicks Finish and presses Escape (or the X) before the re-render that disables the buttons.
     In Forge UI Kit, events cross the bridge asynchronously, so this window is wider than in a browser. `close` sees
     `busy === false`, saves the Draft while `submit` runs, and both paths call `onDone`, so the user sees MSG66 and
     "Saved as draft". If the Draft `PUT` lands after the submission, a Draft row is left for an issue that has a
     ticket. It is harmless for the panel (AC-008.3 shows MSG77 only while the ticket does not exist) but it is an
     orphan row. A double click on Finish is already safe: the server stores one ticket (design edge case 2).
   - **Suggested fix:** keep the in-flight state in a `useRef` checked at the top of `finish`, `close` and `rewrite`,
     and call `onDone` at most once. Add a test that fires Finish then the dialog close before the submit resolves.

3. **LOW — no way to leave the form while T-Solve is down.**
   - **Where:** `SolutionFormModal.jsx:111-115`.
   - **Actual:** after MSG69, Close and Escape both try to save the Draft; when that fails too, the form stays open with
     MSG06 (as AC-005.4 requires), and every further close does the same. The only way out is to reload the Jira page,
     which loses the content.
   - **Note:** this follows AC-005.4 literally. Whether the user should be able to leave without saving after a failed
     save is a spec question (Assumption 3).

4. **LOW — the UI side of AC-005.2 has no test named for it.**
   - **Where:** `jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx` (no `AC-005.2` test); the task
     text lists AC-005.2 among the UI tests.
   - **Actual:** the criterion is traced only through `JiraAppSolutionServiceImplTest.java`. The form fills from
     `form.sections` whatever the origin, and the default `open()` helper uses `origin: 'DRAFT'`, so the behaviour is
     exercised but not named.
   - **Suggested fix:** add `AC-005.2 fills the sections from the Draft` with a Draft-origin payload.

5. **LOW — the client's 20 s budget leaves 2 s over the server's 18 s AI deadline when opening the form.**
   - **Where:** `background/BackgroundApp.jsx:35` with `api/tsolveClient.js:9`.
   - **Actual:** if `POST …/solution-form` takes longer than 20 s end to end (18 s AI deadline plus the FIT check,
     database reads and the Forge hop), the client times out and the form does not open at all, instead of opening
     with the issue prefill and MSG67 (AC-002.2). The panel button stays the way in. Low likelihood; worth a note in
     the design's edge case 8.

6. **LOW — stale comments in the client the form uses.**
   - **Where:** `api/tsolveClient.js:84` ("Finish or Close: creates the issue's ticket") and `:113` ("Save as draft").
   - **Actual:** since 14/11 Close never creates a ticket and there is no Save as draft button. Task 6's file, touched by
     the same change; fix with the next edit.

7. **INFO — the Task 7 text in `tasks.md` describes the 11/11 form.**
   - **Where:** `harnessclaude/artifacts/04-plans/jira-app/iteration-2/tasks.md`, "Task 7": Save as draft, MSG41, MSG70
     confirm, tests AC-006.1/AC-006.3, files `SolutionForm.jsx`, `validation.js`, `RewritePreview.jsx`.
   - **Actual:** the code has `SolutionFormModal.jsx` (with `RewritePreview` inside) and `sections.js`; Task 10 records
     the 14/11 change. The spec is the contract and the code follows it; the task text is only out of date.

8. **INFO — a server MSG02 reads "10000", the client's "10,000".**
   - **Where:** `backend/src/main/java/vn/tsolve/ticket/exception/SolutionTooLongException.java:11` sends `max_length`
     as a number; `SolutionFormModal`'s own check (`sections.js:36`) sends "10,000". Only reachable when the client check
     is bypassed. Cosmetic.

9. **INFO — the Done watcher reads the issue every 4 s while the issue view is visible.**
   - **Where:** `background/BackgroundApp.jsx:14` and `:67` (from the BUG-212 fix `a5a6d4ca`, in this task's file).
   - One `requestJira` status read per visible issue view per 4 s, as the viewer, stopping once the form was offered. It
     is a deliberate workaround (BUG-212); watch for Jira rate-limit responses on busy users.

10. **INFO — the session log was written after the fact.** `harnessclaude/artifacts/05-implementation/jira-app/task-7-solution-form-ui.md`
    says it was reconstructed on 14/11 from the commits. This review does not rely on it.

### What was checked and found sound
- MSG01 under the first empty required section, cleared when that section (or any section, for MSG02) is edited;
  Workaround optional; whitespace-only counts as empty.
- Code-point counting at exactly 10,000 (accepted) and 10,001 (MSG02), with emoji.
- Close from the rewrite step saves the pre-rewrite sections (the rewrite is not accepted), which matches Discard.
- A successful rewrite clears the MSG67 notice; a failed one leaves the sections untouched.
- `doneFlag` ids are per issue and outcome; MSG66 is `success`, "Saved as draft" is `info` — the 14/11 wording.
- `BackgroundApp` offers the form at most once per load, only for the viewer's own move into Done, and never when the
  server says `mayOpen: false` (AC-001.3) or when reading the issue or T-Solve fails.
- The `test/forge-react.jsx` Modal stand-in exposes the close control as a button that calls `onClose`, which is how
  the AC-005.1 "dialog itself is closed" test runs; UI Kit's Modal also reports its X and Escape through `onClose`.
- No `console` calls and no Solution text in logs (NF 3).

## Assumptions made / open judgment calls

1. **AC-005.3 counts as tested** although `sdd.ts trace` marks it ✗: it is Task 2's server criterion, and its tests in
   `backend/src/test/java/vn/tsolve/ticket/**` check it. Same call as the Task 2 and Task 5 reviews. The user may add
   that folder to jira-app's trace paths.
2. **What the form shows for a 409 or 403 on Finish is unspecified.** I treated MSG06 for those as a MEDIUM quality
   issue (issue 1), not a breach of AC-004.3, which only defines the server's answer. If the user wants MSG31 or a
   specific outcome shown, that is a spec addition.
3. **AC-005.4 is applied literally** — a failed Draft save keeps the form open even when the user wants to leave. I did
   not fail the task for the resulting trap (issue 3); the user may want a "close without saving" escape in the spec.
4. **AC-002.3 "under that section"** read as: MSG01 under the first empty required section, one at a time (the server
   does the same, in form order). If the user expects MSG01 under every empty required section at once, this is a defect.
5. **MEDIUM issues do not block.** The evaluate rules fail only on CRITICAL code findings, Critical/High security
   findings, failing tests or criteria without a test. I recommend fixing issues 1 and 2 before the module is completed.
6. **Environment limits.** `mvn verify` was not run (dispatcher's instruction; CI runs it), nor `forge lint` or the
   dev-site check. I made no status change in `progress.json`, as the dispatcher asked.

## Test Report

See `harnessclaude/artifacts/06-evaluation/jira-app/task-7-solution-form-ui-test-report.md`.

| Check | Result |
|-------|--------|
| Forge `npx jest` | 12 suites, 141/141 pass (SolutionFormModal 18, BackgroundApp 10) |
| Forge `npm run lint` (ESLint) | pass |
| `sdd.ts trace` (STORY-002, 004, 005, 007) | 17/18; AC-005.3 tested outside the trace paths (Task 2) |
| `mvn verify` | not run (dispatcher; CI) |
| `forge lint`, dev-site check | not run by the reviewer |
| Coverage | not measured; trace used instead |
