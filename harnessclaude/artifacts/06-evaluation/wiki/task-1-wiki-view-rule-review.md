---
module: wiki
feature: wiki-view-rule
task: 1
verdict: pass
attempt: 2
date: 2026-11-14
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 1: Wiki tables, view rule and retrieval ports

## Summary
This attempt 2 is reviewed against the approved spec version c47a3ede. In that version, AC-001.1 now grants View when one of the user's Departments (AC-001.2) is the Space's Department. Attempt 1 failed on two findings, and both are addressed.

- **Issue 1 (attempt 1) is fixed.** `AccessSql.VIEWABLE_SPACE` (`AccessRepository.java:74-95`) now matches `s.department_id IN (u.department_id UNION <Ticket Resolver MEMBER Project Departments>)`, character for character as in the updated master design.
  - The new IT `grantsProjectDepartmentViewWithoutPermissionAndRechecksEligibility_AC_001_1_AC_001_2_BUG_249` (`WikiAccessIT.java:209`) creates no permission row.
  - It asserts that View is denied without the role and granted with it. It checks the Space lookup and the document port, then removes the role, switches the membership from MEMBER to PROJECT_MANAGER, and deletes the Department.
  - Each of those assertions would fail if the new path were missing or too broad.
- **Issue 2 (attempt 1), the IT run, is still pending.** The orchestrator's Docker-backed `mvn verify` is running. This verdict is **pass on the condition that `WikiAccessIT` and the whole `mvn verify` (including `spotless:check`) are green**. If either is red, the verdict becomes fail, with the failing test as the finding.
- **Minor issues from attempt 1:**
  - Fixed database name: fixed (`WikiAccessIT.java:64`, random suffix).
  - `ViewableSpaceLookup` Javadoc: fixed ("sorted by name").
  - `DEFAULT gen_random_uuid()` and the `WikiChunk` constructor: carried over with no change needed.

The attempt-1 checks still hold: no injection, fail-closed handling, Company-wide versus Department visibility, PENDING and FAILED left out of the ports only, the DDL matching the design, one bean per port, and no scope creep.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-001.1 to AC-001.11 implemented as written in spec c47a3ede |
| Completeness | pass | 11/11 ACs traced. The new grant path has its own IT, named with the AC and bug ids |
| Correctness | pass (conditional) | Unit tests are green (attempt 1). `WikiAccessIT` is waiting for the orchestrator's Docker run |
| Integration | pass | `/me/spaces` (`ViewableSpaceLookup`) now agrees with identity's `canAccessSpace` on Ticket Resolver Project Departments (W11) |

## Traceability
```
trace wiki (STORY-001): 11/11 acceptance criteria have a test
```
AC-001.1 and AC-001.2 also have `WikiAccessIT.grantsProjectDepartmentViewWithoutPermissionAndRechecksEligibility_AC_001_1_AC_001_2_BUG_249`.

## Issues
1. **Minor.** `docs/bug-log.md:227-230`: BUG-249 to BUG-252 leave **Owner** blank ("Owner to be confirmed"). The log's own step 3 says to state whose bug it is. Fix: fill in Owner, for example the KAN-170 assignee for BUG-249 to BUG-251.
2. **Minor.** `docs/bug-log.md:230`: BUG-252 (Docker-backed `*Test` classes in identity and security) has nothing to do with wiki. Logging it is right ("log every bug"). Committing it inside the wiki feature commit mixes in an unrelated change. Fix: put that row in the harness or docs commit, or in its own commit.

## Assumptions made
- **A1 (resolved).** The user chose the reading of Functional requirement 2 and identity AC-011.5. The spec was changed and approved (Changelog row 2026-11-14, c47a3ede).
- **A2.** `mvn` was not run, per the dispatcher's instruction. The pass depends on the orchestrator's `mvn verify` result.
- **A3.** The DDL tests tagged AC_001_1 count as supporting evidence only (unchanged from attempt 1).
- **A4.** The feature status was not changed. When `mvn verify` is green, the caller runs `bun harnessclaude/hooks/sdd.ts feature wiki wiki-view-rule passing`. If it is red, `fixing`.

## Test Report
See `task-1-wiki-view-rule-test-report.md`.
