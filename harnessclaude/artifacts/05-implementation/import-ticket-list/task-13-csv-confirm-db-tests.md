# Task 13: Real-database tests for Confirm

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 22:00

### Working on: csv-confirm-db-tests
- `backend/pom.xml`:
  - test dependencies `spring-boot-testcontainers`, `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter`;
  - `maven-failsafe-plugin` (`integration-test` + `verify`), so `*IT` classes run in `mvn verify`.
- Decision (user, 2026-10-22): `<testcontainers.version>1.21.4</testcontainers.version>` overrides Spring Boot 3.3.5's 1.19.8.
  - Why: 1.19.8 fails on Docker Engine 29 with `BadRequestException (Status 400)`, because its default Docker API version is too old, and Testcontainers reports "Could not find a valid Docker environment". Forcing `-Dapi.version=1.44` proved this was the only problem; 1.21.4 works without any flag.
- `ImportConfirmIT` (`@SpringBootTest`, `pgvector/pgvector:pg16` with `@ServiceConnection`, Liquibase runs the real changesets; the stub maps `pm-a`, `pm-b`, `boom-pm` to Project 1). 4 tests, all pass:
  1. `savingFailureRollsBackEveryTicketButKeepsTheFailedBatch_AC_004_8`: a PL/pgSQL trigger raises an exception on insert into `ticket_review_decision` for reviewer `boom-pm`, the last write of the import. Result: MSG33; 0 tickets and 0 decisions; exactly one `import_batch` row, FAILED with published 0 (so the REQUIRES_NEW record survived the rollback); the preview is kept; no event.
  2. `eventArrivesAfterTheTicketsAreCommitted_AC_004_10`: a test `@EventListener` sees `tx=false` and all 3 tickets visible to another JDBC session when the event arrives.
  3. `uniqueTicketIdIsTheLastGuardAgainstTwoConfirmsAtOnce` (edge case 21): two PMs confirm previews with the same ids at the same moment. Exactly one succeeds, the other fails with MSG33, whether the id check or the unique constraint caught it; 2 tickets; batches COMPLETED + FAILED.
  4. `databaseRejectsADuplicateTicketId`: a direct duplicate insert → `DuplicateKeyException` (`uq_ticket_ticket_id`).
- `backend/AGENTS.md` §8: added that DB-dependent behaviour is tested in `*IT` classes with Testcontainers, and that Docker must be running for `mvn verify`.
- Verify: `mvn spotless:apply`, then `mvn verify -DargLine="-Dnet.bytebuddy.experimental=true"` → exit 0. Surefire: 87 tests, 0 failures. Failsafe: 4 ITs, 0 failures. The argLine is only for this machine's JDK 24 (task 9); the ITs need no flag.

- Commit: default@d15e991 (feat/KAN-31-import-ticket-csv)

### Blockers
none (the trace gap stands, FEEDBACK.md 2026-10-22)

### Next up
Iteration 1 implementation complete; next the evaluate phase.
