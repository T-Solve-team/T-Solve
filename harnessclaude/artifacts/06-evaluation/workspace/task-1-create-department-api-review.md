---
module: workspace
feature: create-department-api
task: 1
verdict: pass
attempt: 2
date: 2026-11-11
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 1: create-department-api

## Summary
Attempt 2 fixes the attempt 1 blocker. `CreateDepartmentRequest` now has a compact constructor that turns a name where `String.isBlank()` is true into `""`. `isBlank()` uses `Character.isWhitespace`, the same rule as `strip()` (D22). So `@NotBlank` now rejects every name that is blank under the spec's rule, at any length, with MSG01, before `@Size` runs. Non-blank names keep their length as received, so the MSG02 rule is unchanged.

The new controller cases pass through the real Jackson and validation path: `　`, ` ` and 51 × ` ` each get 400 MSG01. That proves Jackson runs the compact constructor. The fixes for issues 3, 4, 6 and 7 are in. Issues 2, 5 and 8 were left on purpose (D26), and I agree they do not block.

The workspace tests (26 unit, 9 IT), Spotless and the trace (16/16) pass. Codex found no blocking defect, and I verified both of its minor findings. All STORY-001 criteria pass.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-001.1–.16 pass (Stage 1 below). AC-001.5 and .6 now hold for Unicode whitespace. |
| Completeness | pass | Every criterion is implemented and has a named test. Nothing EXTRA was added. |
| Correctness | pass | Workspace unit 26/26 and IT 9/9 pass; Spotless passes. No CRITICAL finding. |
| Integration | pass | Unchanged since attempt 1: the contract, `DepartmentService.create`, `GET /api/v1/users/departments` and all four `DEPARTMENT_CHANGE` places match the spec. The new `DepartmentService` Javadoc tells in-process callers to validate and to check the Admin role themselves. |

### Stage 1 — spec compliance (changes since attempt 1)

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 5 | AC-001.5 | PASS | `CreateDepartmentRequest.java:7-13` turns a name blank by `isBlank()` into `""`. `rejectsMissingOrBlankName_AC_001_5_AC_001_6` covers `{}`, null, `""`, ASCII spaces, 51 ASCII spaces, `　` and ` `: each gets 400 MSG01 on `name`, and the service is not called |
| 6 | AC-001.6 | PASS | 51 × ` ` gets MSG01 (blank wins). `rejectsFiftyOneNonBlankCharacters_AC_001_6` gets MSG02 with `max_length` 50 |
| others | AC-001.1–.4, .7–.16 | PASS | Code and tests unchanged since attempt 1, apart from comments. `DepartmentCreationIT` 9/9 again |

### Attempt 1 issues

| # | Status in attempt 2 |
|---|--------------------|
| 1 CRITICAL Unicode whitespace | Fixed (`CreateDepartmentRequest.java:7-13`, three new controller cases) |
| 2 Space-failure rollback has a unit test only | Left (D26). Still informational |
| 3 `ON CONFLICT` with no target | Fixed: comment names the three constraints (`DepartmentRepository.java:19`) |
| 4 Return value of `currentWorkspaceId()` ignored | Fixed: comment (`DepartmentServiceImpl.java:31`) |
| 5 AC-001.16 test checks the filter option | Left (D26). Still informational |
| 6 Service contract for in-process callers | Fixed: Javadoc (`DepartmentService.java:7-11`) |
| 7 027 rollback | Fixed: the rollback constraint keeps `DEPARTMENT_CHANGE` (`027-…sql:11`). Rolling back no longer breaks the existing audit rows; the rollback is now forward-only in effect, which is acceptable for an append-only log. The 028 note stays, as accepted by design row 11 |
| 8 `lower()` depends on the database locale | Left (D26; belongs to tenant provisioning). Still informational |
| 9 Logged deviations (D15, `TenantScope` port) | Unchanged; acceptable |

### Security pass
No change to authorization, CSRF, tenant binding, SQL or the audit path since attempt 1. The constructor change narrows the input that can be accepted. No Critical or High finding.

