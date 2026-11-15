---
module: wiki
feature: wiki-permissions-api
task: 11
attempt: 1
date: 2026-11-15
iteration: 1
---

# Test report: wiki — Task 11: wiki-permissions-api

The dispatcher told the reviewer not to run `mvn` or any test (Docker is off), and the reviewer did not.

| Suite | Result | Source |
|-------|--------|--------|
| `WikiSpaceSettingsControllerTest` | not run by reviewer | the commit message says unit tests pass |
| `WikiPermissionServiceImplTest` | not run by reviewer | the commit message says unit tests pass |
| `WikiPermissionIT` | pending | the commit message says the ITs run after all wiki tasks |
| `mvn spotless:check` | not run by reviewer | the commit message says it passes |

Traceability: `sdd.ts trace wiki STORY-006` failed with ENOENT, because the working tree is on another branch. A manual `git grep` at `a20ae6c2` finds a test for each of AC-006.1–AC-006.10.

The review verdict (pass) depends on `mvn verify`, including `WikiPermissionIT`, being green.
