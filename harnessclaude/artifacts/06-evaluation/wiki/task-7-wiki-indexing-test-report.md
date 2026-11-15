---
module: wiki
feature: wiki-indexing
task: 7
attempt: 1
date: 2026-11-15
iteration: 1
---

# Test report: wiki — Task 7: wiki-indexing (commit a54e8b47)

The dispatcher told the reviewer not to run `mvn` (memory is tight, Docker is off). No test was executed by the reviewer; the tests were read from the commit.

| Suite | Result | Source |
|-------|--------|--------|
| `PassageSplitterTest` (5 tests) | not run by reviewer | commit message: unit tests pass (implementer) |
| `WikiIndexJobHandlerTest` (9 tests incl. parameterized) | not run by reviewer | same |
| `WikiIndexIT` (5 tests, PostgreSQL + pgvector) | pending: orchestrator's Docker-backed `mvn verify` | — |
| `mvn spotless:check` | pending: part of the same `mvn verify` | — |

Traceability (`bun harnessclaude/hooks/sdd.ts trace wiki STORY-004`): AC-004.14, AC-004.15, AC-004.16 each have unit and IT tests.

The review verdict (pass) depends on `WikiIndexIT` and the whole `mvn verify` being green.

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
