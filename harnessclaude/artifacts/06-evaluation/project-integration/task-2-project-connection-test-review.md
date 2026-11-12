---
module: project-integration
feature: project-connection-test
task: 2
verdict: pass
attempt: 1
date: 2026-11-12
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: project-integration — Task 2: Test a Project connection and prefill settings (STORY-001, STORY-007, KAN-127)

## Summary

I reviewed commit `6d71a20d` (`6d71a20d^..6d71a20d`, 28 files: 9 backend main, 4 backend test, the OpenAPI contract
and 14 generated client files). I checked it against:
- spec STORY-001 and STORY-007 in full
- AC-002.4, AC-002.6 and AC-002.14
- the Interface Contracts for all three `/api/v1/projects` operations
- Task 2 in `iteration-1/tasks.md` and the design edge rows it names (1, 4, 5, 7, 14, 24–27, 31, 33, 35–37)
- `backend/AGENTS.md` §3–§6 and §9

Codex CLI (read-only, ephemeral) gave a second opinion. I checked each Codex finding myself and kept only the ones I
confirmed; each finding below names its source. I did not read the session log.

The endpoint does what the spec says. The checks run in the spec's order: Workspace binding, then caller
(`TSolveUser`), then DB eligibility, then key format, then the requested-key link, then Jira, then the canonical-key
link. Each refusal maps through the shared `GlobalExceptionHandler` to the exact status, code, params and field in
the `testProjectConnection` table. There is no module exception handler.

`testConnection` has no `@Transactional` (`ProjectServiceImpl.java:54`). The PostgreSQL IT's tracker double throws
when it is called inside a transaction, and all 9 IT cases pass. The "already linked" rule refuses every
non-`REMOVED` status (`ProjectServiceImpl.java:103`). Prefill takes the stored ids that are still in the returned
statuses, falls back to the Done-category ids, and uses 3 only when the range is SQL NULL.

The contract matches the spec tables for all three operations. Regenerating the client with orval at `6d71a20d`
leaves no diff.

