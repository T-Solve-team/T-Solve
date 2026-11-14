# Test report: project-integration — Task 1: project-members-api

- Date: 2026-11-13 · attempt 1 · iteration 1
- Code: backend `9736652..782e23f`. `git diff 782e23f..HEAD -- backend` is empty, so HEAD `f9e60c9` has the same backend.
- JDK: IntelliJ JBR 21

## Full suite (orchestrator evidence, same backend code)
`mvn clean verify` in `backend/`: exit 0.

| Suite | Tests | Failures | Errors | Skipped |
|-------|-------|----------|--------|---------|
| Unit (surefire) | 1473 | 0 | 0 | 0 |
| Integration (failsafe, Testcontainers) | 495 | 0 | 0 | 0 |
| `spotless:check` | included in verify | pass | | |

I did not re-run the full suite here: the machine runs out of memory, as the dispatcher warned.

## Feature tests (read from the report XMLs)
| Class | Tests | Failures | Errors | Skipped |
|-------|-------|----------|--------|---------|
| `vn.tsolve.project.ProjectMembersIT` (`target/failsafe-reports`) | 17 | 0 | 0 | 0 |
| `vn.tsolve.project.service.impl.ProjectMemberServiceImplTest` (`target/surefire-reports`) | 10 | 0 | 0 | 0 |

## Re-run by the reviewer
`mvn -q test -Dtest=ProjectMemberServiceImplTest`: exit 0, no failures.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace project-integration STORY-001 STORY-002`: 23/23 criteria have a test, including all 12 Task 1 criteria (AC-001.1–001.4, AC-002.1–002.8). The command exits 1 only because of the warning about ids from other modules (AC-003.1, AC-003.8) that are in tests but not in this spec.

## Edge-case coverage (design E1–E11)
| Edge case | Test |
|-----------|------|
| E1 | `refusesMemberOfTheProject_AC_001_3` |
| E2 | `refusesManagerOfAnotherProjectInSameDepartment_AC_001_3` |
| E3 | `refusesDeactivatedManagerOrManagerWithoutRole_AC_001_3`, plus the unit test `refusesCallerTheManagerQueryRejects_AC_001_3` |
| E4 | `rejectsMissingEmptyTooLongDuplicateOrNullIds_AC_002_6` |
| E5 | `mapsConcurrentDuplicateInsertToConflictWithoutAudit_AC_002_7` (unit test, mocked exception only) |
| E6, E7 | `refusesWholeRequestWhenAnyIdCannotBeAdded_AC_002_7`, `searchesActiveResolversNotYetMembersByNameOrEmail_AC_002_1` |
| E8 | `matchesPercentAndUnderscoreLiterally_AC_002_1` |
| E9 | `refusesAddToRemovedProject_AC_002_8` |
| E10 | no special case (departments are soft-deleted) |
| E11 | `answersNotFoundForUnknownProject_AC_001_4` (unknown id gives 404, malformed id gives 400) |

Tenant isolation: a Tenant B resolver is never a candidate and gets 409 on POST.

## Coverage
No coverage tool was run for this review. Coverage is judged from the ACs and edge cases above.
