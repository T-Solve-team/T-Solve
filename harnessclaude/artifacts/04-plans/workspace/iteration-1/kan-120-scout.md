# KAN-120 scout: [UC-12] Create Department

Module `workspace`, status `decomposed`, no spec yet. Written 2026-11-11 for the Planner.
Code facts are from `origin/main` at `d524aece`; the branch `feat/KAN-120-workspace-create-department` is level with it (0 ahead, 0 behind). Line numbers are for that commit.

Names used below:

- **SRS** = `SEP490_G89-TaiLieu/SEP490_G89/_context/Report/Sua_31-10/T-Solve_Report3_Software_Requirement_Specification_EN_v3460.docx.md`. See Q1 for why this copy.
- **Messages file** = `.../Sua_31-10/Report_3_BreakDown/System_Messages.docx.md`; **Rules file** = `.../Report_3_BreakDown/Business_Rules.docx.md`. The SRS changelog (31/10) makes the split files the master; UC-12, MSG01/02/05/43 and GBR-29 read the same in both.
- **DDL** = `SEP490_G89-TaiLieu/SEP490_G89/Notes/DDL_01-11/02_tenant.sql`.
- **DB tables doc** = `SEP490_G89-TaiLieu/SEP490_G89/_context/Report/T-Solve Database Tables.pdf.md` (older, still BIGINT ids).

## 1. UC-12 as written

**Actor.** Admin. No secondary actor (SRS line 988). Permission Matrix: "Department | Create / Update / Delete / Restore | Full" for Admin, "No" for every other role (SRS line 705). GBR-16: "The Admin creates the Departments" (SRS line 2500).

**Precondition.** "The Admin is signed in." (SRS line 991). Nothing more is written. In the web app, `RequireWorkspace` already sends an Admin to 2.1 Workspace Setup while `workspaceReady` is false (`frontend/src/app/router/guards.tsx:57-73`), so in practice the Workspace is set up first.

**Normal flow, "Create a Department"** (SRS line 992, verbatim):

1. The Admin opens Workspace Settings.
2. The Admin clicks "New Department".
3. The system shows the Department form.
4. The Admin enters the Department name and clicks Save.
5. The system checks that the name is entered and does not match any Department, including deleted ones (MSG01, MSG43).
6. The system creates the Department in Active status together with its Department Space (GBR-29), records the action in the audit log and shows MSG05.
7. The new Department appears in the Department list when the Admin adds a Project Manager (UC-05).

**Alternative flows** (SRS line 993):

- Step 5, name empty or already used: the system shows MSG01 or MSG43 and keeps the form open.
- Step 4, Admin cancels: the form closes and nothing changes.

No exception flow is written beyond these. The generic ones apply: no permission (MSG08), unexpected error (MSG06).

**Postconditions** (SRS line 994): "The Department exists with a unique name." and "The action is recorded in the audit log."

**Business rules**

| Rule | Text that matters here | Source |
|---|---|---|
| Name unique | "No two Departments share the same name, including deleted Departments (MSG43)." | SRS line 557 |
| Business identity | "The Department name is unique in the Workspace, including against deleted Departments." Lifecycle Active to Deleted and back. | SRS Entity: Department, line 399 |
| GBR-17 | After a delete, "The Department name stays reserved." | SRS line 2501 |
| GBR-29 | "exactly one Space per Department, created, renamed, hidden and restored together with its Department; there is no separate company-wide Space." "A Space is never deleted." | SRS line 2513, Rules file line 36 |
| One Space per Department | "Each Department has exactly one Department Space ... a Space is never deleted (GBR-29)." | SRS line 574 |
| Space entity | "Created with its Department (UC-12), renamed with it (UC-13), hidden when the Department is deleted (UC-14) and shown again when it is restored (UC-15). Never deleted." Attributes: Department, name, Knowledge Manager (at most one), status. | SRS line 517 |
| GBR-30 | "The Space's own Department always has View on it; this cannot be removed." | SRS line 2514 |
| GBR-37 | Each Workspace has its own database; the Workspace of a request comes only from the host, verified cloudId or job, never from request content. | SRS line 2521 |
| Audit trail | "changes to users, roles and Departments ... are recorded in the audit log and cannot be edited." | SRS line 2466, section IV 2.3 Security |