## Traceability
```
  ✓ AC-001.1  DepartmentServiceImplTest.java, DepartmentControllerTest.java, DepartmentCreationIT.java
  ✓ AC-001.2  DepartmentCreationIT.java
  ✓ AC-001.3  DepartmentServiceImplTest.java, DepartmentCreationIT.java
  ✓ AC-001.4  DepartmentCreationIT.java
  ✓ AC-001.5  DepartmentControllerTest.java
  ✓ AC-001.6  DepartmentControllerTest.java
  ✓ AC-001.7  DepartmentServiceImplTest.java, DepartmentCreationIT.java
  ✓ AC-001.8  DepartmentCreationIT.java
  ✓ AC-001.9  DepartmentCreationIT.java
  ✓ AC-001.10  DepartmentCreationIT.java
  ✓ AC-001.11  DepartmentServiceImplTest.java, DepartmentControllerTest.java, DepartmentCreationIT.java
  ✓ AC-001.12  DepartmentControllerTest.java
  ✓ AC-001.13  DepartmentControllerTest.java
  ✓ AC-001.14  DepartmentCreationIT.java
  ✓ AC-001.15  DepartmentServiceImplTest.java, DepartmentCreationIT.java
  ✓ AC-001.16  frontend/src/features/workspace/pages/AuditActionLabel.test.tsx
trace workspace (STORY-001): 16/16 acceptance criteria have a test
```
(Backend paths are under `backend/src/test/java/vn/tsolve/workspace/`.)

## Issues

### CRITICAL (blocks pass)
None.

### Informational (does not block)

A. `backend/src/main/java/vn/tsolve/workspace/dto/CreateDepartmentRequest.java:6`: a name made only of control characters is stricter than the spec allows.
   - Under `strip()` such a name is not blank: `(char) 7` alone, or 51 of it, has `isBlank()` false. `@NotBlank`'s `trim()` still removes it, so it gets 400 MSG01. The spec rule would give 201 for the single character and MSG02 for 51.
   - I confirmed this with the validator directly (hibernate-validator 8.0.1). Codex found it.
   - Nothing is written, and rejecting a name of only control characters is the safer outcome.
   - Fix: none needed. Optionally state it in the spec (Change Mode) or replace `@NotBlank` with a check based on `strip()`.

B. `backend/src/test/java/vn/tsolve/workspace/controller/DepartmentControllerTest.java:94`: no test guards "longer than 50 characters as received" for a non-blank name with whitespace around it.
   - For example, `" " + "a".repeat(49) + " "` (51 as received, 49 after stripping) must get MSG02.
   - If someone later strips the name in the constructor, every current test still passes. Codex found it.
   - Fix: add that case to `rejectsFiftyOneNonBlankCharacters_AC_001_6`.

C. Process: D28 leaves the attempt 1 defect out of `docs/bug-log.md`, because it was found and fixed before the first commit. The project rule (root `CLAUDE.md` and `backend/AGENTS.md` §10) says to log every bug found, including in review. This is a logged choice with precedent (KAN-96 D21), and the review file keeps the trail. Listed as a judgment call below.

D. Carried from attempt 1 and left on purpose (D26): issue 2 (Space-failure rollback has a unit test only), issue 5 (AC-001.16 test checks the filter option, and the table uses the same map), issue 8 (`lower()` depends on the database locale), and issue 9 (logged deviations D15 and the `TenantScope` port).

### Codex second opinion (attempt 2)
Run: `codex exec -s read-only --ephemeral` on the six changed files. Its answers per question:
- **Jackson runs the compact constructor:** no defects. My test run confirms it: the `　`, ` ` and 51 × ` ` cases get MSG01.
- **Constructor effects (null, mixed content, in-process callers, `equals`):** no defects.
- **027 rollback:** no defects.
- **Kept:** Codex 1 (control characters) as informational A, after verifying it. Codex 5 (no test of length as received with surrounding whitespace) as informational B.
- **Rejected:** none.

## Assumptions made
- **Judgment call:** D28 does not log the attempt 1 defect in `docs/bug-log.md`. I did not fail the task for this, because no criterion covers it. If the project rule "log every bug" should apply to defects found in review before the first commit, the developer adds the row and names the three new controller cases with its id.
- **Judgment call:** a name made only of control characters gets MSG01 (A). I judged this acceptable because nothing is written. The user may want the spec to say so.
- As in attempt 1: AC-001.12 is tested with a user who holds no role. Uniqueness is judged on a UTF-8-locale PostgreSQL. Coverage is not measured (no tool, no threshold).
- I re-ran only the workspace tests, Spotless and the trace, as the coordinator asked. The main session runs the full backend and frontend suites afterwards; attempt 1's full run is in the test report.

