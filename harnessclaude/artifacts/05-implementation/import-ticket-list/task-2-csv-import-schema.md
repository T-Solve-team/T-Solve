# Task 2: Schema, entities and repositories

**Module:** import-ticket-list
**Assignee:** bluebearccc (with Claude)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-10-22 01:20

### Working on: csv-import-schema
- Changeset `002-ticket-import-schema.sql` (id `002-ticket-import-schema`, author `ticket`). It creates `import_batch`, `ticket`, `ticket_review_decision`, `import_preview` (unique `owner`) and `import_preview_row` (JSONB `errors`, ON DELETE CASCADE). The `ticket` and `ticket_review_decision` sequences are set to INCREMENT BY 50. Included in `db.changelog-master.xml`.
- Entities in `ticket/entity`:
  - `Ticket`: factory `imported(…)` with the nested record `Ticket.Solution`; status is always PUBLISHED (GBR-23).
  - `ImportBatch`: factories `completed`/`failed`; every column is `updatable = false`.
  - `TicketReviewDecision.approve`, `ImportPreview`, `ImportPreviewRow` (embedded record id, `Values` record, `remove()`).
  - Enums: `TicketSource`, `TicketStatus`, `ImportBatchStatus`, `PreviewRowStatus`, `ReviewDecision`.
- `csv/RowError` record created here, because `ImportPreviewRow.errors` (JSONB) stores it; Task 4 uses it.
- Repositories:
  - `TicketRepository.findExistingTicketIds` (one JPQL `in` query)
  - `ImportPreviewRepository`: `findByIdAndOwner`, `deleteByOwner` (bulk delete), `findIdsCreatedBefore`
  - `ImportPreviewRowRepository`: paged by preview, with and without status; `findByIdPreviewIdAndStatus`; `count…`
  - `ImportBatchRepository`, `TicketReviewDecisionRepository`
- `application.yml`: `hibernate.jdbc.batch_size: 100` and `order_inserts: true`.
- TEXT and JSONB columns carry an explicit `columnDefinition`, so `ddl-auto: validate` matches the changeset.
- Verify: `mvn spotless:apply` then `mvn verify` → exit 0. There are no tests yet, per `backend/AGENTS.md` §8 (no in-memory DB).
- The machine has JDK 24 only (`JAVA_HOME` pointed at a missing jdk-21), so Maven ran with `JAVA_HOME=C:\Program Files\Java\jdk-24`. The pom still targets Java 21.
- Schema check (2026-10-22): on a fresh `pgvector/pgvector:pg16` container (temporary, port 25432, removed afterwards), `mvn spring-boot:run` applied `001-init-core-schema` and `002-ticket-import-schema`. Hibernate `ddl-auto: validate` passed (`Started TSolveApplication`, `/actuator/health` UP). All 5 tables exist; `ticket_id_seq` and `ticket_review_decision_id_seq` have increment 50.
- Local environment finding: a native Windows PostgreSQL also listens on `0.0.0.0:5432` and takes `localhost:5432`. The compose `db` service is therefore unreachable from the host with `DB_HOST=localhost`, which explains the failed logins for user "tsolve". This is a machine issue, not a project one. Created the git-ignored `.env` from the compose values.

- Commit: default@7c1385d (feat/KAN-31-import-ticket-csv)

### Blockers
none

### Next up
Task 3 csv-parser.
