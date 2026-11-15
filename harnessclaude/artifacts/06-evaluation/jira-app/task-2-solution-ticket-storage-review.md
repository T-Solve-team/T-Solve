---
module: jira-app
feature: solution-ticket-storage
task: 2
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: jira-app — Task 2: Solution Form ticket storage and Drafts

## Summary

I reviewed the code at HEAD (`feat/KAN-126-jira-app-solution-form-v2`, 221d53ca). The feature owns commit 285c597e and the
fix 0077864a (BUG-200, logged on the branch as BUG-198 and renumbered when main was merged). Later commits touched only
`TicketRepository` (Published-ticket queries for Task 5) and the formatting; the storage code I judged is unchanged.

The storage does what the spec asks for this task's criteria. A Solution Form ticket is inserted as
`SOLUTION_FORM`/`PENDING_REVIEW` with the writer id and name, the issue key, the title cut to 200 code points, and
`review_due_date` = the Vietnam calendar day + the Project's `review_range` (3 when not positive). The 009/019 triggers
set the queue entry time and NOTIFY. Duplicates are refused atomically by `INSERT … ON CONFLICT (source_ticket_id) DO
NOTHING` on the existing `uq_ticket_ticket_id`, and this becomes 409 MSG31. Server-side MSG01 and MSG02 (in code points)
apply unless all five sections are empty (Close). The writer's Draft is deleted in the same transaction. Drafts are
keyed by creator, Project and issue.

I found no CRITICAL issue. The main open point is a spec-wording gap. `findStatus` and the 409 count a ticket of any
source with the same Ticket ID anywhere in the Workspace, but AC-001.2, AC-004.1 and AC-004.3 speak of "a Solution Form
ticket … in that Project". The schema makes the wider rule unavoidable (see Issue 1). I recorded it as an assumption and
did not fail the task on it.

Per the dispatcher I did not run Maven. The main session runs the backend suite and writes the test report. The verdict
assumes that report shows the tests below passing. A failing test there overrides this pass.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Stage 1 below: every criterion of the task is met in code. |
| Completeness | pass | Nothing MISSING in substance. AC-005.3 shows as untested in `sdd.ts trace` only because its tests are under `backend/src/test/java/vn/tsolve/ticket/**`, which is not in the module's trace paths (Assumption 1). Planned items were dropped with a reason in design.md: the partial unique index (replaced by `uq_ticket_ticket_id`) and `Ticket.submitted(...)` (replaced by a native insert, because `writer_id`/`review_due_date` are read-only in the entity). Two planned items were dropped without a note: `SolutionDraftService.exists` (callers use `find`) and the `project` parameter of `findStatus` (Issue 1). |
| Correctness | pass (pending test report) | No CRITICAL finding. The race is handled in the database, not by check-then-insert. The Vietnam date boundary is unit-tested (`SolutionFormTicketServiceImplTest:37-39`). Test results: see the test report. |
| Integration | pass | The new ticket is read by `TicketReviewReadService.queue` with entry time, due date and author (`SolutionFormTicketIT:101-131`). It stays in tenant A, not tenant B (`:128-130`). Consumer: `JiraAppSolutionServiceImpl:63,83-89,98,115-119`. `ReviewStorageIT`'s upgrade master leaves 027 out, because 027 needs `project.public_id` (0077864a). |

