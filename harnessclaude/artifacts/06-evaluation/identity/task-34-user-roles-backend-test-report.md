# Test Report: identity — Task 34: user-roles-backend

**Date:** 2026-11-08
**Commands:** `mvn -o verify -Dapi.version=1.44 -Dspotless.check.skip=true -Dtest='UserRoleUpdateServiceImplTest,UserDetailControllerTest' -Dit.test='UserRoleUpdateIT,UserDetailIT,MockSignedInCallerIT,ReviewStorageIT,ProjectMembershipReviewAccessIT'` (DOCKER_API_VERSION=1.44; Spotless skipped because of BUG-130). The full verify was not re-run: the card gives the developer's result (887 unit, 296/297 IT, the one failure since fixed) and the code did not change after it (the later merge only brought harness files).

## Results
- Build exit 0; 0 failures, 0 errors, 0 skipped in the selected files.
- UserRoleUpdateServiceImplTest 46 · UserDetailControllerTest 18 · UserRoleUpdateIT 13 · UserDetailIT 3 · MockSignedInCallerIT 6 · ReviewStorageIT 4 · ProjectMembershipReviewAccessIT 3.
- Compile: pass. Lint (Spotless): not run (BUG-130).

## Coverage
Not measured; the spec sets no threshold.

## Acceptance Criteria → Tests
`sdd.ts trace identity STORY-007`: AC-007.8 to AC-007.23 all covered (see the review).

## Failures
None. The two ERROR lines in the log come from the rollback tests and are expected.

## Notes
`MockSignedInCallerIT` took 27 s (containers).