**Messages, exact text** (Messages file; codes and text match the SRS message table and `frontend/src/shared/messages/catalog.ts`)

| Code | Type | When | Text |
|---|---|---|---|
| MSG01 | Red text below the field | Name empty | The {field_name} field is required. |
| MSG02 | Red text below the field | Name over the limit (not cited by UC-12, see Q4) | {field_name} must not exceed {max_length} characters. |
| MSG43 | Red text below the field | Name already exists, including deleted Departments | A Department with this name already exists. |
| MSG05 | Toast | Saved | Changes saved successfully. |
| MSG08 | Inline | No permission | You do not have permission to access this page. |
| MSG06 | Toast | Unexpected server error | Something went wrong. Please try again later. |

**Screen.** SRS screen 2.2 Workspace Settings (SRS lines 1618-1640), related UCs UC-11, UC-12, UC-15.

| Item | Rule | Source |
|---|---|---|
| New Department (button) | "Asks for the Department name \*: text, at most 50 characters, unique, including against deleted Departments (MSG43)." | SRS line 1639 |
| Departments table | "Columns: Name, Project Managers, Projects, Members, Tickets. Deleted Departments are hidden by default; turn on 'Show deleted' to list them with a Restore button (MSG51, MSG52)." | SRS line 1638 |
| Name limit in the frontend guide | "Department name 50" | `frontend/guidelines/09-forms-and-validation.md` section 2 |
| Screen list | 2.1, 2.2, 2.3, 2.4 only. There is no separate "New Department" screen or popup in the list. | `frontend/guidelines/01-project-structure.md:157`, `frontend/src/features/workspace/routes.ts:3` |

The screen image in the SRS is an embedded picture and was not readable in the markdown copy. The Figma file is linked from `Thiet Ke Giao Dien/Link_Figma.docx`; it was not opened.

Story text (`Notes/Stories_T-Solve.md:90`): KAN-120, UC-12, 2 points, Must, Sprint S1, owner Bình, depends on KAN-118. Criteria: name unique in the Workspace; Department Space created at the same time.

## 2. Conflicts and gaps in the sources

Each has a recommendation, so the Planner can default if the user does not answer.

**Q1. Which SRS is current? The path in `harnessclaude/manifest.yaml` (`sources.srs`, `.../Report/Minh_sua_report123/...v3456.docx.md`) does not exist.**
Copies of v3460: `_context/Report/` (stale; Create Department is still UC-10 there), `DocumentWorkGuide/sources/`, and `Sua_31-10/` (newest, edited 08/11, UC-12 numbering matches Jira).
Options: (a) use `Sua_31-10` and correct the manifest later; (b) ask the user for another file.
Recommended: (a). It is the only copy whose numbering matches Jira, and its split files agree with it. Changing the manifest is not part of this scout.

**Q2. Is "same name" case-insensitive?** The SRS does not say. The DB tables doc says the comparison ignores case ("so sánh không phân biệt hoa thường", lines 134-137). The DDL and the changelog have a plain `UNIQUE (name)`, which is case-sensitive.
Options: (a) case-insensitive, with a new unique index on `lower(name)` in a new changeset; (b) case-sensitive, using the existing constraint only.
Recommended: (a). "IT" and "it" as two Departments is exactly what the rule is meant to stop, and the only document that speaks to it says so. The index also makes it hold under concurrent requests. The existing constraint stays.

**Q3. Is the name trimmed?** No source says.
Options: (a) strip leading and trailing whitespace before the uniqueness check and before saving; (b) keep what was typed.
Recommended: (a). Add User already strips the email (`UserCreationServiceImpl.java:91`) and the front end sends it trimmed (`CreateUserForm.tsx:36`). The MSG01 rule already treats whitespace-only as empty (guideline 09 section 2). Spaces inside the name are left alone.

