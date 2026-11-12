---
module: workspace
feature: workspace-setup-api
task: 4
verdict: pass
attempt: 2
date: 2026-11-13
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: workspace — Task 4: Setup and read API, status lookup, OpenAPI

## Summary
Commits `default@b1b2b30` (original) and `default@b18a62f` (fix), covering STORY-001 and STORY-002. The fix resolves
the attempt-1 IMPORTANT finding (design D30, `backend/AGENTS.md` §4). `setup` is no longer `@Transactional`, so these
steps run with no transaction open: the field checks, the site rule, the `count()` 409 check and
`tracker.checkServiceAccount`. After that, `linkJiraSite`, `persist`+`flush` and the audit run in one
`TransactionTemplate` (`WorkspaceServiceImpl.java:97-127`). AC-001.8 still holds: the link and the insert share a
transaction, and a refused link throws inside it, so `TransactionTemplate` rolls back. The race guard (singleton or
PK unique violation → 409) is unchanged and still covered by the two-thread IT.

There are three new unit tests: one transaction in order (get → link → persist → flush → audit → commit), rollback
with no commit on a refused link, and "no transaction during the Jira check". The last one uses a `doAnswer` that calls
`verifyNoInteractions(transactions)` from inside `checkServiceAccount`, so it fails if a transaction were open.

I re-checked the whole feature against the current spec; STORY-001 and STORY-002 did not change. Verdict: **pass**.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Order matches AC-001.2..11: fields → email → site rule → 409 → Jira check (no tx) → [tx: link → persist+flush → audit] (`WorkspaceServiceImpl.java:74-128`). GET and `isReady()` read the bound Workspace's row (`:130-136`, `WorkspaceStatusLookupImpl`). |
| Completeness | pass | 17/17 ACs PASS (table below). EXTRA (unchanged, harmless, tested): a malformed or non-object body → 400 `MSG03` with no field. |
| Correctness | pass | Orchestrator's verify after the four fixes: `mvn -B verify` — 1594 unit + 481 IT, BUILD SUCCESS. Not re-run by the reviewer (brief: low-RAM machine). No CRITICAL finding. |
| Integration | pass | Endpoints, schemas and `WorkspaceStatusLookup.isReady()` match the Interface Contracts. The fix changes no signature and no OpenAPI. |

### Stage 1 — spec compliance

| # | AC | Status | Evidence |
|---|----|--------|----------|
| 1 | AC-001.1 | PASS | `:108-126`; IT "AC-001.1 returns 201 and stores encrypted setup only in the bound tenant" |
| 2 | AC-001.2 | PASS | `field()` `:195-205`; unit + IT |
| 3 | AC-001.3 | PASS | `:201-203` `MSG02` + `max_length`; unit at and over the limits |
| 4 | AC-001.4 | PASS | `:83-85`; unit, no Jira call |
| 5 | AC-001.5 | PASS | `:86`, before `count()` and any call; unit verifies no interactions |
| 6 | AC-001.6 | PASS | `:92`, before the transaction, the link and the persist; unit "AC-001.6 checks the Jira account before any transaction opens D30" (`WorkspaceServiceImplTest.java:260`) |
| 7 | AC-001.7 | PASS | `:91-96`; unit for 401/403/500/timeout, no link or insert; IT |
| 8 | AC-001.8 | PASS | `:98-127`; unit "AC-001.8 setup links, inserts and audits inside one transaction" (`:236`) and "AC-001.8 a refused link rolls the setup transaction back" (`:250`); IT "AC-001.9 refused link rolls back" (no row) |
| 9 | AC-001.9 | PASS | `:102-104`; unit + IT (`WorkspaceControllerIT.java:201`) |
| 10 | AC-001.10 | PASS | `:105-106`; unit checks the log line has no token and no throwable |
| 11 | AC-001.11 | PASS | `:88-90` + `isConcurrentSetup` `:207-217`; IT `:213` (second setup) and `:226` (`CyclicBarrier` race → 201 + 409, one row, one audit) |
| 12 | AC-001.12 | PASS | `:121-125`; unit + IT, no token or ciphertext in details |
| 13 | AC-001.13 | PASS | `@PreAuthorize` on the controller; slice + IT |
| 14 | AC-002.1 | PASS | `WorkspaceStatusLookupImpl`; unit + IT |
| 15 | AC-002.2 | PASS | `WorkspaceInfoResponse` has 3 fields; slice + IT |
| 16 | AC-002.3 | PASS | `:135`; unit, slice, IT |
| 17 | AC-002.4 | PASS | controller `@PreAuthorize`; slice + IT |

## Traceability
```
$ bun harnessclaude/hooks/sdd.ts trace workspace STORY-001 STORY-002
  ✓ AC-001.1 … ✓ AC-001.13, ✓ AC-002.1 … ✓ AC-002.4
trace workspace (STORY-001, STORY-002): 17/17 acceptance criteria have a test
```
(Files: `WorkspaceServiceImplTest`, `WorkspaceControllerTest`, `WorkspaceControllerIT`, `WorkspaceOpenApiTest`,
`WorkspaceStatusLookupImplTest`, plus Task 1's `WorkspaceInfoRepositoryIT` and `WorkspaceChangelogTest`.)

## Issues
CRITICAL: none. IMPORTANT: none.

MINOR (carried from attempt 1, do not block):
1. `WorkspaceController` / `dto/WorkspaceSetupRequest.java`: the body is validated by hand from `JsonNode` instead of a
   `@Valid` record (`backend/AGENTS.md` §3). The Javadoc justifies it (ordered MSG codes). Note the deviation in the
   design if it stays.
2. `dto/WorkspaceInfoMapper.java`: the MapStruct mapper is in `dto/`; the convention puts mappers in `mapper/`.
3. `WorkspaceServiceImpl.java:78-82`: the length is checked on the raw value and then `strip()`ped, so
   `"  " + 99 chars` passes. Harmless (the DB column is 150).

## Attempt 1
Verdict pass with an IMPORTANT finding (`b1b2b30`).
- IMPORTANT: `setup` was `@Transactional` and made the Jira account check (up to two remote calls) inside the
  transaction, against `backend/AGENTS.md` §4. **Resolved** in `b18a62f` (D30): the check runs before the transaction;
  link + persist + audit run in a `TransactionTemplate`, as `update` does; there are tests for each part.
- MINOR 2–4 (hand validation, mapper package, length before strip). **Not changed**; they are carried as MINOR 1–3.

## Assumptions made
- The link (`InterimJiraSiteLinker`, an outbound GET) still runs inside the transaction. AC-001.8 and D13 require
  that, and the spec wins over the §4 convention. This is not a finding.
- Tests were not re-run by the reviewer. The dispatcher's brief forbids `mvn` on this low-RAM machine. The
  orchestrator's recorded verify (session log, "Session 2026-11-13") is used.

## Test Report
See `task-4-workspace-setup-api-test-report.md`. Recorded: `mvn -B verify` BUILD SUCCESS (1594 unit + 481 IT).
Trace 17/17 (run by the reviewer).
