---
module: workspace
feature: default-project-api
task: 12
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude-reviewer (independent subagent)
iteration: 1
---

# Review: workspace — Task 12: Default Project API with the Jira check

## Summary
Reviewed `5a363ceb` (code) within `db594997..030ed7b7` against STORY-012 and the reworded AC-003.6 / AC-003.14. Every criterion is implemented and has a test that checks it. The Jira check runs outside any transaction, between a read-only pre-read and the write transaction (`DepartmentServiceImpl.java:88-104`). It runs only when `defaultProjectId` names a Project that is not the pre-read current default (`:90`). The MSG04/MSG156/MSG157 mapping matches AC-012.5/8/9 (`:95, :100-101, :114`). The BUG-314 fix (`DepartmentRepository.java:72-78`: `FOR UPDATE` on `department` alone, then a fresh join statement) is correct under PostgreSQL READ COMMITTED. The IT `appliesTwoConcurrentDefaultChangesInTurn_AC_012_16_BUG_314` would fail on the old single-statement join. Workspace reaches tracker only through `tracker.service.TrackerProjectService` and `tracker.dto.ChatbotProjectCheck`. No token or Jira body is logged or audited. The targeted tests and `spotless:check` pass. No CRITICAL findings.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-012.1–17 and AC-003.6/003.14 checked against the code; see Stage 1 table below |
| Completeness | pass | 17/17 ACs traced; no MISSING; no EXTRA behaviour |
| Correctness | pass | 206 targeted tests green (178 unit + 28 IT), spotless clean; no CRITICAL findings |
| Integration | pass | `getDepartment`/`updateDepartment` answer `DepartmentDetail` in `tsolve-api.yaml`; generated client has no diff after `generate:api`; tracker port used, not its client |

### Stage 1 — spec compliance

| AC | Status | Evidence |
|----|--------|----------|
| AC-012.1 | PASS | `DepartmentRepository.activeProjects` ORDER BY key; IT `listsTheDepartmentsActiveProjectsByKey_AC_012_1` (excludes Removed and other Department) |
| AC-012.2 | PASS | `DepartmentServiceImpl.java:140`; IT `storesTheCheckedProjectAndAnswersTheDetail_AC_012_4_AC_012_2` |
| AC-012.3 | PASS | `CURRENT` join `AND p.status = 'ACTIVE'` (`DepartmentRepository.java:53-58`); IT `readsARemovedDefaultAsNone_AC_012_3` |
| AC-012.4 | PASS | `:134-135`; IT + unit |
| AC-012.5 | PASS | pre-read `findActiveProject` filters department and ACTIVE, MSG04 `{}` on `defaultProjectId` before Jira (`:93-95`); IT covers other Department, Removed, random id; controller test asserts empty params |
| AC-012.6 | PASS | null/absent → `setDefaultProject(id, null)`, no Jira (`:90, :135`); IT `clearsTheDefaultWithNullOrAbsent_AC_012_6` |
| AC-012.7 | PASS | `TrackerProjectServiceImpl.checkChatbotProject`, `JiraClient.hasProjectPermissions/createFields` (URI templates, paging by `startAt`/`total`, `values` fallback); InOrder test `checksJiraBeforeTheWriteTransaction_AC_012_7` |
| AC-012.8 | PASS | no account / missing permission / 4xx → DENIED, 429/5xx/timeout/other → UNAVAILABLE, both → MSG156 `{tracker_name: "Jira"}` (`:101`) |
| AC-012.9 | PASS | no Task or blocking required field → NOT_READY → MSG157 `{project_name: key}` (`:100`) |
| AC-012.10 | PASS | `:90` returns before Jira; unit `keepsTheCurrentDefaultWithoutJira_AC_012_10`, `sendsNoJiraCallForNull..._AC_012_10` |
| AC-012.11 | PASS | `LinkedHashMap` details, `defaultProject` only when changed, `spaceId` only when renamed (`:121-139`); unit `auditsDefaultProjectOnlyWhenItChanged_AC_012_11` |
| AC-012.12 | PASS | rename branch skipped when name equal (`:130`); IT `changesOnlyTheDefaultWhenTheNameIsTheSame_AC_012_12` |
| AC-012.13 | PASS | `lockActiveProject ... FOR SHARE` in the write tx, MSG04 (`:110-114`); unit test (Mockito only) |
| AC-012.14 | PASS | scope first (`:85`), 404 pre-read before Jira, `@Valid`/`@PreAuthorize` before service; controller + unit tests |
| AC-012.15 | PASS | rename before `setDefaultProject`, any throw rolls back the template tx; IT MSG43-after-READY and audit-failure cases |
| AC-012.16 | PASS | BUG-314 two-statement lock; IT race test asserts second `before` = first `after` and the column = last applied |
| AC-012.17 | PASS | logs carry key + status/class name only (`TrackerProjectServiceImpl.java:110-122`); `JiraClient.call` keeps no body; unit log-capture test with a secret-bearing exception; IT checks audit/log hold no token |
| AC-003.6 (reworded) | PASS | IT `keepsStatusDefaultProjectAndProjectsWhenTheDefaultIsSent_AC_003_6` |
| AC-003.14 (reworded) | PASS | `:118-119`; IT `addsNoAuditWhenNameAndDefaultAreUnchanged_AC_003_14` (no Jira, no audit) |