**Q4. What is the length limit, and which message?** Screen 2.2 says 50 characters. UC-12 cites only MSG01 and MSG43. The column is `VARCHAR(100)` in the changelog, DDL and DB tables doc.
Options: (a) 50, with MSG02, and no schema change; (b) 100.
Recommended: (a). The screen table is the field-level source, guideline 09 also says 50, and MSG02 is the generic message for "longer than the allowed limit".

**Q5. What is the default name of the Space?** No source gives one. The DB tables doc says the Space name "follows the Department name" (line 469). The demo seeder uses `name || ' Wiki'` (`MockUserListSeeder.java:35-36`) and the users MSW data uses "IT Knowledge".
Options: (a) exactly the Department name; (b) Department name plus " Wiki".
Recommended: (a). It is the only documented behaviour, it keeps UC-13's rename a plain copy, and the Space column is wide enough. The seeder value is demo data.

**Q6. Which audit action? UC-12 requires an audit record, but no action fits.** `AuditAction` (`common/audit/AuditAction.java:3-14`), the database check `ck_audit_log_entry_action` (`018-audit-log-entry.sql:14-17`) and the contract enum (`frontend/openapi/tsolve-api.yaml:1530`) hold APPROVE, REJECT, REQUEST_CHANGES, PUBLISH, UNPUBLISH, IMPORT, DELETE, ROLE_CHANGE, PERMISSION_CHANGE, TRACKER_CONFIGURATION_CHANGE. The list in `backend/AGENTS.md` section 5 does not include Departments either, while the SRS audit trail does.
Options: (a) add one new value, `DEPARTMENT_CHANGE`, following the existing `*_CHANGE` names, with `details` saying what happened (for example `{"operation":"CREATE","name":"IT"}`), so UC-13 and UC-15 can reuse it; (b) add `DEPARTMENT_CREATE` and add more values later; (c) reuse an existing value such as `PERMISSION_CHANGE`.
Recommended: (a). (c) makes the log say something false, and (b) costs a new check-constraint changeset per story.
Consequence: four places change together, and two are **outside this module's `paths` in `module.md`** (see section 6): the enum in `common/audit`, a new changeset replacing the check, the contract enum, and `ACTION_LABELS` in `frontend/src/features/audit-log/hooks/audit-filters.ts:4`, which is a `Record<AuditAction, string>` and will not compile without the new key.

**Q7. How much of screen 2.2 does this story build?** `WorkspaceSettingsPage` is a placeholder (`frontend/src/features/workspace/pages/WorkspaceSettingsPage.tsx`). The story needs the "New Department" entry point. Nothing owns the Departments table: KAN-119 is the company information (UC-10, UC-11), KAN-121 to KAN-123 are rename, delete and restore, and no story names the table. No endpoint returns its Project Managers, Projects, Members or Tickets counts.
Options: (a) a page with the New Department button, the form and a Departments table showing Name only, read from the existing `GET /api/v1/users/departments`; (b) the button and form only; (c) the full five-column table with a new endpoint.
Recommended: (a). Without a list the Admin cannot see the result on the screen, and the existing endpoint already returns Active Departments only, which matches "deleted hidden by default". Ask the user to name a story for the other columns and "Show deleted"; (c) goes beyond a 2-point story and needs data other modules own.

**Q8. Is the "Department form" a popup or an inline form?** The SRS says the button "asks for" the name and the system "shows the Department form". The screen list has no such popup.
Options: (a) a `FormModal` titled "New Department" with one field, Save and Cancel; (b) an inline form on the page.
Recommended: (a). It is the kit's pattern for a small form on a page, and it clears itself on close, which is the "Cancel, nothing changes" flow. Button labels "New Department" and "Save" are from UC-12; the field label is "Department name" per the screen table. The modal title is not given by any source, and the Figma frame should confirm it.

**Q9. JPA entity or JDBC repository?** `backend/AGENTS.md` section 3 asks for Spring Data repositories and section 7 asks entities to use a UUID key. `department` has a BIGSERIAL key and no entity exists; every existing write to Department-related tables is JDBC (`UserCreationRepository`).
Options: (a) a JDBC repository in `workspace/repository`, as identity does; (b) a new entity on a BIGSERIAL key.
Recommended: (a). `INSERT ... ON CONFLICT DO NOTHING` gives the duplicate answer without a failed transaction, and no entity is created that KAN-248 would have to redo. The design should state this as a deliberate deviation from section 3.

