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

## Run 2026-11-15 (evaluate attempt 2, HEAD 7e6155cd)
| Command | Result |
|---|---|
| `mvn verify` (JBR 21) | fail — unit: 2693 run, 1 failure, 215 errors; ITs not reached |
| `mvn verify -Dtest=none -Dmaven.test.failure.ignore=true` | ITs: 739 run, 542 errors; all `vn.tsolve.wiki.*IT` error before their assertions |
| Wiki unit suites (`vn.tsolve.wiki.**Test`, 16 classes) | all pass |
| `spotless:check` | pass |

Root causes: BUG-288 (no `FileStorage` bean when storage is not configured, from `WikiUploadServiceImpl` / `WikiTrashServiceImpl`) and BUG-287 (`commons-io` 2.16.1 resolved because of `poi-ooxml`). Wiki ITs stay unverified until BUG-288 is fixed.

## Run 2026-11-15 (after the BUG-287 / BUG-288 fix)
| Command | Result |
|---|---|
| `mvn verify` (JBR 21) | pass — unit 2694/2694, IT 877/877, `spotless:check` pass |
| Wiki ITs | WikiAccessIT 28, WikiBrowseIT 10, WikiFolderIT 16, WikiIndexIT 6, WikiPermissionIT 23, WikiSearchIT 7, WikiTrashIT 15, WikiUploadIT 7 — all pass |
| New test | `WikiUploadServiceImplTest.answersMsg06WithoutWritesWhenStorageIsNotConfigured_BUG_288` pass |