There are no CRITICAL findings, and the tests pass. The one MAJOR finding is test strength, not a defect: the test
named AC-001.17 plants no credential or Jira body, so it cannot fail (details below). It is an open judgment call.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All 30 STORY-001/STORY-007 criteria and AC-002.4/002.6/002.14 hold in the code (Stage 1 table). |
| Completeness | pass | Every Task 2 criterion has a test naming it (trace 30/30 at `6d71a20d`). Nothing EXTRA: `CreateProjectRequest` is used only for Bean Validation, which Task 2 plans. There is no create or list endpoint yet. AC-001.17 and AC-002.14 have weak tests (Issues 1 and 2). |
| Correctness | pass | 75 unit + 9 IT pass in an isolated worktree, spotless passes. The full verify at this state passed: 1503 unit + 447 IT. No CRITICAL findings. |
| Integration | pass | The YAML matches the Interface Contracts. The generated client is untouched by hand. `ProjectService` keeps `findActivePublicIdByJiraProjectKey` for tracker. Project uses tracker only through `TrackerProjectService` and `tracker.dto`. The new constructor has no caller outside the test. The full IT suite loads the context, so there is no bean cycle. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.1 strip + `Locale.ROOT` upper for link lookup and `findProject` | PASS | `ProjectServiceImpl.java:60`. `stripsAndUpperCasesTheKey_AC_001_1`, `upperCasesWithRootEvenUnderTurkishLocale_AC_001_1` (`qi` → `QI` under `tr`). |
| 2 | AC-001.2 missing/null/empty/whitespace → 400 MSG01 on field, no Jira | PASS | `@NotBlank` (`ProjectConnectionTestRequest.java:7`). The handler's precedence makes MSG01 win over MSG02 (`GlobalExceptionHandler.java:63`). `refusesBlankKey_AC_001_2` covers `{}`, null, `""`, 3 spaces and 21 spaces; it checks the exact envelope and that the service is never called. |
| 3 | AC-001.3 >20 UTF-16 units → MSG02 `{field_name, max_length: 20}` | PASS | `@Size(max = 20)` counts `String.length()`. `refusesMoreThanTwentyUtf16Units_AC_001_3` (21 ASCII, 11 emoji = 22 units). `acceptsTwentyUtf16Units…` (20 ASCII, 10 emoji) passes through. |
| 4 | AC-001.4 format → 409 MSG48 `{tracker_name}`, no field, no Jira | PASS | `ProjectServiceImpl.java:61-63`. `refusesInvalidFormatBeforeLinkOrJira_AC_001_4` (5 keys, no link read, no tracker call). |
| 5 | AC-001.5 Active link anywhere → 409 MSG49 on field, owner Department | PASS | `refuseIfLinked` `:102-108` refuses any non-REMOVED status. Unit tests for both departments; envelope in `rendersLinkedProjectConflictEnvelope_AC_001_5`; IT `PCTACT`. |
| 6 | AC-001.6 Removed link of another Department → MSG49 | PASS | `:104`. `refusesRemovedLinkInAnotherDepartment_AC_001_6`. |
| 7 | AC-001.7 protected previous PM (different, Active, PM role, same Department) → MSG49 | PASS | SQL `manager_protected` (`ProjectLinkRepository.java:60-62`) plus `!caller.equals(managerId)` (`:105`). The unit test covers the protected and allowed cases. IT `protectsActiveManagerAndAllowsReconnectAfterDeactivation_AC_001_7…` runs on real SQL: refused with no Jira call, then allowed after the PM is deactivated. |
| 8 | AC-001.8 Jira only through `TrackerProjectService.findProject`, after the local check | PASS | `:65-67`. `callsTrackerExactlyOnceAfterTheLocalLinkCheck_AC_001_8` (InOrder, once, no more interactions); IT `tracker.calls()` assertions. |
| 9 | AC-001.9 200 with canonical key and each status `{id,name,done}` once | PASS | `:94-95`; the tracker dedupes by id (`TrackerProjectServiceImpl.java:45-47`). Unit test and exact-JSON controller test. |
| 10 | AC-001.10 new → `reconnect:false`, `projectId` omitted | PASS | `@JsonInclude(NON_NULL)` (`ProjectConnectionTestResponse.java:12`). Plain-Jackson unit test and MVC `doesNotHaveJsonPath`. |
| 11 | AC-001.11 eligible Removed own Department → `reconnect:true` + existing id | PASS | `:96-97`. Unit, MVC and IT (`PCTOLD`, `PCTNUL`, `PCTPRO`). |
| 12 | AC-001.12 new → Done-category ids and 3 | PASS | `:87-90,98-99`. `prefillsNewProjectWithDoneCategoryAndThreeDays…`. |
| 13 | AC-001.13 reconnect → stored ids still present, else Done-category | PASS | `:91-92,98`. Unit tests cover dedupe and stale ids, null/empty/stale fallback, and empty Jira statuses. IT reads real JSONB `["10001","STALE"]` → `["10001"]`, and SQL NULL → `["10002"]`. |
| 14 | AC-001.14 stored range, 3 only for SQL NULL | PASS | `:99` (`Optional.map` of a null `Integer` is empty). Unit NULL→3, 0→0, -1→-1; IT 0 stays 0, NULL → 3. |
| 15 | AC-001.15 NOT_FOUND/DENIED → 409 MSG48, no field | PASS | `:71-73`. Unit test for both outcomes; MVC envelope. |
| 16 | AC-001.16 UNAVAILABLE → 503 MSG06 `{}` | PASS | `:68-70`, `ProjectTrackerUnavailableException`. Unit, MVC and IT. |
| 17 | AC-001.17 no credential/Jira body in Project responses or logs | PASS (weak test) | No Project log statement. The response and exceptions carry only the lookup's key and statuses, fixed codes and DB Department names. `TrackerProjectLookup` has no credential or body field. Tracker tests `…_AC_013_12`/`_AC_013_13` plant credentials and bodies at the tracker boundary. The Project test itself cannot fail: Issue 1. |
| 18 | AC-001.18 no write on success or failure | PASS | IT snapshots `project`, `project_membership` and `audit_log_entry` around success (new, reconnect), MSG48, 503 and MSG49. The service has no write path. |
| 19 | AC-001.19 canonical key refused by 001.5–001.7 → MSG49 | PASS | `:76-79`. `refusesCanonicalLinkConflicts_AC_001_19` (Active foreign, Removed foreign, protected own). |
| 20 | AC-001.20 unreadable JSON → 400 MSG06 `{}`, no field, no Jira | PASS | Shared handler. `refusesUnreadableJson_AC_001_20` (`""`, `{`, `{"jiraProjectKey":}`, `[`). |
| 21 | AC-001.21 requested and canonical on two Projects → MSG49 with the canonical's Department | PASS | `:80-82` (and `:79` when the canonical is itself refused, same Department). Unit test for a canonical row that is ACTIVE and one that is REMOVED. |
| 22 | AC-001.22 exactly one of the two keys held by an eligible Removed Project → reconnect | PASS | `:84`. `reconnectsTheOnlyEligibleAliasLink_AC_001_22` (requested-only and canonical-only). Storing the canonical key on Save belongs to Task 3. |
| 23 | AC-002.4 `doneStatusIds` missing/null/empty → MSG01 | PASS (DTO level, as planned) | `@NotEmpty(message="MSG01")`; the handler maps `NotEmpty` → MSG01 `{field_name}`. Bean Validation test; endpoint assertions are in Task 3. |
| 24 | AC-002.6 `reviewRange` missing/null → MSG01 | PASS (DTO level, as planned) | `@NotNull`; Bean Validation test. |
| 25 | AC-002.14 `findActivePublicIdByJiraProjectKey` returns the new id | PASS (lookup only) | Renamed test `findsTheActiveProjectOfAJiraKey_AC_002_14`, as Task 2 asks. The "when a new Project commits" part needs Task 3: Issue 2. |
| 26 | AC-007.1 `@PreAuthorize` with `Roles.PROJECT_MANAGER` | PASS | `ProjectController.java:37`; reflection test. The other two handlers are Task 3 and Task 5. |
| 27 | AC-007.2 anonymous → 401 MSG07 | PASS | `refusesAnonymous_AC_007_2`, service never called. |
| 28 | AC-007.3 non-PM valid body → 403 MSG08 | PASS | `refusesEmployeeWithValidBody…`, `refusesAdminWithValidBody…`. The invalid-body → 400 order (Interface Contracts, row 25) is tested too. |
| 29 | AC-007.4 no/inactive Department, inactive account, role removed in DB → 403 | PASS | `findCallerDepartment` requires an Active user, an Active Department and a `user_role` PM row (`ProjectLinkRepository.java:42-44`). IT parameterized test with department, account and role changed in the DB while the session role stays: 403 `{}`, no field, no Jira call. |
| 30 | AC-007.5 no Workspace → 500 MSG06 | PASS | `:55`. `LocalTenantScope` throws `IllegalStateException`, which the catch-all turns into 500 MSG06. IT unbound request: no Jira call, main DB unchanged. |
| 31 | AC-007.6 only the bound Workspace's DB; same key allowed elsewhere | PASS | IT: `PCTNEW` exists only in BETA. In ACME it is allowed (no link). In BETA it is refused with BETA's Department, with no Jira call. |
| 32 | AC-007.7 scope from `TenantScope` and `app_user.department_id`, not the request | PASS | The request records hold only Project fields (reflection test). The Department comes from the join. |
| 33 | AC-007.8 Workspace and eligibility before format, link and Jira | PASS | `:55-61`. `refusesIneligibleCallerBeforeEvenInvalidKeyFormat…` (key `1BAD` gives 403, not 409), `derivesCallerFromSessionAfterWorkspaceBinding…`, `propagatesUnboundWorkspaceBeforeAnyLookup…`. |

