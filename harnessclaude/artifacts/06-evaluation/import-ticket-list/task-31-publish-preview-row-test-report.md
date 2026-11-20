# Test Report: import-ticket-list — Task 31: publish-preview-row

**Date:** 2026-11-19. Branch `feat/KAN-373-import-publish-row`, HEAD `2f785147`. Code commit `80cece71` (range
`58e57127..80cece71`). No uncommitted change under `backend/` or `frontend/src` (only `docs/diagrams/` is untracked).

**Commands** (Git Bash, in `backend/`, after
`export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.1.1/jbr"; export PATH="$JAVA_HOME/bin:$PATH"`):
1. `mvn -B test -Dtest='vn.tsolve.ticket.**.*Test'`
2. `mvn -B spotless:check`
3. `mvn -B -Dapi.version=1.44 -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='Import*IT,JiraFinishIT' verify`
4. In `frontend/` with Node 24: `npm run generate:api`, then `git status --short src/shared/api/generated`.
5. `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-017`

## Results
| Run | Result |
|-----|--------|
| Unit tests, `vn.tsolve.ticket.**.*Test` | 413 run, 0 failed, 0 errors, 0 skipped. `BUILD SUCCESS` |
| — `ImportConfirmServiceImplTest` | 33/33 (10 new STORY-017 tests) |
| — `ImportControllerTest` | 22/22 (unchanged; no case for the new endpoint) |
| `spotless:check` | pass |
| ITs `Import*IT, JiraFinishIT` | 41 run, 0 failed. `BUILD SUCCESS`: `ImportHistoryControllerIT` 13, `ImportDraftRepositoryIT` 6, `ImportConfirmIT` 12, `ImportHistoryDateRangeIT` 6, `JiraFinishIT` 4 |
| `generate:api` | no diff in `src/shared/api/generated` (see the task 32 test report for the frontend run) |
| Traceability | 10/10 STORY-017 ACs have a test. AC-017.1–AC-017.7 are in `ImportConfirmServiceImplTest` |

The ERROR line `TransactionSynchronization.afterCompletion threw exception … IllegalStateException: search down` in
the IT log is expected. `ImportConfirmIT` makes its listener fail on purpose (an existing AC-004.10 test).

## Coverage of the new code (by reading the tests)
| Path in `ImportConfirmServiceImpl.publishRow` / `publishOne` | Tested |
|---|---|
| Happy path, CSV (batch 1/0/0 Completed, Ticket fields trimmed, decision, `rows.delete`, MSG32, commit) | yes (unit) |
| Happy path, Jira source | yes (unit, source only) |
| Preview kept, no `deleteById` | yes (unit) |
| `TicketsPublishedEvent` published | yes (unit, mocked; after-commit **not** shown) |
| With-Solutions preview, non-Valid row -> MSG06 | yes (unit) |
| Taken Ticket ID found by query -> MSG33 409, no batch, row kept | yes (unit) |
| Unique violation at insert/commit -> MSG33 409 (lines 184-190) | **no** |
| Other save failure -> 500 | **no** |
| MSG40, then 403 | yes (unit) |
| Other user / missing row -> 404 | yes (unit) |
| MANUAL preview -> 404 | no |
| Preview deleted between the pre-check and the lock (concurrent Confirm) | no |
| Real DB: row deleted, counts, lock against Confirm | **no IT** |
| Audit record | no assertion |

## Known, not counted
`EmailOutboxIT.concurrentClaims` (BUG-298 / BUG-346) was not in this run's IT filter.

## Defects
None of the tests failed. The review lists the test gaps as issues 1, 2 and 4.
