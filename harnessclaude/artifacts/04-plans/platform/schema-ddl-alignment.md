# Plan: align the backend schema and entities with the DDL

Approved by the user on 2026-11-04. Jira: KAN-248. Waiting for the prerequisites below; nothing here is implemented.

Sources: `SEP490_G89-TaiLieu/SEP490_G89/Notes/DDL_01-11/01_catalog.sql` and `02_tenant.sql`.
The platform spec text used here is on `docs/KAN-33-platform-align-schema-ddl`, which is not merged.

## Decisions

| # | Question | Chosen | Rejected | Why |
|---|----------|--------|----------|-----|
| 1 | Scope | All 25 tenant tables in the changelog, entities only for the 8 mapped tables | Only the 8 tables; 29 entities | FKs get their target tables, and no unused entities. Owning modules add the other 21 entities |
| 2 | Migration | New changeset that drops and recreates | `ALTER` keeping data; squash 001–006 | `architecture.md` forbids editing applied changesets. `String` actors cannot become UUIDs. The repo shows dev databases only, so dev data is lost |
| 3 | Tables of changeset 001 | Drop all, delete `vn.tsolve.knowledge` | Keep them per AC-001.21 | Old `workspace` clashes with the DDL `workspace`. Nothing calls `/api/solutions` |
| 4 | Current user id | `TSolveUser.id()` from PR 107 (KAN-117) | Temporary name-to-UUID port; postpone the ticket part | No temporary code to delete later |
| 5 | Catalog | Rename `sign_in_state` in place; `platform` builds the catalog | Build the catalog database now | No second datasource exists, and routing is platform STORY-001/002 |
| 6 | Jira and branch | New issue KAN-248, `refactor/KAN-248-platform-align-schema-ddl` from an up-to-date `main` | Reuse KAN-33 | One branch per issue |

Decided in planning: the 8 entities map every DDL column, so "matches the DDL" can be read off the entity. Getters
are added only where code reads them.

## Prerequisites, in order

State on 2026-11-05 in brackets.

1. Merge PR 110 (KAN-31): it holds the ticket import UUID changeset, and `main` does not have UUID `project_id` yet.
   [Open: review required, `frontend` check failed, conflicts with `main`.]
2. Merge `docs/KAN-33-platform-align-schema-ddl`: it holds the aligned platform spec, `AGENTS.md` and
   `architecture.md`. [Done: PR 112.]
3. Merge PR 107 (KAN-117): it holds `TSolveUser`. [Done; the name stays `TSolveUser`.]
4. Branch from `main`. Take the next free changeset numbers.

**Main moved after this plan was approved; re-check before the spec step.** `main` now has
`006-identity-app-user.sql`, `007-identity-hand-off-code.sql`, and the entities `identity/account/Account`
(`app_user`) and `identity/signin/HandOffCode`. So the changeset numbers below (`007`, `008`) are taken, the drop and
create lists must not recreate `app_user` or `hand_off_code` blindly, and these two tables have to be compared with
the DDL too. `hand_off_code` is a catalog table in the DDL. These two entities were not compared yet.

## Spec changes (Change Mode, before any code)

Each edit locks that module's code until the user approves the new version with `sdd.ts approve`.

| Module | Spec change | Gate after approval |
|--------|-------------|---------------------|
| `platform` | AC-001.21: the allow-list drops the 5 legacy tables and adds `sign_in_state` as temporary until the catalog exists. Remove the Out of Scope line about legacy tables. Approval is already pending | Changesets open |
| `import-ticket-list` | AC-004.3 and the contract table: file source values, `approved` decision, `previewId` is a UUID. Add a Changelog row | `completed`, so reopen with `sdd.ts status import-ticket-list planned` |
| `tracker` | The `suggestion_result` and `project` ids are UUIDs; the temporary-changeset checklist line goes | Open |
| `identity` | `accounts`, `account_roles`, `jira_account_links` become `app_user`, `user_role`, `app_user.linked_jira_account_id`; `sign_in_states` becomes `sign_in_state` | Open |
| `be-foundation` | AC-004.6, AC-004.7 and the Database contract: `audit_events` becomes `audit_log_entry`, with no `workspace_id` | Open, including `knowledge/Solution.java` and `SolutionService.java` |

Run `risk-review` on the draft design: this is a data migration in a multi-tenant system. Run `edge-cases` for the
new tasks.

Two areas have no module that can open them, because their owners are `decomposed`:

| Files | Owner | Route |
|-------|-------|-------|
| `knowledge/SolutionController`, `SolutionRepository`, `SolutionRequest`, `SolutionStatus` (4 files) | `ticket-repository` | Quick change |
| `project/entity/Project`, `ProjectRepository`, `ProjectService`, `ProjectServiceImpl`, `ProjectServiceImplTest` (5 files) | `project-integration` | Quick change |

A quick change holds at most 5 files, so these are two quick changes, each confirmed by the user.

## Changes

### Changesets

- `007-align-tenant-schema-with-ddl.sql`, author `platform`. Drops every table of 001–006 except the identity table
  below (`DROP TABLE … CASCADE`, because `department` and `project` reference each other). Creates all 25 tables
  verbatim from `02_tenant.sql`.
