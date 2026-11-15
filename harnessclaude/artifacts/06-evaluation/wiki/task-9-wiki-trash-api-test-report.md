---
module: wiki
feature: wiki-trash-api
task: 9
attempt: 1
date: 2026-11-15
iteration: 1
---

# Test report: wiki — Task 9: wiki-trash-api

The dispatcher told the reviewer not to run `mvn`, `npm` or any test (memory is tight and Docker is off). The orchestrator runs the full suite.

| Suite | Result | Source |
|-------|--------|--------|
| `vn.tsolve.wiki.service.impl.WikiTrashServiceImplTest` | not run by reviewer | the commit message says unit tests pass |
| `vn.tsolve.wiki.controller.WikiTrashControllerTest` | not run by reviewer | the commit message says unit tests pass |
| `vn.tsolve.wiki.WikiTrashIT` | pending: the orchestrator's Docker run | — |
| `mvn spotless:check` | pending: part of `mvn verify` | the commit message says it passes |
| `npm run generate:api` leaves no diff | pending | — |

Traceability (`bun harnessclaude/hooks/sdd.ts trace wiki STORY-005`): 17/17 acceptance criteria have a test. The Task 9 ACs are AC-005.1–AC-005.12 and AC-005.17.

The review verdict (pass) depends on these suites and `mvn verify` being green.

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
