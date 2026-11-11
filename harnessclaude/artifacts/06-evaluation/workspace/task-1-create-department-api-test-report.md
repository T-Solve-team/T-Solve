# Test Report: workspace — Task 1: create-department-api

## Attempt 2 (2026-11-11)

**State tested:** the same branch, working tree after the developer's fix for attempt 1 issues 1, 3, 4, 6 and 7.
**Command (from `backend/`):** `mvn -B -Dapi.version=1.44 "-Dtest=vn.tsolve.workspace.**" "-Dit.test=DepartmentCreationIT" -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false verify`. Scope set by the coordinator; the main session runs the full suites afterwards.

| Check | Result |
|-------|--------|
| Surefire (unit) | 26 run · 0 failures: `DepartmentControllerTest` 13 (8 parameterized blank cases, now including `　`, ` ` and 51 × ` `), `DepartmentServiceImplTest` 4, `DepartmentCreationIT` 9 (the package pattern also matches the IT) |
| Failsafe (IT) | `DepartmentCreationIT` 9/9 |
| Spotless check | pass · BUILD SUCCESS |
| `sdd.ts trace workspace STORY-001` | 16/16 |
| Extra check | `NotBlankValidator` with `(char) 7` (1× and 51×): invalid. `isBlank()` false, `strip()` length 1/51. Such a name gets MSG01, stricter than the spec's `strip()` rule (review informational A) |
| Not re-run | full backend suite, frontend checks, `generate:api` (out of the coordinator's scope for attempt 2) |

Defects: the attempt 1 Unicode-whitespace defect is fixed. It is not logged in `docs/bug-log.md` (decision D28; see the review's judgment call).

---

## Attempt 1
**Date:** 2026-11-11
**State tested:** branch `feat/KAN-120-workspace-create-department`, base `d524aece`, change uncommitted (working tree + untracked files; `docs/diagrams/`, `RESEARCH-003`, `schema-ddl-alignment.md` excluded as not part of the change)
**Iteration:** 1 · **Attempt:** 1 · **Reviewer:** harnessclaude-reviewer (independent run)

## Commands

- Backend, from `backend/` (Git Bash, `JAVA_HOME` = IntelliJ JBR 21.0.6): `mvn -B -Dapi.version=1.44 verify`
- Frontend, from `frontend/` (Node v24.22.2): `npm run format:check`, `lint`, `typecheck`, `test`, `build`, `generate:api`; SHA-1 of every file in `src/shared/api/generated` before and after `generate:api`, compared
- Repo root: `bun harnessclaude/hooks/sdd.ts trace workspace STORY-001`
- Extra check: `NotBlankValidator.isValid` (hibernate-validator 8.0.1.Final, the version Spring Boot 3.3.5 uses) against `String.strip()` for U+3000, U+2003, U+0020 and U+00A0 (scratch program, not committed)

## Results

| Check | Result |
|-------|--------|
| Backend unit (Surefire) | 1256 run · 0 failures · 0 errors · 0 skipped |
| Backend IT (Failsafe, Testcontainers) | 430 run · 0 failures · 0 errors · 0 skipped |
| Spotless check | pass (BUILD SUCCESS) |
| `DepartmentServiceImplTest` | 4/4 |
| `DepartmentControllerTest` | 10/10 (5 parameterized blank cases) |
| `DepartmentCreationIT` | 9/9 |
| `UserListIT` (fixture changed) | 18/18 |
| Frontend `format:check` | exit 0 |
| Frontend `lint` | exit 0 (1 warning, `features/tickets/components/TicketListFilters.tsx:23`, not in this change) |
| Frontend `typecheck` | exit 0 |
| Frontend `test` | 86 files · 1492/1492 passed |
| Frontend `build` | exit 0 |
| `generate:api` | exit 0, no diff in `src/shared/api/generated` |

BUG-197 (`ReviewQueueEventsIT` flake) did not show in this run.

## Extra check: `@NotBlank` against `strip()`

| Input | `@NotBlank` valid | `strip()` result |
|-------|-------------------|------------------|
| `"　"` (ideographic space) | true | `""` |
| `" "` (em space) | true | `""` |
| `"   "` | false | `""` |
| `" "` (no-break space) | true | `" "` |

Hibernate's `@NotBlank` uses `trim()` (code points up to U+0020 only). The spec defines whitespace as what `String.strip()` removes (D22). A name made only of U+2003 or U+3000 passes validation, and the service stores it as `""`. See review issue 1.

## Coverage

The backend has no coverage tool set up, and the spec sets no threshold, so coverage was not measured. Every STORY-001 criterion has at least one named test (trace below).

## Acceptance Criteria → Tests

`sdd.ts trace workspace STORY-001` → `16/16 acceptance criteria have a test`.

| AC | Tests |
|----|-------|
| AC-001.1 | `DepartmentServiceImplTest.stripsNameCreatesSpaceAndRecordsCreation_AC_001_1_AC_001_3`, `DepartmentControllerTest.createsWithFiftyCharacterName_AC_001_1`, `DepartmentCreationIT.createsActiveDepartmentWithSpaceAndAudit_AC_001_1_AC_001_2_AC_001_3` |
| AC-001.2 | `DepartmentCreationIT.createsActiveDepartmentWithSpaceAndAudit_…` |
| AC-001.3 | the unit test and the IT above |
| AC-001.4 | `DepartmentCreationIT.appearsInDepartmentList_AC_001_4` |
| AC-001.5 | `DepartmentControllerTest.rejectsMissingOrBlankName_AC_001_5` (5 ASCII cases; no Unicode-whitespace case) |
| AC-001.6 | `DepartmentControllerTest.rejectsFiftyOneNonBlankCharacters_AC_001_6` |
| AC-001.7 | `DepartmentServiceImplTest.refusesDuplicateWithoutSpaceOrAudit_AC_001_7`, `DepartmentCreationIT.refusesDuplicateOfActiveName_AC_001_7` |
| AC-001.8 | `DepartmentCreationIT.refusesNameOfDeletedDepartment_AC_001_8` |
| AC-001.9 | `DepartmentCreationIT.refusesCaseVariantNames_AC_001_9` (ASCII and `Phòng IT`) |
| AC-001.10 | `DepartmentCreationIT.concurrentRequestsCreateOneDepartment_AC_001_10` |
| AC-001.11 | `DepartmentServiceImplTest.propagatesSpaceFailureWithoutAudit_AC_001_11`, `DepartmentControllerTest.rendersUnexpectedFailure_AC_001_11`, `DepartmentCreationIT.rollsBackWhenAuditFails_AC_001_11` |
| AC-001.12 | `DepartmentControllerTest.rejectsNonAdmin_AC_001_12` |
| AC-001.13 | `DepartmentControllerTest.rejectsAnonymous_AC_001_13` |
| AC-001.14 | `DepartmentCreationIT.allowsNameHeldInOtherWorkspace_AC_001_14` |
| AC-001.15 | `DepartmentServiceImplTest.failsBeforeWritesWithoutWorkspace_AC_001_15`, `DepartmentCreationIT.answersMsg06WithoutBoundWorkspace_AC_001_15` |
| AC-001.16 | `frontend/src/features/workspace/pages/AuditActionLabel.test.tsx` "AC-001.16 labels the DEPARTMENT_CHANGE action…" |

## Defects

| Bug | Severity | Status |
|-----|----------|--------|
| (new, not yet in `docs/bug-log.md`) Name made only of Unicode whitespace (U+2003, U+3000) passes `@NotBlank`, and an empty-named Department, Space and audit entry are written (AC-001.5); 51 such characters get MSG02, not MSG01 (AC-001.6) | High | Open — review issue 1. The developer logs it in `docs/bug-log.md` with the fix (the reviewer writes only evaluation files) |
| BUG-200 (audit-log mock actions shift when `AuditAction` grows) | Medium | Fixed in this change; `handlers.test.ts` "BUG-200 …" passes |