### Stage 1 — spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-002.5 server rejects an empty required section (400 MSG01) or more than 10,000 characters (400 MSG02) | PASS | `SolutionFormTicketServiceImpl.java:47-49,75-89`: Symptoms, Root cause, Resolution steps and Scope are required, in form order, with `field_name` and `field`. `SolutionSections.java:30-42` counts code points. `SolutionSectionRequiredException` is a 400 `FieldMessageCodeException` and `SolutionTooLongException` a 400 MSG02. Tests: `refusesAnEmptyRequiredSectionWithMsg01OnThatSection_AC_002_5` and `acceptsExactlyTenThousandCodePointsAndRefusesOneMoreWithMsg02_AC_002_5` (9,997 emoji + 3 accepted, 9,998 + 3 refused). |
| 2 | AC-004.1 one Pending review ticket with issue key, summary, Project, Solution, writer; enters the Review Queue with entry time and due date; Draft deleted | PASS | `TicketRepository.java:55-77` (insert … select from ACTIVE project, due date at `:60`). `SolutionFormTicketServiceImpl.java:50` (Asia/Ho_Chi_Minh day), `:64` (`deleteDraft`). IT `entersTheReviewQueueWithEntryTimeAndDueDate_AC_004_1` checks the queue row, `writer_id`, due date = VN today + 5, and that the Draft is gone. |
| 3 | AC-004.3 no second ticket; 409 | PASS | `TicketRepository.java:63` `on conflict (source_ticket_id) do nothing`. `SolutionFormTicketServiceImpl.java:100-105` → `TicketAlreadyExistsException` (a `BusinessRuleException`, which `GlobalExceptionHandler.java:82-85` maps to 409, MSG31). IT `createsOneTicketWhenTwoSubmitsRace_AC_004_3` runs two threads and checks for one "created", one 409 and one row. Under READ COMMITTED the losing insert waits for the winner's commit, so its follow-up `findStatusByTicketId` sees the row. |
| 4 | AC-005.1 Draft stored unvalidated as the user's only Draft for the issue, no ticket | PASS | `SolutionDraftServiceImpl.java:26-32` (find-then-replace). Changeset 027 `uq_solution_draft (creator_id, project_id, jira_issue_key)`. Tests: `storesAFirstDraftUnvalidated_AC_005_1`, `replacesTheOnlyDraftOfTheIssue_AC_005_1`, IT `keepsOneDraftPerCreatorAndIssue_AC_005_1_AC_005_3` (one row, emoji round-trip through JSONB). |
| 5 | AC-005.3 Draft shown only to its creator | PASS (trace: MISSING, see Assumption 1) | `SolutionDraftRepository.java:13` always filters by `creatorId`. `JiraAppSolutionServiceImpl.java:67,98,118` passes the FIT user's id. IT `:178-179` (another user gets nothing while the writer has a Draft). Unit test `returnsOnlyTheCreatorsOwnDraft_AC_005_3`. |
| 6 | AC-006.2 Close creates the ticket in Pending review as AC-004.1, every section empty | PASS | `SolutionFormTicketServiceImpl.java:47` skips validation when `isEmpty()`. Test `storesAnEmptySolutionWhenTheFormWasClosed_AC_006_2` also checks that the Draft is deleted. See Issue 3 for whitespace. |

Changeset review (`027-jira-app-solution-draft.sql`): it has a formatted-SQL header, the author `ticket`, FKs to
`app_user(id)` and `project(public_id)`, a unique key per creator, Project and issue, and a `--rollback DROP TABLE
solution_draft;` that undoes it fully. It is included in `db.changelog-master.xml:51` after 026. It creates no partial
unique index, by design (design.md Key Decisions, "Duplicate guard"). `uq_ticket_ticket_id` (003:40, renamed column in
008:6) covers the race.

## Traceability

```
  ✓ AC-002.1  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/SolutionFormAiImplTest.java
  ✓ AC-002.2  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/background/watchDone.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/service/impl/SolutionFormAiImplTest.java
  ✓ AC-002.3  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-002.4  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-002.5  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-004.1  jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-004.2  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-004.3  jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-004.4  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js
  ✓ AC-005.1  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx, jira-app/TSolve_JiraApp/src/frontend/api/solutionClient.test.js, backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java, backend/src/test/java/vn/tsolve/jiraapp/controller/JiraAppSolutionControllerTest.java
  ✓ AC-005.2  backend/src/test/java/vn/tsolve/jiraapp/service/impl/JiraAppSolutionServiceImplTest.java
  ✗ AC-005.3  no test under this module's paths (jira-app/**, backend/src/main/java/vn/tsolve/jiraapp/**, backend/src/test/java/vn/tsolve/jiraapp/**, frontend/openapi/**, frontend/src/shared/api/generated/**, docs/bug-log.md) — tests elsewhere? add their folder to paths mentions it
  ✓ AC-005.4  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-006.1  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-006.2  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
  ✓ AC-006.3  jira-app/TSolve_JiraApp/src/frontend/form/SolutionFormModal.test.jsx
trace jira-app (STORY-002, STORY-004, STORY-005, STORY-006): 15/16 acceptance criteria have a test
```