**Q10. Does this story wait for the UUID alignment (KAN-248)?** `harnessclaude/artifacts/04-plans/platform/schema-ddl-alignment.md` plans to drop and recreate these tables with UUID keys. It is unstarted, and that plan file is not on `main`.
Options: (a) build on today's tables; (b) wait.
Recommended: (a). The API uses only `public_id`, so only the repository's SQL is affected if KAN-248 lands later.

**Q11. Who adds the CSRF header?** The backend requires `X-XSRF-TOKEN` on every `/api/**` write (`SecurityConfig.java:46-47`, be-foundation AC-002.11). `frontend/src/shared/api/http.ts` does not send it, and nothing else in `frontend/src` does. `be-foundation/spec.md:178` lists it as a pending quick change. So the browser POST gets 403 against the real backend, for this story and every other write.
Options: (a) a separate quick change to `http.ts`, in its own commit, before the live check of this story; (b) leave it, and verify this story only with MSW and backend tests; (c) put it in the KAN-120 commit.
Recommended: (a). The story cannot be shown working in a browser without it. (c) breaks the one-feature-per-commit rule, and `shared/` needs the FE Lead's review (`frontend/AGENTS.md` rule 10).

## 3. Schema state on `origin/main`

**`department`** (`002-tenant-project-suggestion.sql:8-14`, `008-search-public-ids.sql:13-15`)

| Column | Type | Notes |
|---|---|---|
| `department_id` | `BIGSERIAL` | primary key |
| `default_project_id` | `BIGINT` | nullable, FK to `project` (`fk_department_default_project`, lines 28-30) |
| `name` | `VARCHAR(100) NOT NULL` | `uq_department_name UNIQUE (name)` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` | no CHECK; `ACTIVE` and `DELETED` by convention |
| `public_id` | `UUID NOT NULL DEFAULT gen_random_uuid()` | `uq_department_public_id` |

**`space`** (`020-identity-space-manager.sql:5-11`)

| Column | Type | Notes |
|---|---|---|
| `space_id` | `BIGSERIAL` | primary key |
| `public_id` | `UUID NOT NULL DEFAULT gen_random_uuid()` | unique |
| `department_id` | `BIGINT NOT NULL UNIQUE` | FK to `department` |
| `name` | `VARCHAR(100) NOT NULL` | not unique |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` | CHECK `ACTIVE`, `HIDDEN` |

**`space_manager`** (same file, lines 12-17): `space_id BIGINT` primary key and FK, `user_id UUID NOT NULL` FK to `app_user`, `assigned_at TIMESTAMPTZ NOT NULL`. So at most one Knowledge Manager per Space.

**`audit_log_entry`** (`018-audit-log-entry.sql`): `actor_id UUID`, `actor_email VARCHAR(254) NOT NULL`, `action VARCHAR(50) NOT NULL` with the check above, `target_type VARCHAR(50) NOT NULL`, `target_id UUID NOT NULL`, `details JSONB`, `created_at TIMESTAMPTZ NOT NULL`. Append-only by trigger.

**Soft delete.** There is no `is_deleted` or `deleted_at`. A deleted Department stays in the table with `status = 'DELETED'`, so `uq_department_name` already covers deleted rows, as the SRS requires. Nothing filters the constraint by status.

**Workspace scope.** There is no `workspace_id`, by design (ADR-0002, `docs/adr/0002-database-per-workspace.md`). Each Workspace has its own database, so a table-wide `UNIQUE (name)` is "unique in the Workspace".

**Differences from the DDL**

