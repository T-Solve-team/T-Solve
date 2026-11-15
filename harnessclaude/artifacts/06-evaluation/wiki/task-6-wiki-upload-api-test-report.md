---
module: wiki
feature: wiki-upload-api
task: 6
attempt: 1
date: 2026-11-15
iteration: 1
---

# Test report: wiki — Task 6: wiki-upload-api (commit acf89c50)

The dispatcher told the reviewer not to run `mvn` or `npm` (memory is tight, Docker is off). No test was executed by the reviewer; the tests were read from the commit.

| Suite | Result | Source |
|-------|--------|--------|
| `TextExtractorTest` (7 tests) | not run by reviewer | commit message: unit tests pass (implementer) |
| `WikiUploadServiceImplTest` (16 tests) | not run by reviewer | same |
| `WikiUploadControllerTest` (8 tests) | not run by reviewer | same |
| `WikiDocumentControllerTest` (constructor change) | not run by reviewer | same |
| `WikiUploadIT` (7 tests, PostgreSQL) | pending: orchestrator's Docker-backed `mvn verify` | — |
| `mvn spotless:check` | pending: part of the same `mvn verify` | — |
| Frontend `generate:api` no diff, `typecheck`, `build` | pending: orchestrator | — |

Traceability (`bun harnessclaude/hooks/sdd.ts trace wiki STORY-004`): 23/23 acceptance criteria have a test; Task 6's AC-004.1 to AC-004.13, AC-004.17 and AC-002.12 all named in test methods.

The review verdict (pass) depends on `WikiUploadIT` and the whole `mvn verify` being green.

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
