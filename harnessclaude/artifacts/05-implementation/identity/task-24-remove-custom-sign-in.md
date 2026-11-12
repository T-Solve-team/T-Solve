# Task 24: remove the custom sign-in — KAN-295

**Module:** identity
**Feature:** remove-custom-sign-in
**Status:** in-progress
**Iteration:** 1

## Session 2026-11-08

### Hand-off
- done: custom sign-in removed (14 main classes, 13 tests, 3 orphan helpers), changesets 021 and 022 added, config, fixtures and tests trimmed, OpenAPI and sign-in.test.ts edited and regenerated; review fix round 1 applied (B1 SignInProperties reflow and typo, B2 MSG120 assertion, B3 filterReturnTo cases moved to SignInStartControllerFilterReturnToTest; BUG-165 row set to Fixed)
- open: re-review, fresh-clone spotless, commits, push, PR
- next: Task 25a (KAN-296)
- branch: refactor/KAN-295-identity-remove-custom-sign-in
- PR: none yet
- Verify: backend 762 unit + 267 IT green; spotless clean on a fresh clone (commit 01f033e1); frontend full suite green after re-running 10 files that timed out under load; re-review PASS (attempt 2)