Contract (`frontend/openapi/tsolve-api.yaml`, OpenAPI 3.1.0):
- **Operations:** the `projects` tag and the three operationIds are present, and every status code in each spec
  table is listed:
  - `testProjectConnection`: 200, 400, 401, 403, 409, 500, 503
  - `createProject`: 201, 200, 400, 401, 403, 409, 500, 503
  - `listProjects`: 200, 401, 403, 500
- **Errors:** all through the shared `Problem` / `ProblemDetail`.
- **`reviewRange` request:** `number`, `multipleOf: 1`, `minimum: 1`, `maximum: 2147483647`.
- **Response schemas:** `projectId` is optional on `ProjectConnectionTest`, `ProjectRow.projectManager` is required
  and nullable (`oneOf` with `null`), and `DepartmentRef` is reused. Every id is `format: uuid`. No request schema
  has a Workspace or Department property.
- **Generated client:** `getListProjectsQueryKey` is generated.

## Traceability

Run in a worktree at `6d71a20d`. The main checkout holds Task 3 work in progress.

```
  ✓ AC-001.1  …/project/service/impl/ProjectServiceImplTest.java
  ✓ AC-001.2  …/project/controller/ProjectControllerTest.java
  ✓ AC-001.3  …/project/controller/ProjectControllerTest.java
  ✓ AC-001.4  …/project/service/impl/ProjectServiceImplTest.java
  ✓ AC-001.5  ProjectServiceImplTest.java, ProjectControllerTest.java
  ✓ AC-001.6  ProjectServiceImplTest.java
  ✓ AC-001.7  ProjectServiceImplTest.java, ProjectConnectionIT.java
  ✓ AC-001.8  ProjectServiceImplTest.java, ProjectConnectionIT.java
  ✓ AC-001.9 … AC-001.11  ProjectServiceImplTest.java, ProjectControllerTest.java
  ✓ AC-001.12  ProjectServiceImplTest.java
  ✓ AC-001.13, AC-001.14  ProjectServiceImplTest.java, ProjectConnectionIT.java
  ✓ AC-001.15, AC-001.16  ProjectServiceImplTest.java, ProjectControllerTest.java
  ✓ AC-001.17  ProjectServiceImplTest.java
  ✓ AC-001.18  ProjectConnectionIT.java
  ✓ AC-001.19, AC-001.21, AC-001.22  ProjectServiceImplTest.java
  ✓ AC-001.20  ProjectControllerTest.java
  ✓ AC-007.1 … AC-007.3  ProjectControllerTest.java
  ✓ AC-007.4, AC-007.5  ProjectServiceImplTest.java, ProjectControllerTest.java, ProjectConnectionIT.java
  ✓ AC-007.6  ProjectConnectionIT.java
  ✓ AC-007.7, AC-007.8  ProjectServiceImplTest.java
trace project-integration (STORY-001, STORY-007): 30/30 acceptance criteria have a test
  ✓ AC-002.4   ProjectServiceImplTest.java
  ✓ AC-002.6   ProjectServiceImplTest.java
  ✓ AC-002.14  ProjectServiceImplTest.java
```