The trace does not see this task's own backend tests, because they live under `backend/src/test/java/vn/tsolve/ticket/`.
These tests name the task's criteria:

- `SolutionFormTicketServiceImplTest`: AC_002_5 (×2), AC_004_1 (×3), AC_004_3, AC_006_2, AC_008_2
- `SolutionDraftServiceImplTest`: AC_005_1 (×2), AC_005_3
- `SolutionFormTicketIT`: AC_004_1, AC_004_3, AC_005_1_AC_005_3
- `SolutionSectionsTest`: AC_006_2, BUG_200

I read each of them, and each one checks what it names. AC-005.3 is therefore covered: `SolutionDraftServiceImplTest:61-72` and
`SolutionFormTicketIT:164-183`, the latter against a real database.

## Issues

No CRITICAL issues.

Informational (do not block):

1. **`findStatus` is wider than AC-001.2 / AC-008.2 / AC-004.3 say.** `TicketRepository.java:119-121` and
   `SolutionFormTicketService.java:19-20` look up a ticket by Ticket ID with any source (`JIRA_IMPORT`, `CSV`, `MANUAL`,
   `SOLUTION_FORM`), in any Project of the Workspace. The task text asked for `findStatus(project, issueKey)`
   (tasks.md:46). The spec says "no Solution Form ticket exists for the issue in that Project" (AC-001.2), "a Solution
   Form ticket in Pending review" (AC-008.2) and "a Solution Form ticket already exists … in its Project" (AC-004.3).
   This makes a real difference. `JiraImportServiceImpl.java:129-186` stores Jira-imported tickets under the Jira issue
   key, as PUBLISHED. Take an issue that was imported earlier, then reopened and moved to Done again. The form will not
   open, the panel gets status PUBLISHED (`JiraAppSolutionServiceImpl.java:63,115`), and a direct submit answers 409
   MSG31 (`SolutionFormTicketServiceImpl.java:100-103`). A CSV ticket in another Project whose id happens to equal
   `ABC-1` has the same effect, and the panel of project ABC then shows that other Project's ticket status. The global
   `uq_ticket_ticket_id` (003:40) means the server cannot store a second ticket with that key anyway, so the code
   follows the schema, and it matches the Jira import's own skip of taken keys (`JiraImportServiceImpl.java:129-132`).
   It does not follow the spec's wording.
   Fix: do not change the code. Change the spec (plan skill, Change Mode) so that AC-001.2, AC-004.1 and AC-004.3 say
   "a ticket with the issue key as Ticket ID exists in the Workspace (any source)", or else decide to scope
   `uq_ticket_ticket_id` per Project and source. Either way, rename or document `findStatus` to say "any source", and
   decide what the panel shows for a PUBLISHED imported ticket (Task 5/8). This needs the user's judgment (Assumption 2).

2. **Stale plan text about the partial unique index.** `design.md:143` (Edge case 2: "One ticket (partial unique
   index)") and `design.md:131` (tell Dương about "the partial unique index") still describe the index that the Key
   Decisions table (design.md, "Duplicate guard") and the 027 comment dropped. `tasks.md:40-41,46` still list the
   partial index, `findStatus(project, …)` and `SolutionDraftService.exists`.
   Fix: bring design.md rows 131 and 143 and Task 2's text in line with the built design.

3. **A whitespace-only Close is stored as whitespace, not as empty sections.** `SolutionSections.isEmpty()`
   (`SolutionSections.java:22-28`) uses `isBlank()`, but the service stores the original strings
   (`SolutionFormTicketServiceImpl.java:57-61`). The test pins `" "` in `resolution_steps`
   (`SolutionFormTicketServiceImplTest:107-126`). AC-006.2 says "every Solution section empty".
   Fix: when `isEmpty()`, pass `""` for the four NOT NULL sections and `null` for Workaround.