## Test Report
`harnessclaude/artifacts/06-evaluation/workspace/task-1-create-department-api-test-report.md`

| Check (attempt 2) | Result |
|-------|--------|
| `mvn -B -Dapi.version=1.44 -Dtest='vn.tsolve.workspace.**' -Dit.test=DepartmentCreationIT … verify` | BUILD SUCCESS. Surefire 26/26 (`DepartmentControllerTest` 13, `DepartmentServiceImplTest` 4, `DepartmentCreationIT` 9, matched by the package pattern); Failsafe `DepartmentCreationIT` 9/9; Spotless pass |
| `sdd.ts trace workspace STORY-001` | 16/16 |
| Full backend and frontend suites | Not re-run in attempt 2 (main session runs them). Attempt 1: 1256 unit + 430 IT, frontend 1492/1492, all checks pass |

---

## Attempt history

### Attempt 1 — verdict: fail (2026-11-11)

**Summary.** The endpoint, transaction, race-safe insert, audit entry, tenant binding, changesets and contract were sound. Every check passed: backend 1256 unit and 430 IT tests, Spotless, all frontend checks, and `generate:api` left no diff. The trace found a test for 16 of 16 criteria. The task failed on one blocking defect: a name made only of Unicode whitespace passed `@NotBlank`, which uses `trim()`, and the service stripped it to `""`. That stored a Department, a Space and an audit entry with an empty name, breaking AC-001.5. It also broke AC-001.6: 51 such characters got MSG02 instead of MSG01. Codex found the same defect on its own.

**Criteria.** Functionality: fail (AC-001.5 and .6). Completeness: pass. Correctness: fail (issue 1 CRITICAL). Integration: pass.

**Issues (attempt 1).**

1. **CRITICAL.** `CreateDepartmentRequest.java:6` with `DepartmentServiceImpl.java:32`: Hibernate Validator 8.0.1's `@NotBlank` checks `trim().length() > 0`, which removes only code points up to U+0020. The service uses `strip()` (`Character.isWhitespace`, D22).
   - `POST {"name":"　"}` returned 201 and wrote `department.name = ''`, `space.name = ''` and an audit entry.
   - `POST {"name": 51 × " "}` returned MSG02, not MSG01.
   - Suggested fix: apply the spec's blank rule before the length rule. Add `　`, ` ` and 51 × ` ` cases.
2. Informational. `DepartmentServiceImplTest.java:83`: only a mock test covered the Space-failure branch of AC-001.11.
3. Informational. `DepartmentRepository.java:24`: `ON CONFLICT DO NOTHING` with no target turns any unique violation into MSG43. Suggested: a comment naming the constraints.
4. Informational. `DepartmentServiceImpl.java:31`: the result of `currentWorkspaceId()` was thrown away. Suggested: a comment.
5. Informational. `AuditActionLabel.test.tsx:12`: the test checks the filter option, not a table row. The table uses the same `ACTION_LABELS` map (`AuditLogPage.tsx:23`).
6. Informational. `DepartmentService.java:7`: the interface checks neither the name nor the role for in-process callers.
7. Informational. 028 stops Liquibase on existing case-variant names (accepted by design row 11). 027's rollback failed once `DEPARTMENT_CHANGE` rows existed.
8. Informational. `lower()` folds non-ASCII letters only under a UTF-8 `LC_CTYPE`.
9. Informational. Logged deviations: D15 (JDBC repository) and the `identity.signin.port.TenantScope` import, which the spec sanctions.

**Codex (attempt 1).**
- Kept: Codex 1 (Unicode whitespace) and Codex 4 (Space-failure rollback).
- Downgraded to informational: Codex 2 (028 on existing data; design row 11), Codex 3 (027 rollback; developer tool, no criterion) and Codex 5 (the AC-001.16 table uses the same map).
- Rejected: none.

**Assumptions (attempt 1).**
- Judged AC-001.5 and .6 by the spec's own `strip()` definition, and treated the defect as blocking.
- Took a user with no role as "without the Admin role" for AC-001.12.
- Judged uniqueness on a UTF-8-locale PostgreSQL.
- Did not measure coverage.
