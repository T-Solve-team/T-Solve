# Session Log: workspace — Task 4 workspace-setup-api

**Module:** workspace
**Assignee:** Codex (developer), orchestrated by Claude
**Status:** evaluating
**Iteration:** 1

## Session 2026-11-12

### Working on: workspace-setup-api
- Codex wrote `common/lookup/WorkspaceStatusLookup` + `WorkspaceStatusLookupImpl`, `WorkspaceController`
  (`POST /api/v1/workspace/setup`, `GET /api/v1/workspace`, Admin only), `WorkspaceService(Impl)`, DTOs + MapStruct
  mapper, OpenAPI paths/schemas (PUT declared for Task 5) and the regenerated client.
- Setup order as design: fields (MSG01/02/03) → site rule (MSG61) → 409 when set up → service account check (MSG156)
  → link (MSG153 / MSG61) → insert → audit (no token).
- Decisions (Codex): insert-only `EntityManager.persist` + flush (a `save` with an assigned id would merge and could
  overwrite a concurrent Setup); the singleton/PK unique violation maps to 409; company name and email trimmed.
- The Codex process was orphaned when Claude Code reaped background shells for low memory; it finished on its own.
- Verify: `mvn -B verify` — 1534 unit + 454 IT, BUILD SUCCESS (WorkspaceServiceImplTest 41, WorkspaceControllerIT 15,
  WorkspaceOpenApiTest 2). Frontend: format:check ok, lint 0 errors (1 existing warning in tickets), typecheck ok,
  generate:api leaves the generated folder unchanged (hash), vitest 86 files / 1499 tests passed, build ok.
- Trace STORY-003, STORY-004: 17/17.
- Commit: default@b1b2b30

### Next up
Task 2 fix-up (trace ids), then Task 5 workspace-update-api

## Session 2026-11-13 (fix after review, attempt 1 → 2)

- Review IMPORTANT (D30, backend/AGENTS.md §4): Jira check ran inside the setup transaction. Fix: `@Transactional` removed; check first, link + persist + audit in `TransactionTemplate`. Tests: AC-003.8 one transaction, AC-003.8 refused link rolls back, AC-003.6 no transaction during the check.
- Verify (tree with all four fixes): `mvn -B verify` — 1594 unit + 481 IT, BUILD SUCCESS.
- Commit: default@b18a62f
