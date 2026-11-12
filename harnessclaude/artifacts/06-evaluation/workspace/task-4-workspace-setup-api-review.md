---
module: workspace
feature: workspace-setup-api
task: 4
verdict: pass
attempt: 1
date: 2026-11-12
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 4: Setup and read API, status lookup, OpenAPI

## Summary
Commit `default@b1b2b30` (STORY-001, STORY-002). Every AC of both stories is implemented and has a test that checks
the behaviour rather than just naming it (unit + MockMvc slice + Testcontainers IT with two tenant databases,
including a real two-thread race for AC-001.11). Token handling is clean: request DTOs redact `toString()`, the
response record has only the three public fields, logs and audit carry no token, and a 500 never echoes an exception
message. One IMPORTANT convention finding (Jira call inside the setup transaction); no CRITICAL finding. Verdict
pass.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Setup order matches AC-001.2..11: fields → email → site rule → 409 → Jira check → link → persist+flush → audit (`WorkspaceServiceImpl.java:75-126`). GET and `isReady()` read the bound Workspace's row (`:130-134`, `WorkspaceStatusLookupImpl.java:21-23`). |
| Completeness | pass | 17/17 ACs PASS (table below). EXTRA: malformed / non-object body → 400 `MSG03` with no field (not in the spec, harmless, tested). |
| Correctness | pass | Recorded verify: `mvn -B verify` BUILD SUCCESS (1534 unit + 454 IT); frontend checks green. Not re-run by the reviewer (dispatcher's low-RAM limit). No CRITICAL finding. |
| Integration | pass | Endpoints, schemas and `WorkspaceStatusLookup.isReady()` match the spec's Interface Contracts; OpenAPI YAML + regenerated client committed, `generate:api` reported no diff. `/me` wiring belongs to be-foundation (out of scope). |

### Stage 1 — spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 1 | AC-001.1 | PASS | `WorkspaceServiceImpl.java:107-125`; IT "AC-001.1 returns 201 and stores encrypted setup only in the bound tenant" decrypts the stored value with the ACME id |
| 2 | AC-001.2 | PASS | `field()` `:184-194`; unit (missing/null/blank/non-string) + IT |
| 3 | AC-001.3 | PASS | `:190-192` raw length, `MSG02` + `max_length`; unit at and over limits |
| 4 | AC-001.4 | PASS | `:84-86`; unit verifies no Jira call |
| 5 | AC-001.5 | PASS | `:87` before `count()` and any call; unit verifies no interactions |
| 6 | AC-001.6 | PASS | `:93` before link/persist; unit `inOrder` |
| 7 | AC-001.7 | PASS | `:94-97`; unit 401/403/500/timeout, no link/insert; IT |
| 8 | AC-001.8 | PASS | `@Transactional` on `setup`, link `:99` then persist `:112`; IT "AC-001.9 refused link rolls back" proves no row |
| 9 | AC-001.9 | PASS | `:101-103`; unit + IT (MSG153, `tracker_name`, field `jiraSiteUrl`) |
| 10 | AC-001.10 | PASS | `:104-105`; unit checks the log line has no token and no throwable |
| 11 | AC-001.11 | PASS | `:89-91` + `isConcurrentSetup` `:196-206`; IT race with `CyclicBarrier` → 201 + 409, one row, one audit; unrelated integrity error not mislabelled |
| 12 | AC-001.12 | PASS | `:120-124`; unit + IT check details and absence of token/ciphertext |
| 13 | AC-001.13 | PASS | `@PreAuthorize` `WorkspaceController.java:46`; slice + IT (no side effects) |
| 14 | AC-002.1 | PASS | `WorkspaceStatusLookupImpl.java:21-23`; unit + IT (false before setup, tenant-isolated after) |
| 15 | AC-002.2 | PASS | `WorkspaceInfoResponse` 3 fields; slice + IT assert exactly three fields |
| 16 | AC-002.3 | PASS | `:133`; unit, slice, IT |
| 17 | AC-002.4 | PASS | `WorkspaceController.java:61`; slice + IT |

## Traceability
```
$ bun harnessclaude/hooks/sdd.ts trace workspace STORY-001 STORY-002
  ✓ AC-001.1 … ✓ AC-001.13, ✓ AC-002.1 … ✓ AC-002.4
trace workspace (STORY-001, STORY-002): 17/17 acceptance criteria have a test
```
(Files: `WorkspaceServiceImplTest`, `WorkspaceControllerTest`, `WorkspaceControllerIT`, `WorkspaceOpenApiTest`,
`WorkspaceStatusLookupImplTest`, plus Task 1's `WorkspaceInfoRepositoryIT` / `WorkspaceChangelogTest`.)

## Issues

CRITICAL: none.

IMPORTANT (does not block):
1. `backend/src/main/java/vn/tsolve/workspace/service/impl/WorkspaceServiceImpl.java:74,93` — `setup` is
   `@Transactional` and calls `tracker.checkServiceAccount` (two Jira calls, up to 5 s + 10 s each) inside the
   transaction, against `backend/AGENTS.md` §4 "No outbound HTTP call … inside a `@Transactional` method". A DB
   connection is held for the whole Jira round-trip. The link call at `:99` must stay inside (AC-001.8 / D13 require
   it; the spec wins over the convention there), but the account check need not.
   Fix: do fields → site rule → `count()` → `checkServiceAccount` outside the transaction, then link + persist +
   audit in a `TransactionTemplate` (as `update` already does); keep the singleton constraint as the race guard.

MINOR:
2. `WorkspaceController.java:51-54`, `dto/WorkspaceSetupRequest.java:34` — the body is taken as a `String` and
   validated by hand instead of a `@Valid` record with Bean Validation (`backend/AGENTS.md` §3). Justified in the
   Javadoc (ordered MSG codes); acceptable, but note the deviation in the design if it stays.
3. `dto/WorkspaceInfoMapper.java` — MapStruct mapper lives in `dto/`; the convention puts mappers in `mapper/`.
4. `WorkspaceServiceImpl.java:79-81` — length is checked on the raw value and then `strip()`ped, so
   `"  " + 99 chars` passes; harmless (DB column is 150), noted only.

## Assumptions made
- The Jira-check-in-transaction convention breach is IMPORTANT, not CRITICAL, because the behaviour is correct and
  the link-in-transaction part is spec-mandated (AC-001.8). The user may raise it.
- Tests were not re-run by the reviewer: the dispatcher's brief forbids `mvn` on this low-RAM machine; the verify
  result recorded by the orchestrator in `artifacts/05-implementation/workspace/task-4-workspace-setup-api.md` is
  used.

## Test Report
See `task-4-workspace-setup-api-test-report.md`. Recorded: `mvn -B verify` BUILD SUCCESS — 1534 unit + 454 IT
(WorkspaceServiceImplTest 41, WorkspaceControllerIT 15, WorkspaceOpenApiTest 2); frontend format/lint/typecheck/
test (1499)/build ok, `generate:api` no diff. Trace 17/17 (run by the reviewer).