The trace in the main checkout gives the same 30/30.

## Issues

No CRITICAL findings.

Informational (does not block):

1. **MAJOR: the AC-001.17 test cannot fail.** Location: `backend/src/test/java/vn/tsolve/project/service/impl/ProjectServiceImplTest.java:413-504`,
   forbidden strings at `:497`. Source: reviewer, confirmed by Codex.
   - **Problem:**
     - The strings `svc@acme.atlassian.test`, `ATATT-planted-token` and `{"errorMessages"` are never given to any
       collaborator, so no change to Project code could make them appear.
     - The test checks selected exception properties (`code`, `params`, `field`), not the rendered HTTP errors.
     - It captures only the `vn.tsolve.project` logger, so it misses `GlobalExceptionHandler`'s logs.
   - **Why this does not block:** there is no production leak. Project code has no log statement, and
     `TrackerProjectLookup` carries no credential or body. Tracker AC-013.12 and AC-013.13 are tested with planted
     values. Design row 35 (Critical) still relies on this test for the Project side.
   - **Fix:** add one composition test that plants the values where they really enter:
     - real `TrackerProjectServiceImpl` + `JiraClient` against WireMock
     - a service account with the planted email and token
     - Jira 401/404/500 bodies holding the planted JSON
     - `ProjectServiceImpl` with a mocked `ProjectLinkRepository`
     - responses rendered by a standalone MockMvc with `GlobalExceptionHandler`, root logger captured at ALL with
       throwables

     Then assert that no response body or log line contains the planted values. A plain test with no Spring context
     keeps the single shared IT context (D43).
2. **MINOR: the AC-002.14 test only covers the lookup.** Location: `ProjectServiceImplTest.java:114`. Source:
   reviewer.
   - **Problem:** `findsTheActiveProjectOfAJiraKey_AC_002_14` is the old mock test of the lookup, renamed as Task 2
     asks. The AC's trigger, "when a new Project commits", is not exercised yet. The trace already shows AC-002.14
     as covered, so Task 3 could miss it.
   - **Fix:** in Task 3, the create IT should call `findActivePublicIdByJiraProjectKey(<canonical key>)` after the
     201 and assert it returns the response's `projectId`. Name that test `_AC_002_14`.
3. **MINOR: two invalid-body tests are labelled AC-007.3.** Location:
   `backend/src/test/java/vn/tsolve/project/controller/ProjectControllerTest.java:147,154`. Source: Codex,
   confirmed.
   - **Problem:** `validatesEmployeeBodyBeforeMethodAccess_AC_001_2_AC_007_3` and
     `validatesAdminBodyBeforeMethodAccess_AC_001_2_AC_007_3` send `{}` and expect 400. AC-007.3 is the valid-body
     → 403 rule, which `refusesEmployeeWithValidBody_AC_007_3` and `refusesAdminWithValidBody_AC_007_3` cover.
   - **Fix:** drop `_AC_007_3` from the two names and keep `_AC_001_2`. The order they prove (Bean Validation before
     `@PreAuthorize`) is an Interface Contracts rule (row 25), not AC-007.3.

Checked and found sound (no finding):
- **Transactions:** no transaction is open around Jira. There is no class-level `@Transactional`, and `open-in-view`
  is false (AGENTS §4). The IT double asserts `isActualTransactionActive()` is false on every call.