| Item | Changelog | DDL |
|---|---|---|
| `department.department_id` | `BIGSERIAL` | `UUID`, `gen_random_uuid()` |
| `department.default_project_id` | `BIGINT` | `UUID` |
| `department.public_id` | present | absent |
| `space.space_id`, `space.department_id` | `BIGSERIAL`, `BIGINT` | `UUID` |
| `space.public_id` | present | absent |
| `space.status` CHECK | present | absent |
| `space_manager` key | `space_id` only (one manager) | composite `(space_id, user_id)` |
| `space_manager` timestamp | `assigned_at`, no default | `date_assigned`, `DEFAULT now()` |
| `space_manager.space_id` FK | no `ON DELETE CASCADE` | `ON DELETE CASCADE` |
| `space_manager.user_id` FK | `app_user (id)` | `app_user (user_id)` |
| `space_manager` user index | `idx_space_manager_user` | absent |
| `space.department_id` unique | inline, unnamed | named `uq_space_department` |
| `space_permission`, `folder`, `wiki_document` | absent | present |

The `department` and `space` tables are otherwise aligned for this feature. The DB tables doc also lists `department.created_at`; neither the DDL nor the changelog has it, and platform AC-001.20 limits timestamps to the DDL's, so it is not needed.

**Missing for UC-12**

- A new changeset extending `ck_audit_log_entry_action` (Q6). The check must be dropped and re-added, since a changeset that is already applied may not be edited.
- A case-insensitive unique index on `department` if Q2 (a) is chosen.
- Next free number: `027` (the last are `026-identity-app-user-keycloak-sub.sql` and two `025`/`024` files; the master file order is what matters). Each new file must be added at the end of `db.changelog-master.xml`.

No new table or column is needed. Nothing in the schema forces a Department to have a Space (the foreign key runs from `space` to `department`), so the create operation is what keeps that true.

There is also a legacy `workspace` table (`001-init-core-schema.sql:8-14`) with demo rows for IT, HR, Finance and Asset. It is not the DDL's `workspace` and has nothing to do with this story; do not use it.

Row policies, `tenant_identity` and the AC-001.22 convention guard test are not on `main`, so they constrain nothing today. A new table is not being added anyway.

## 4. Code to reuse

Do not rewrite any of these.

| Need | Reuse | Where |
|---|---|---|
| Admin-only endpoint | `@PreAuthorize("hasRole('" + Roles.ADMIN + "')")` | `identity/controller/UserCreationController.java:39`, `common/security/Roles.java:9` |
| Authentication fallback | `/api/**` authenticated, everything else denied | `common/security/SecurityConfig.java:41-44` |
| Controller shape for a create | 201 with DTO; `@Operation(operationId = ...)`; `@ApiResponse` for 400, 403, 409 | `UserCreationController.java:34-43` |
| Validated request | `record` with `@NotBlank(message = "MSG01") @Size(max = ..., message = "MSG02")` | `chatbot/answer/dto/ChatbotQuestionRequest.java:9-10` |
| 400 and fieldErrors for validation | Built in; Bean Validation maps to MSG01 and MSG02 | `common/exception/GlobalExceptionHandler.java` (`fromConstraint`) |
| Duplicate as a field error | `new BusinessRuleException("MSG43", Map.of(), "name")` gives 409 with `fieldErrors` | `common/exception/BusinessRuleException.java:17`, handler `coded` at `GlobalExceptionHandler.java:173` |
| Race-safe uniqueness | `INSERT ... ON CONFLICT ... DO NOTHING`, then check the row count | `identity/repository/UserCreationRepository.java:71-82` |
| Service transaction | `@Transactional` on the write; `scope.currentWorkspaceId()` first | `identity/service/impl/UserCreationServiceImpl.java:77-80` |
| Audit record | `audit.record(action, "DEPARTMENT", id, details)`, in the same transaction; it refuses to run outside one and reads the actor from `TSolveUser.current()` | `common/audit/AuditService.java:9`, `impl/AuditServiceImpl.java:33-38`, caller `UserCreationServiceImpl.java:183` |
| Workspace of the request | `TenantScope.currentWorkspaceId()` throws when nothing is bound, giving 500 MSG06 | `identity/signin/port/TenantScope.java`, `LocalTenantScope.java:17-23`, bean in `SignInConfig.java:88` |
| Service unit test | Mockito, checks the audit call | `backend/src/test/java/vn/tsolve/identity/service/impl/UserCreationServiceImplTest.java:261` |
| Controller test | `@WebMvcTest` with `SecurityConfig` and the handler, for 403 and 400 | `backend/src/test/java/vn/tsolve/identity/controller/UserCreationControllerTest.java:35-37` |
| Real-database test | Shared containers; two Workspace databases | `backend/src/test/java/vn/tsolve/identity/UserCreationIT.java`, `TwoTenantDatabases.java` |
| Form popup | `FormModal`; full example in the review feature | `frontend/src/shared/ui/FormModal/FormModal.tsx`, `frontend/src/features/review/components/RejectTicketModal.tsx` |
| Form rules and server errors | `rules.required`, `rules.maxLength`, `count={{ show: true, max }}`, `applyApiErrors(form, error)` | `frontend/guidelines/09-forms-and-validation.md`, `CreateUserForm.tsx:44,70-73` |
| Mutation hook | `meta: { handlesOwnErrors: true }`, invalidate the list, `showMessage` | `frontend/src/features/users/hooks/useUserMutations.ts:11-22` |
| Reading Departments | `useGetUserDepartments`, Active only, sorted by name | `features/users/components/CreateUserForm.tsx:22`, `identity/repository/UserListRepository.java:96-100` |
| Route and role guard | Already declared for 2.2, Admin only | `frontend/src/features/workspace/routes.ts:15-20` |
| Message text | Catalog already has MSG01, 02, 05, 43 | `frontend/src/shared/messages/catalog.ts:24,25,28,120` |

