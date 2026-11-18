# Test Report: ticket-repository — Task 27: ticket-publication

**Date:** 2026-11-15
**Commit range:** `default`: ce4dad2..da60828 (checked at HEAD 18faf52, the code of 1315800)
**Commands (in `backend/`, Git Bash, `JAVA_HOME=jdk-24`, `JAVA_TOOL_OPTIONS=-Dnet.bytebuddy.experimental=true`):**
`mvn -o verify` · `mvn -o failsafe:integration-test failsafe:verify -Dit.test=ReviewQueueEventsIT` (re-run of the one failure) · `mvn -o spotless:check`
Traceability: `bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-009`

## Results
- Unit (Surefire): Total 2500 · Passed 2500 · Failed 0 · Errors 0 · Skipped 0
- Integration (Failsafe): Total 771 · Passed 770 · Failed 1 · Errors 0 · Skipped 0 → `BUILD FAILURE` (Total time 26:59 min)
- Re-run of the failed class alone: `ReviewQueueEventsIT` Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 → `BUILD SUCCESS`
- `mvn -o spotless:check` → `BUILD SUCCESS` (run separately because the full run stopped at `failsafe:verify` before Spotless)
- Typecheck (compile): pass · Lint: n/a (Spotless only)

New tests of this task:
- `vn.tsolve.ticket.service.impl.TicketPublicationTest` — Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
- `vn.tsolve.ticket.controller.TicketPublicationIT` — Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
Touched / related ITs: `ReviewStorageIT` 4/4 · `ReviewHistoryApiIT` 19/19 · `TicketDecisionIT` 9/9 — all pass.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no coverage goal is bound in `mvn verify`) | spec gives none |

## Acceptance Criteria → Tests
```
  ✓ AC-009.1  TicketPublicationTest, TicketPublicationIT
  ✓ AC-009.2  TicketPublicationTest, TicketPublicationIT
  ✓ AC-009.3  TicketPublicationTest, TicketPublicationIT
  ✓ AC-009.4  TicketPublicationTest, TicketPublicationIT
  ✓ AC-009.5  TicketPublicationTest, TicketPublicationIT
  ✓ AC-009.6  TicketPublicationIT
  ✓ AC-009.7  TicketPublicationTest, TicketPublicationIT
  ✓ AC-009.8  TicketPublicationActions.test.tsx
  ✓ AC-009.9  TicketPublicationActions.test.tsx
  ✓ AC-009.10 TicketPublicationActions.test.tsx
trace ticket-repository (STORY-009): 10/10 acceptance criteria have a test
```
AC-009.1 to AC-009.7 belong to this task; each named test was read and checks the criterion (see review).

## Failures
### `backend/src/test/java/vn/tsolve/review/ReviewQueueEventsIT.java` — edge1_ticketMovedToAnotherProjectWhilePendingNotifiesNewAndOldProject_AC_012_2
- Error: `ReviewQueueEventsIT.edge1_…_AC_012_2:277->openReady:213->awaitUntil:189->awaitUntil:200 [condition within 5000 ms]`
- Cause: a timing wait (SSE stream ready within 5 s) under machine load (4 java, 34 node processes from other sessions during the run). The class passed 18/18 when re-run alone. Nothing in ce4dad2..da60828 touches the review queue events, `review_queue` notify or the project move.
- Suggested fix: none for this task. The test polls with a fixed timeout, which backend AGENTS.md §8 warns about (BUG_173 pattern); worth a bug-log row by the `ticket-review` owner if it recurs in CI.

## Notes
- Flaky: `ReviewQueueEventsIT` (above). No skipped tests. Slow: `TicketPublicationIT` 4.2 s (shares the `TwoTenantDatabases` container, no new container).
- The full suite took 27 min instead of ~16 min because other sessions loaded the machine.