- `008-identity-sign-in-state.sql`, author `identity`. Drops `sign_in_states`. Creates `sign_in_state` as in
  `01_catalog.sql` without the FK to `tenant_workspace`, which lives in another database. Rows expire in 10 minutes,
  so nothing of value is lost.

### Entities

All ids become UUIDs with `@GeneratedValue(strategy = GenerationType.UUID)` (AC-001.23), except that
`ImportPreviewRow` keeps its composite key.

| Entity | Table | Changes |
|--------|-------|---------|
| `SignInState` | `sign_in_states` → `sign_in_state` | Key column `sign_in_state_id` |
| `Project` | `project` | `project_id` is a UUID. Map `department_id`, `field_mapping`, `done_status`, `review_range` |
| `SuggestionResult` | `suggestion_result` | `suggestion_id`, `project_id` are UUIDs. Map `sources_used`, `suggested_solution`, `reporter_answer` |
| `Ticket` | `ticket` | Key `ticket_id`. Business id `ticket_key`, nullable. `writer` splits into `writer_id` and `author_name`; imports fill `author_name` only. `import_history_id`. Map `review_due_date`, `rejection_reason`, `change_request_comment`, `source_link`, `updated_at` |
| `ImportBatch` → `ImportHistory` | `import_batch` → `import_history` | Key `import_history_id`. `performed_by` is a UUID. `tickets_published`, `errors`. Map `date_range_from`, `date_range_to`. Also rename `ImportBatchStatus`, `ImportBatchRepository`, `ImportBatchRecorder` |
| `ImportPreview` | `import_preview` | Key `import_preview_id`. `owner` becomes `owner_id` |
| `ImportPreviewRow` | `import_preview_row` | `preview_id` becomes `import_preview_id`. `ticket_id` becomes `ticket_key` |
| `TicketReviewDecision` | `ticket_import_decision` → `review_decision` | Key `decision_id`. `ticket_id` is a UUID. `reviewer` becomes `project_manager_id`. `reason` becomes `reason_or_comment`. The sequence goes |

### Enums

- `TicketSource`: `SOLUTION_FORM`, `JIRA_IMPORT`, `FILE_IMPORT`. `CSV` and `MANUAL` go.
- New `ImportSource`: `JIRA`, `FILE`, for `ImportHistory` and `ImportPreview`.
- `ReviewDecision`: `APPROVE` becomes `APPROVED`. The other two values come with the review work.

### Callers and API

- `ImportController` takes `@AuthenticationPrincipal TSolveUser`. Storage uses `user.id()`. `ProjectAccess` keeps its
  `String` key (the email), so `StubProjectAccess` and `.env.example` do not change.
- `ProjectService.findActiveIdByJiraProjectKey` and `IssueCreatedServiceImpl` return and take `UUID`.
- `previewId` and the import result's batch id become UUIDs in `ImportController`, the DTOs,
  `frontend/openapi/tsolve-api.yaml`, the generated client, and `features/ticket-import` mocks and tests.
- The CSV template keeps its `ticket_id` column: only the database column is renamed.

### Deleted

`vn.tsolve.knowledge` (6 files).

## Commits

The changesets and the entities must land in one commit, because `ddl-auto: validate` stops the app otherwise.

1. Harness commit: specs, design, tasks, features.
2. `refactor(ticket-repository): remove the unused knowledge package (KAN-248)`. The `solution` table stays until
   commit 3, which is harmless because nothing maps it any more.
3. `refactor(platform): align the tenant schema and entities with the DDL (KAN-248)`: changesets 007 and 008,
   8 entities, enums, callers, API, tests.
4. Harness commit: feature statuses and `sdd.ts quick done`.

Stop and report to the user before each commit, as the project rules say.

## Verification

- Backend: `mvn verify`. Hibernate `validate` runs in the ITs against the new schema.
- The 18 test files that touch these types or tables are updated, `ImportConfirmIT` and `SignInStateRepositoryIT`
  among them. Fixtures seed `department`, `project` and `app_user`, because the FKs are now real.
- `csv-import-performance` still passes with UUID generation.
- New IT: a CSV Confirm writes `FILE_IMPORT`, `FILE` and `APPROVED`, which the DDL CHECK constraints accept.
- Frontend, in `frontend/`: `format:check`, `lint`, `typecheck`, `test`, `build`, and `generate:api` leaves no diff.

## Risks and open items

- **Dev data:** applying 007 empties tickets, imports and previews in every dev database. Warn the team in the PR.
- **Manual import in dev:** real FKs need `app_user` and `project` rows, and `workspace-session` is pending. Check at
  the start of implementation whether a real import can run in dev at all. The demo seed (platform STORY-012) owns
  dev data, so this plan adds none.
- **Unmerged branches:** `origin/feat/ticket-review` has its own `002-add-ticket-review.sql` and 4 entities that were
  not compared with the DDL. Any branch adding a changeset must rebase on 007.
- **Docs left over:** other specs and designs still naming old tables are updated by their owning modules unless
  the list above names them.

## Out of scope

- The catalog changelog, the second datasource, `tenant_workspace`, `workspace_email`, `hand_off_code` (platform,
  identity).
- Entities for the other 21 tables.
- Embedding and chunk tables (`search`, `wiki`).