- **SQL:** the JDBC reads are parameterized. The `project_membership.project_id` → `project.public_id` join matches
  changeset 017. `uq_membership_one_manager` keeps the LEFT JOIN to one row per Project.
- **Error mapping:**
  - MSG48 and MSG49 go through `BusinessRuleException` (409); MSG49 has field `jiraProjectKey`.
  - MSG08 and the 503 MSG06 use `MessageCodeException` subclasses in `project/exception`, which have no handler of
    their own.
  - Unbound Workspace becomes 500 MSG06 through the catch-all, which logs only the exception class at ERROR.
- **Spotless:** clean.

## Assumptions made

1. AC-001.17 counts as covered through composition: the tracker tests plant credentials and Jira bodies, and the
   Project code passes on only the lookup's key and statuses. The Project-level test named AC-001.17 cannot fail
   on its own (Issue 1). I did not count it as MISSING. If you want each AC's own test to be able to fail, the
   verdict should be fail until Issue 1 is fixed. **Open judgment call for the user.**
2. AC-001.5–AC-001.7 come before AC-001.21: when the requested key is itself refused, the 409 names the requested
   key's Department and Jira is not called. AC-001.21 (the canonical key's Department) applies only when the
   requested key's own Project is an eligible reconnect. This follows "local conflicts make no Jira call" in the
   status table.
3. AC-002.4 and AC-002.6 are accepted at DTO level (Bean Validation plus the shared handler's `NotEmpty`/`NotNull`
   → MSG01 mapping), as Task 2 plans. The endpoint assertions belong to Task 3.
4. Normalization follows the spec literally: `String.strip()` then `toUpperCase(Locale.ROOT)`. So `ß` becomes `SS`,
   and a 20-unit key can become longer after normalization. Only the length as received is limited. I did not
   treat this as a defect.

## Test Report

Isolated worktree at `6d71a20d`, Git Bash, IntelliJ JBR,
`mvn -B -Dapi.version=1.44 -Dtest='Project*Test' -Dit.test='ProjectConnectionIT' … verify`. Log:
`C:/Users/admin/AppData/Local/Temp/claude/53e9b9e089520e92/64463b39-cd5d-4751-a518-aad974551625/scratchpad/kan127-t2-review-mvn.log`.

```
Tests run: 26, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.controller.ProjectControllerTest
Tests run: 49, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.service.impl.ProjectServiceImplTest
Tests run: 75, Failures: 0, Errors: 0, Skipped: 0
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.project.ProjectConnectionIT
--- spotless:3.2.0:check (default) @ t-solve-backend ---
BUILD SUCCESS
```

The two `ERROR … Unhandled exception java.lang.IllegalStateException` lines in that log are the expected
unbound-Workspace cases (500 MSG06). They name only the exception class.

The full verify at this state, run by the dispatcher
(`C:/Users/admin/AppData/Local/Temp/claude/53e9b9e089520e92/64463b39-cd5d-4751-a518-aad974551625/scratchpad/kan127-t2-verify.log`),
gives `Tests run: 1503 … Failures: 0` (unit), `Tests run: 447 … Failures: 0` (IT) and `BUILD SUCCESS`.

Frontend, from the dispatcher's log
(`C:/Users/admin/AppData/Local/Temp/claude/53e9b9e089520e92/64463b39-cd5d-4751-a518-aad974551625/scratchpad/kan127-t2-fe.log`):
- `format:check`, `lint` (0 errors, 1 old warning in `TicketListFilters.tsx`), `typecheck` and `build` exit 0
- `test`: 86 files, 1499 tests passed

Generated client: I ran orval (`orval --config orval.config.ts`) in the worktree, using the main checkout's
installed modules through a junction that I removed before deleting the worktree. Afterwards `git status` and
`git diff` on `frontend/src/shared/api/generated` were empty: the committed output is exactly what the generator
produces. Log:
`C:/Users/admin/AppData/Local/Temp/claude/53e9b9e089520e92/64463b39-cd5d-4751-a518-aad974551625/scratchpad/kan127-t2-review-orval.log`.

Codex second opinion: prompt
`C:/Users/admin/AppData/Local/Temp/claude/53e9b9e089520e92/64463b39-cd5d-4751-a518-aad974551625/scratchpad/kan127-t2-review-prompt.md`,
output
`C:/Users/admin/AppData/Local/Temp/claude/53e9b9e089520e92/64463b39-cd5d-4751-a518-aad974551625/scratchpad/kan127-t2-review-codex.md`.
It found nothing on transactions, check order, link rules, prefill, the contract or the generated files. Its two
test findings are Issues 1 and 3.

The dispatcher asked for this review file only, so there is no separate test report file; the run results are in
this section.