## Traceability
```
trace workspace (STORY-012): 17/17 acceptance criteria have a test
```
(Every AC-012.1–012.17 is listed with at least one of DepartmentServiceImplTest, DepartmentControllerTest, DepartmentRenameIT, TrackerProjectServiceImplTest, JiraClientTest. The tool lists JiraClientTest only under AC-012.17, but its `..._AC_012_7` tests exist and pass.)

## Issues
CRITICAL: none.

Informational (do not block):
1. `backend/src/main/java/vn/tsolve/workspace/service/impl/DepartmentServiceImpl.java:89-117`: the Jira check is decided on the pre-read, and the write uses the locked re-read. In this race, request A sends the default it pre-read (P1) and skips Jira, while request B commits P2. A then lands with `defaultChanged = true` and puts P1 back without a new check, which silently undoes B's choice (last writer wins). The inline comment at `:109` admits this. It fits AC-012.7 as worded ("before it opens the write transaction"), and P1 passed a check when it was first chosen. It is a lost-update window that comes from D29 ("always send the current value"). Fix if wanted: in `save`, refuse with a retryable error when `wanted` differs from the locked default and the pre-read skipped the check. See Assumptions.
2. `DepartmentRepository.java:72-78, 104-106`: the lock order is `department FOR UPDATE`, then `project FOR SHARE`. The future remove-Project story (GBR-21) must take its locks in the same order, or it can deadlock with this save. Add a note in that story's design.
3. `docs/bug-log.md` BUG-314 row: "Fixed in" still reads `KAN-121 (uncommitted)`, although the fix is in `5a363ceb`. The note says the design's SQL "should be updated", but `design.md:651` already describes the two statements. Update the row.
4. `DepartmentRenameIT` AC-012.17 mocks `TrackerProjectService`, so the IT proves only the audit and the workspace logs. The real redaction is proved by `TrackerProjectServiceImplTest.logsTheKeyAndJirasStatusOnly_AC_012_17` and the JiraClientTest `..._AC_012_17` cases. That is adequate, but these are unit-level only.
5. The task's Definition of Done asks for the three Jira calls to be made once by hand on staging, with the response shapes noted in the task log. No `artifacts/05-implementation/workspace/task-12-default-project-api.md` session log exists, so I could not confirm it. The client parses `issueTypes`/`fields` with a `values` fallback, so a shape mismatch is low-risk. Confirm the manual check was done.

## Assumptions made
- Finding 1: I took the race "a stale pre-read default is re-applied without a Jira check" as acceptable under AC-012.7's wording, because it is last-writer-wins, like AC-003.16. **Open judgment call for the user:** accept it, or require the save to fail or re-check when the locked default differs from the pre-read one.
- The task has no session log, so I took the commit range from the dispatcher (`5a363ceb`) without cross-checking it against a log.

## Test Report
See `task-12-default-project-api-test-report.md`. Targeted backend: JiraClientTest 78, TrackerProjectServiceImplTest 37, DepartmentControllerTest 40, DepartmentServiceImplTest 23 (178 unit, 0 failures); DepartmentRenameIT 28 (0 failures); `spotless:check` clean; BUILD SUCCESS.