Do not read the Department list from Search scope: `ScopeTicketRepository.java:10-11` finds Departments through their tickets, so a new, empty Department would not appear there.

**Contract and generated client.** `frontend/openapi/tsolve-api.yaml` is the source of truth (AGENTS section 6). `npm run generate:api` (orval, `frontend/orval.config.ts`) writes `src/shared/api/generated`; never edit it by hand, and CI fails on a diff (`.github/workflows/ci.yml:120-126`). Change the YAML first. Reusable schemas: `DepartmentRef` (line 1773), `components/responses/Problem`. A new tag is needed; the list at the top of the file has none for Departments.

**`ScopeAccessService` (KAN-118)** is not used by this story. It answers "may this account reach this existing Department, Project or Space" (`identity/service/ScopeAccessService.java:6-18`). Create is Admin-only and has no Department id yet. Its queries do read `department` and `space` by `public_id` and `status = 'ACTIVE'` (`ScopeAccessRepository.java:33,55-56`), so new rows must be `ACTIVE` with their defaults, and they will be.

## 5. What creating the Space means

There is no code that creates a Space outside the demo seeder (`MockUserListSeeder.java:35-36`).

Concretely, in **one database transaction**:

1. Insert one row in `department`: `name` (the stripped name), `status` left to its default `'ACTIVE'`, `default_project_id` NULL. Use `RETURNING department_id, public_id`. If the insert hits the unique name (zero rows), throw MSG43.
2. Insert one row in `space`: `department_id` from step 1, `name` = the Department name (Q5), `status` left to its default `'ACTIVE'`. `public_id` and `space_id` come from their defaults.
3. Record one audit entry (Q6): `target_type` `DEPARTMENT`, `target_id` the Department's `public_id`, `details` holding the name and the new Space's `public_id`.

All three are in the same `@Transactional` method, so a failure leaves nothing (GBR-29: "together"). No event or background job: nothing leaves the database, so the AGENTS rule against outbound calls in a transaction is not touched.

What is **not** created:

- **No `space_manager` row.** A Space has at most one Knowledge Manager, chosen by the Admin in UC-05, UC-07 and UC-09. The Admin who creates the Department does not become its manager.
- **No permission row.** GBR-30's "the Space's own Department always has View" is a derived rule, already coded that way in `ScopeAccessRepository.java:62-64` (`s.department_id IN (...)`), and the `space_permission` table does not exist on `main`. The locked row in Space Settings (SRS line 2274) is wiki's to render when it is built.
- **No folders or documents.** A new Space is empty.