4. **"Finish with all five empty" cannot be told apart from Close.** A direct API call (or a client bug) that sends
   five empty sections on Finish creates an empty ticket instead of 400 MSG01 (`SolutionFormTicketServiceImpl.java:47`).
   The spec's interface contract ("all empty = Close") allows this, so it is not a defect. AC-002.5 ("whatever the Jira
   App checked") reads against it (Assumption 3).
   Fix, if the user wants it: an explicit `close: true` flag on `POST …/solution`.

5. **The first Draft save can race.** `SolutionDraftServiceImpl.java:26-32` is find-then-insert. Two concurrent first
   saves by the same user for the same issue hit `uq_solution_draft`, and the loser gets an unhandled
   `DataIntegrityViolationException`, which becomes 500 (the client shows MSG06, AC-005.4). Impact is low, since one
   user saves one form.
   Fix: `INSERT … ON CONFLICT (creator_id, project_id, jira_issue_key) DO UPDATE SET content = …, updated_at = …`.

6. **A Project that stops being ACTIVE answers MSG40, an import message.** `SolutionFormTicketServiceImpl.java:104`
   reuses `ProjectNotActiveException`, whose text is about an import Confirm (`ProjectNotActiveException.java:7`).
   This happens only if the Project goes inactive between `JiraIssueAccess` and the insert. The insert also does not
   check that the Department is ACTIVE (`TicketRepository.java:62`).
   Fix: a dedicated message, or reuse MSG08/MSG40 on purpose and record that in the design.

7. **The IT due-date check can fail around midnight.** `SolutionFormTicketIT.java:109` takes today from
   `LocalDate.now(Asia/Ho_Chi_Minh)`, while the service uses the `Clock` bean. A run that crosses 00:00 Vietnam time
   between the submit and this line fails.
   Fix: take today from the injected `Clock`, or compute it before the submit and allow either day.

8. **Other members' Drafts are left orphaned.** Only the writer's Draft is deleted (matches AC-004.1). Drafts of other
   members for the same issue stay in `solution_draft` and are never shown again, because the panel hides Drafts once a
   ticket exists (`JiraAppSolutionServiceImpl.java:116-119`).
   Fix (optional): delete all Drafts for the Project and issue on submit, or a cleanup job.

## Assumptions made

1. **AC-005.3 counts as tested** even though `sdd.ts trace` marks it ✗. Its tests (`SolutionDraftServiceImplTest
   .returnsOnlyTheCreatorsOwnDraft_AC_005_3`, `SolutionFormTicketIT.keepsOneDraftPerCreatorAndIssue_AC_005_1_AC_005_3`)
   live in the `ticket` package, outside the module's trace paths, and they check the criterion. The tests also put the
   AC id at the end of the name, while the spec's Definition of Done says the name starts with it. The other tests
   traced for this module use the same suffix style, so I did not treat this as missing. Open for the user: add
   `backend/src/test/java/vn/tsolve/ticket/**` to jira-app's trace paths, or accept as is.
2. **The "any source, whole Workspace" rule for one ticket per issue is accepted** (Issue 1). It follows the global
   `uq_ticket_ticket_id` and the Jira import, but the spec says "Solution Form ticket … in that Project". Open for the
   user: correct the spec wording, or change the schema. If the user holds the spec literally, this becomes a failing
   finding against AC-004.1 (an issue with an imported ticket cannot get its Solution Form ticket) and AC-001.2.
3. **"All five empty = Close" on the server is accepted** as the spec's interface contract says, even though it lets an
   empty Finish through without MSG01 (Issue 4).
4. **"10,000 characters" means Unicode code points**, as design.md Edge case 4 decides. Java `String.length()` would
   count an emoji as 2.
5. **The verdict depends on the main session's test report.** I did not run Maven, as instructed. If the report shows
   any failing test in `SolutionFormTicketIT`, `SolutionFormTicketServiceImplTest`, `SolutionDraftServiceImplTest`,
   `SolutionSectionsTest` or `ReviewStorageIT`, or a Spotless failure, the verdict is fail.

## Test Report

See task-2-solution-ticket-storage-test-report.md
