# Task 15: UUID Project ids

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-01

### Working on: project-uuid
- D10: `ProjectAccess`, `StubProjectAccess` (`user:<uuid>,<uuid>;…`, a malformed id grants nothing), the three entities, `ImportPreviewResponse`, `CsvImportService(Impl)` and the controller take `UUID`. A non-UUID `projectId` is a 400 (Spring type mismatch, existing handler).
- Changeset `008-ticket-import-project-uuid.sql`: `ALTER COLUMN project_id TYPE UUID USING lpad(to_hex(project_id), 32, '0')::uuid` on `import_batch`, `ticket`, `import_preview`; included in the master changelog. `ImportConfirmIT` runs it on PostgreSQL.
- Contract: `projectId` `format: uuid` in the upload body and `ImportPreviewResponse`; client regenerated.
- `.env.example`: stub format comment.
- Tests: `StubProjectAccessTest` on UUIDs (+ an old numeric id grants nothing), `ImportControllerTest.uploadRejectsAProjectIdThatIsNotAUuid`, the other ticket tests and `ImportConfirmIT` moved to UUID ids.

### Verify
- `mvn verify` (JAVA_HOME = jdk-24, `-DargLine=-Dnet.bytebuddy.experimental=true -Dapi.version=1.44`, `DOCKER_API_VERSION=1.44`): 229 unit tests + 10 IT, 0 failures, Spotless OK, BUILD SUCCESS.
  - Environment, not code: JAVA_HOME pointed to a missing jdk-21; Mockito cannot mock a class on JDK 24 without the ByteBuddy experimental flag; Testcontainers needs the Docker API version pinned with Docker 29.
- Frontend: `format:check`, `typecheck`, `lint` (1 old warning in `features/tickets`), `test` (54 files, 1022 tests), `build` pass; `generate:api` is stable.
- `sdd.ts trace … STORY-001` reports 0/12 because its globs skip `*Test.java` (FEEDBACK.md 2026-11-01); grep finds `AC_001_1` … `AC_001_12` in the backend tests.

### Deviations
- Code commit: default@c4f3900 (plan: 3f3734b).
- Branch: `feat/KAN-31-ticket-import-csv-screens` (team rule in the root CLAUDE.md), not `feat/import-ticket-list`.

### Next up
Task 16 preview-read

## Session 2026-11-04

### Working on: project-uuid — Low findings of review attempt 1
- Review issue 2 → BUG-104: `StubProjectAccess` keeps an id only when it is the canonical UUID form; test `aShortFormIdGrantsNothing_BUG_104` (red before the fix).
- Review issue 1: the `application.yml` comment says the ids are UUIDs.
- Review issue 3: `uploadRejectsAProjectIdThatIsNotAUuid` also checks the body carries a `code`.
- Verify: `mvn verify` BUILD SUCCESS (235 unit tests, 1 skipped as before; 10 ITs; spotless) with JDK 24 and `-Dapi.version=1.44` for Docker Desktop 29; frontend `format:check`, `lint` (0 errors, the old `TicketListFilters.tsx` warning), `typecheck`, `test` (58 files, 1083 passed), `build` pass; `generate:api` leaves no diff.
- Commit: default@462b826

### Next up
Module completion