Who writes it: the `workspace` module, directly. `wiki` depends on `workspace` in `module-map.md` (line 43), so `workspace` calling `wiki` would be a cycle (AGENTS section 1), and `wiki` has no code and no spec. The `space` table's comment already says the wiki module reuses it (`020-identity-space-manager.sql:4`).

The new Space becomes visible without further work: `getUserCreationOptions` lists Active Spaces for the Knowledge Manager picker (`UserCreationRepository.java:44-49`).

## 6. Backend and frontend scope

**Backend (new, inside `workspace/`).** `controller/DepartmentController`, `service/DepartmentService` and `impl/DepartmentServiceImpl`, `repository/DepartmentRepository` (JDBC, Q9), `dto/CreateDepartmentRequest`. Response reuses a `{departmentId, name}` shape, matching the contract's `DepartmentRef`. Proposed endpoint: `POST /api/v1/departments`, operationId `createDepartment`, 201; 400 MSG01 or MSG02 on field `name`; 409 MSG43 on field `name`; 403 MSG08. AGENTS naming fixes the path form. Nothing here is decided by a source beyond that form, so the Planner should confirm.

**Frontend.** Yes, there is a screen to build under this story: `WorkspaceSettingsPage` (route exists, content is a placeholder), with a New Department popup and, on Q7 (a), a Name-only table. All inside `features/workspace`, plus the generated client. On success the page refreshes `getUserDepartments`, which also satisfies UC-12 step 7, because the Add User form reads the same query. The Add User and Roles drop-downs need no change.

Caveats for the Planner:

- MSW: `FEATURE_MOCKS` tries `users` before `workspace` (`frontend/src/mocks/handlers.ts`), and `users` answers `GET /users/departments` from a static list, so in browser mock mode a newly created Department will not appear in that list. A page test can override the handler for its own case. Do not import the other feature's mock data.
- Bean Validation fills `field_name` with the API field name (`name`), so a server-side MSG01 reads "The name field is required." The browser rule shows the proper "Department name" label first, so this is only seen if the client check is bypassed.
- `@Size` counts the untrimmed value. A name padded past 50 characters is rejected with MSG02, which is acceptable.

**Behaviours the spec needs to cover** (each testable): created with Active status; Space created with it, same name, Active; both or neither on failure; empty or whitespace-only name gives MSG01 and no rows; over 50 gives MSG02; duplicate of an Active Department gives MSG43; duplicate of a Deleted Department gives MSG43; duplicate differing only in case gives MSG43 (Q2); two concurrent requests with one name give one Department and one MSG43; non-Admin gets 403 MSG08 and no rows; audit row written with actor, action and target; no audit row on failure; Cancel writes nothing; the form stays open with input on error; the new Department appears in the Add User list; tenant isolation (a Department in Workspace A does not block or appear in Workspace B).

**Dependencies on `origin/main`**

| Dependency | State on `origin/main` | Blocks? |
|---|---|---|
| KAN-118 scope checks | Merged (`705ad787`). Not called by this story. | No |
| Tenant layer (KAN-98, KAN-99): `TenantContext`, routing `DataSource`, row guard | Not on `main`; `platform` is still `planned` with these features pending (`harnessclaude/progress.json:304`). No `common/tenant`, no `tenant_identity`, one datasource (`application.yml:13-16`). `TenantScope` with `LocalTenantScope` is the interim port every identity service already uses, and it does no routing. | No. Build on `TenantScope`, as identity did. "Unique in the Workspace" holds by design (ADR-0002), but until routing exists only an IT on `TwoTenantDatabases` can demonstrate it. |
| Per-request Workspace binding (KAN-275, identity Tasks 26a/26b, `WebSessionFilter`) | Not on `main`. Only `MockSignedInCallerFilter` under the `identity-mock` profile binds a Workspace for API calls (`MockIdentityConfiguration.java:13`, `MockSignedInCallerFilter.java:43`). Without it, the service answers 500 MSG06. A real Keycloak sign-in ends in "session not built yet" and MSG06 (`OidcCallbackHandler.java:145-147`). | Not for building. It does block the production Admin journey: until KAN-275 lands the endpoint runs for real only under `identity-mock`, same as the User List. Say so in the spec. |
| CSRF header in the web app | Not on `main` (Q11). | Blocks a live browser check against the backend, for every write. Does not block the code, the tests or MSW. |
| Workspace catalog (Registry) | Not on `main`. Not needed: create does not read the catalog. | No |
| KAN-119 (company information) | Not on `main`, no branch found; S2, depends on KAN-114. The page is a placeholder. | No. KAN-120 adds its part of the page; a later KAN-119 adds the rest. |
| Wiki module | No code and no spec. `space` table is present. | No |
| Harness gate | `workspace` is `decomposed`; `paths` in `module.md` do not cover `backend/.../common/audit/**` or `frontend/src/features/audit-log/**`. | Process blocker for Q6: `module.md` must list those two files, or they go through a separate quick change, before the code gate will open them. |

Sprint note: KAN-120 is S1 and KAN-119 is S2, so 2.2 will be built in the order this story needs, with the company-information part arriving later.

## 7. Codex disagreements

Codex CLI 0.167.0 ran read-only and finished with exit 0. It read the code, the changelog, the DDL and the revised SRS. Every claim kept below was checked against the file. It ran no tests, and neither did this scout.

Codex agreed with this report on: the current Department and Space columns and BIGINT keys, deleted names staying reserved, `ON CONFLICT` as the race-safe pattern, the missing audit action, the 50-versus-100 limit, the inserts that create a Space, no production Space-create code, and `ScopeAccessService` not being a create guard.

| # | Topic | Codex said | This scout said first | What the file shows | Outcome |
|---|---|---|---|---|---|
| 1 | Case and whitespace in the name | The comparison policy is unspecified in the sources | Case-insensitive, per the DB tables doc | `T-Solve Database Tables.pdf.md:134-137` says the comparison ignores case. The SRS and DDL are silent. Whitespace is stated nowhere. | This scout on case, Codex on whitespace. Kept as Q2 and Q3. The doc is older than the DDL, so the user should confirm. |
| 2 | CSRF on the browser POST | The generated request and `http.ts` may not send the token; not verified | Not checked | `SecurityConfig.java:46-47` enforces CSRF. A search of `frontend/src` on `origin/main` finds no `XSRF` or `csrf`. `be-foundation/spec.md:178` records the header as an open quick change. | Codex was right and the first draft missed it. Now Q11 and a row in section 6. |
| 3 | Is "unique in the Workspace" actually met? | Uniqueness is database-wide, and real isolation is unproven on this commit | Per-database uniqueness equals per-Workspace | Both are true. `application.yml:13-16` has one datasource, `LocalTenantScope` does no routing, and routing exists only in `TwoTenantDatabases.java` under tests. | Refined. The design is right under ADR-0002, but only an IT on `TwoTenantDatabases` can prove it today. Noted in section 6. |
| 4 | Is the production Admin journey blocked? | Yes: the OIDC callback does not build a session | Only that the binding needs `identity-mock` | `OidcCallbackHandler.java:145-147` logs "session not built yet" and redirects with MSG06. | Codex's statement is stronger and correct. Section 6 now says the real sign-in cannot reach this screen yet. |
| 5 | Own-Department View: stored row or derived? | Not verified | Derived, no row | `ScopeAccessRepository.java:62-64` derives it, and `space_permission` does not exist on `main`. SRS line 2274 shows a locked row in the Permissions tab, which wiki can render without a stored row. | This scout, for this story. Wiki decides how the tab is built. |
| 6 | Default Space name | Not verified; the seeder suffix is not a rule | The Department name | DB tables doc line 469 says the Space name follows the Department name. | Kept as Q5, recommendation unchanged. |
| 7 | Traps | Search scope finds Departments only through tickets; a legacy `workspace` table exists | Not covered | `ScopeTicketRepository.java:10-11` and `001-init-core-schema.sql:8-14` confirm both. | Added to sections 3 and 4. |
| 8 | Differences from the DDL | Listed more: named unique on Space, `app_user.user_id` versus `app_user.id`, the user index on `space_manager` | Shorter table | All confirmed in `020-identity-space-manager.sql` and `02_tenant.sql:271-284`. | Added to section 3. |
