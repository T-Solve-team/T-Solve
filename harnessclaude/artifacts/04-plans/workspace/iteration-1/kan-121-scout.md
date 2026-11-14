# KAN-121 scout: [UC-13] Update Department

Module `workspace`, status `implementing` (UC-12 features `create-department-api` and `create-department-screen` are `passing`). Written 2026-11-12 for the Planner.
Code facts are from the branch `feat/KAN-121-workspace-update-department` at `4eb50c68`, which is level with `feat/KAN-120-workspace-create-department` (pull request #202, not merged) and 11 commits ahead of `origin/main`, 0 behind. Line numbers are for that commit.

Names used below (same sources as `kan-120-scout.md`):

- **SRS** = `SEP490_G89-TaiLieu/SEP490_G89/_context/Report/Sua_31-10/T-Solve_Report3_Software_Requirement_Specification_EN_v3460.docx.md` (KAN-120 D6).
- **Messages file** = `.../Sua_31-10/Report_3_BreakDown/System_Messages.docx.md`; **Rules file** = `.../Report_3_BreakDown/Business_Rules.docx.md`; **UC list** = `.../Report_3_BreakDown/UC_list_and_Actors.docx.md`.
- **DDL** = `SEP490_G89-TaiLieu/SEP490_G89/Notes/DDL_01-11/02_tenant.sql`.
- **Stories** = `SEP490_G89-TaiLieu/SEP490_G89/Notes/Stories_T-Solve.md` (24/10, older than the SRS revision of 31/10).

## 1. UC-13 as written

**Actor.** Admin. No secondary actor (SRS line 998). Permission Matrix: "Department | Create / Update / Delete / Restore | Full" for Admin, "No" for Project Manager, Ticket Resolver, Knowledge Manager and Employee (SRS line 705).

**Description** (SRS line 1000): "As the Admin, I want to rename a Department when the team is renamed and choose the Project that receives the issues created from the Knowledge Chatbot, so that every screen shows the correct name and questions without an answer reach the right team."

**Preconditions** (SRS line 1001): "The Admin is signed in." and "The Department is Active."

**Normal flow, "Rename a Department"** (SRS line 1002, verbatim):

1. The Admin opens Workspace Settings and opens the Department Detail of a Department.
2. The Admin clicks Edit, changes the name and, for a Department that handles support requests, selects the default Project (GBR-40), then clicks Save.
3. The system checks the name (MSG01, MSG43) and that the default Project has the issue type Task and requires no other field (MSG157), and that the service account has Create Issues and Modify Reporter in it (MSG156).
4. The system saves the new name of the Department and of its Department Space, records the action in the audit log and shows MSG05.
5. The new name appears on every screen immediately. The Projects and tickets of the Department do not change.

**Alternative flows** (SRS line 1003, verbatim):

- Step 3, Name empty or already used: "The system shows MSG01 or MSG43 and keeps the form open."
- Step 2, Admin cancels: "Nothing changes."

**Exception flows.** None written. The generic ones apply: no permission (MSG08), session expired (MSG07), unexpected error (MSG06). No flow covers MSG156 or MSG157 failing, a Deleted or unknown Department, or two Admins editing at once.

**Postconditions** (SRS line 1004): "The Department has a new, unique name. A Department with a default Project is offered in the Knowledge Chatbot issue form." and "The action is recorded in the audit log."

**UC list** (line 34): "Rename a Department and its Department Space; the new name must be unique. For a Department that handles support requests, choose the default Project that receives issues created from the Knowledge Chatbot (issue type Task); other Departments have none (GBR-40)."

**Story text** (Stories line 91): KAN-121, UC-13, 1 point, Must, Sprint S1, owner Bình, depends on KAN-120. "Là Admin, tôi muốn đổi tên Department." Criteria: "Tên mới duy nhất" (new name unique) and "Department Space đổi tên theo" (the Space is renamed with it). **No default Project.** The default Project part reached UC-13 in the SRS revision of 31/10 (SRS changelog line 207: "new UC-58 ... default Project of a support Department"), after the story file of 24/10. The Jira issue itself was not read by this scout. See Q1.

**Business rules**

| Rule | Text that matters here | Source |
|---|---|---|
| Name unique | "No two Departments share the same name, including deleted Departments (MSG43)." Applies to "Creating or renaming a Department". | SRS line 557 |
| Business identity | "The Department name is unique in the Workspace, including against deleted Departments." Attributes include "default Project (support Departments only)". | SRS Entity: Department, lines 403-404 |
| GBR-17 | After a delete, "The Department name stays reserved." When the Department is deleted it "has no default Project". | SRS line 2501, Rules file line 24 |
| GBR-29 | "exactly one Space per Department, created, renamed, hidden and restored together with its Department". | SRS line 2513, Rules file line 36 |
| Space entity | "renamed with it (UC-13)". | SRS line 517 |
| Space header | "Read-only: Space name, Department, Knowledge Manager". No UC renames a Space on its own. | SRS line 2273 |
| GBR-40 | The Chatbot issue is created "in the default Project of the chosen Department, set by the Admin (UC-13)". "Only Departments that handle support requests ... have a default Project". "The default Project must have the issue type Task and require no field other than summary and description (MSG157)." When the default Project is removed (GBR-21) or its Department deleted (GBR-17), "the Department has no default Project ... until the Admin chooses another one (UC-13)". Same text in the Rules file. | SRS line 2524, Rules file line 47 |
| GBR-37 | The Workspace of a request comes only from the host, verified cloudId or job, never from request content. | SRS line 2521 |
| Audit trail | "changes to users, roles and Departments ... are recorded in the audit log and cannot be edited." | SRS line 2466 |

**Messages, exact text** (Messages file; the same in the SRS message table)

| Code | Type | When | Text | In `catalog.ts`? |
|---|---|---|---|---|
| MSG01 | Red text below the field | Name empty | The {field_name} field is required. | yes |
| MSG02 | Red text below the field | Name over 50 (not cited by UC-13; KAN-120 D9) | {field_name} must not exceed {max_length} characters. | yes |
| MSG43 | Red text below the field | Name already exists, including deleted | A Department with this name already exists. | yes (`catalog.ts:120`) |
| MSG05 | Toast | Saved | Changes saved successfully. | yes |
| MSG157 | Red text below the field | Default Project has no Task type or requires other fields | {project_name} cannot receive Chatbot issues: it has no issue type Task or requires other fields. Choose another Project. | **no** |
| MSG156 | Red text below the field | Service account cannot connect or lacks permissions | The service account could not connect to {tracker_name} or lacks the required permissions. | **no** |
| MSG04 | Inline | Nothing found | No results found. | yes (`catalog.ts:27`) |
| MSG06 | Toast | Unexpected server error | Something went wrong. Please try again later. | yes |
| MSG07 | Confirmation dialog | Session expired | Your session has expired. Please log in again. | yes |
| MSG08 | Inline | No permission | You do not have permission to access this page. | yes |

Messages file rows: MSG01 line 9, MSG02 10, MSG04 12, MSG05 13, MSG06 14, MSG07 15, MSG08 16, MSG43 56, MSG157 57, MSG156 76.

**Screens.**

- **2.2 Workspace Settings** (SRS lines 1618-1639): "Create a Department and open a Department." The Departments table (line 1638) lists the five columns; KAN-120 built Name only (D13). Opening a Department from the table is how step 1 reaches 2.3.
- **2.3 Department Detail** (SRS lines 1641-1659), related UCs UC-13, UC-14. "Rename or delete the Department. View the Project Managers of the Department (Managers tab)."

| Item | Rule | Source |
|---|---|---|
| Name \* | "Text, at most 50 characters, unique (MSG43)." | SRS line 1656 |
| Default Project | "Drop-down list of the Active Projects of this Department; optional. Leave it empty for a Department that does not handle support requests ... The Project must have the issue type Task and require no field other than summary and description (MSG157), and the service account must have Create Issues and Modify Reporter in it (MSG156)". | SRS line 1657 |
| Managers tab | "Read-only list of the Project Managers whose Department is this Department, with the Projects each one manages." | SRS line 1658 |
| Delete (button) | "Opens the Delete Department popup." (UC-14, KAN-122) | SRS line 1659 |
| Service account API token (on 2.2) | "Create Issues and Modify Reporter are checked in a default Project when the Admin selects it (UC-13, MSG156)." | SRS line 1637 |

No "Edit" button is listed in the 2.3 field table, although step 2 says "The Admin clicks Edit". The screen images are embedded pictures and were not readable; the Figma file was not opened.

## 2. What already exists (KAN-120)

**Backend, `vn.tsolve.workspace`**

| Piece | Where | What UC-13 does with it |
|---|---|---|
| `DepartmentController`, `@RequestMapping("/api/v1/departments")`, `@Tag(name = "departments")`, POST `createDepartment`, `@PreAuthorize` Admin, `@Valid` | `workspace/controller/DepartmentController.java:20-38` | Add one method, e.g. `@PutMapping("/{departmentId}")` (Q2) |
| `CreateDepartmentRequest`: `@NotBlank(message = "MSG01") @Size(max = 50, message = "MSG02")`, compact constructor turns a Unicode-blank name into `""` so it gets MSG01 (KAN-120 D27) | `workspace/dto/CreateDepartmentRequest.java:6-15` | Same rules for the new name (D7-D9, D22, D27 reuse), Q9 |
| `DepartmentService.create`, Javadoc says in-process callers must validate and check the role | `workspace/service/DepartmentService.java:6-12` | Add `update(UUID, ...)` |
| `DepartmentServiceImpl.create`: `scope.currentWorkspaceId()` first, `strip()`, insert, insert Space, `audit.record(DEPARTMENT_CHANGE, "DEPARTMENT", publicId, {operation: CREATE, name, spaceId})`, one `@Transactional` | `workspace/service/impl/DepartmentServiceImpl.java:28-43` | Same order for update; details in Q7 |
| `DepartmentRepository` (JDBC, `NamedParameterJdbcTemplate`), `insertDepartment` with `ON CONFLICT DO NOTHING` and **no target** so both name indexes arbitrate | `workspace/repository/DepartmentRepository.java:19-32` | `ON CONFLICT` does not exist for `UPDATE`: a taken name raises 23505 (Q5) |
| `insertSpace(departmentId, name)` | same file `:34-39` | Add an `UPDATE space SET name ... WHERE department_id` (Q6) |
| Changeset 027: `DEPARTMENT_CHANGE` added to `ck_audit_log_entry_action`; its rollback keeps the value on purpose (review issue 7) | `db/changelog/changes/027-workspace-audit-department-change.sql` | Reused; **no new changeset** for the audit action |
| Changeset 028: `CREATE UNIQUE INDEX uq_department_name_lower ON department (lower(name))`; `uq_department_name UNIQUE (name)` stays (`002-tenant-project-suggestion.sql:13`) | `028-workspace-department-name-lower-unique.sql:4` | Holds for renames too, including against Deleted rows. A case-only rename of the same row (`it` to `IT`) does not conflict with itself |
| `AuditAction.DEPARTMENT_CHANGE` | `common/audit/AuditAction.java:14` | Reused (KAN-120 D11 planned this) |
| Tests: `DepartmentControllerTest`, `DepartmentServiceImplTest`, `DepartmentCreationIT` (`TwoTenantDatabases`, latch race test `concurrentRequestsCreateOneDepartment_AC_001_10` at `:283`, rollback test `:337`, other Workspace `:360`, no binding `:395`) | `backend/src/test/java/vn/tsolve/workspace/**` | Models for the update tests |

Read side, owned by identity: `GET /api/v1/users/departments`, Admin only (`identity/controller/UserListController.java:50-52`), returns Active Departments only, `ORDER BY lower(name), department_id` (`identity/repository/UserListRepository.java:96-101`), shape `DepartmentRefResponse(UUID departmentId, String name)` (`identity/dto/DepartmentRefResponse.java`). **There is no endpoint that reads one Department**, and none that returns its status or default Project.

**Contract and generated client.** `frontend/openapi/tsolve-api.yaml`: tag `departments` (line 13-14, description "Create a Department and its Space (SRS UC-12)", to widen), path `/api/v1/departments` (line 79), `CreateDepartmentRequest` (line 1762, `minLength: 1, maxLength: 50`), `DepartmentRef`. The contract uses `put:` for every update (lines 162, 216, 1275, 1499) and has no `patch:`. `npm run generate:api` writes `src/shared/api/generated`.

**Frontend, `features/workspace`**

| Piece | Where | Note |
|---|---|---|
| Route 2.3 Department Detail, Admin only, `paths.departmentDetail(id)` = `/workspace-settings/departments/:departmentId` | `routes.ts:19-24`, `shared/routing/paths.ts:18-19` | Already declared |
| `DepartmentDetailPage` | `pages/DepartmentDetailPage.tsx:1-8` | `ScreenPlaceholder` only. Its header comment says "UC-12, UC-13"; the SRS says UC-13, UC-14 |
| `WorkspaceSettingsPage`, Name column is `cells.text(d.name)` | `pages/WorkspaceSettingsPage.tsx:8-10` | Needs `cells.link(paths.departmentDetail(d.departmentId), d.name)` to open a Department, as `review/components/ReviewQueueTable.tsx:14` does |
| `NewDepartmentModal` (`FormModal`, `submitting` ref, `applyApiErrors`, `rules.required` and `rules.maxLength('Department name', 50)`, `count={{ show: true, max: 50 }}`, trims before sending) | `components/NewDepartmentModal.tsx:17-60` | The field and submit logic to copy for the edit form |
| `useAddDepartment` (`handlesOwnErrors`, MSG05, invalidates `getGetUserDepartmentsQueryKey()`) | `hooks/useDepartmentMutations.ts:5-16` | Add `useUpdateDepartment` beside it (Q10) |
| MSW: `POST */api/v1/departments`, local `created` list starting **empty**, case-insensitive MSG43 against it only | `mocks/handlers.ts:6-38` | The master design said the list is seeded with `IT` and `HR` (`04-plans/workspace/design.md` File Structure); the code is not. Harmless, but a PUT mock will not know the ids of the users mock's static Departments either (Section 4) |
| `route-table.test.tsx` asserts exactly 8 popups named in `routes.ts` files (`app/router/route-table.test.tsx:95-101`) | | An edit popup must not be added to the `// Popups` comment (same as KAN-120) |
| Edit-on-a-detail-page model: `UserAccountDetail` (`LoadingState` / `ErrorState` / `SectionCard` + `Descriptions`) and `UserRolesForm` with Cancel and Save | `features/users/components/UserAccountDetail.tsx:13-40`, `UserRolesForm.tsx:120-129` | Pattern for 2.3 |

Query client: `staleTime: 30_000` by default (`shared/api/query-client.ts:66`). Other screens that show a Department name (User List, User Detail, Ticket List through Search) keep a cached copy for up to 30 s (Q10).

**Where the name is read.** Every reader joins `department.name` live: `UserListRepository.java:68,107`, `UserDetailRepository.java:29,112`, Search's `SearchTicket` `@Subselect` (`search/scope/entity/SearchTicket.java:21-26`). No table stores a copy of the Department name, so step 5 "appears on every screen immediately" holds on the server with no extra write. Space names are read live too (`UserDetailRepository.java:56`, `UserRoleUpdateRepository.java:53`). Only past audit entries keep the old name, which is intended.

**Default Project: what exists.** Almost nothing.

- `department.default_project_id BIGINT`, nullable, FK to `project` (`002-tenant-project-suggestion.sql:10,28-30`); the DDL has the same column as `UUID` (`02_tenant.sql:23,65-66`). Nothing reads or writes it.
- `project` has `department_id`, `jira_project_key`, `status` (`ACTIVE`, `REMOVED`, `project/entity/ProjectStatus.java`) and **no name column** (`002-...sql:16-25`); Search shows `jira_project_key` as the project name (`SearchTicket.java:21`).
- No Project can be added in the app: `project-integration` (UC-16, KAN-127) is `decomposed`.
- No Jira call checks issue types, required fields or permissions: `tracker/client/JiraClient.java` has `addComment` and `findComment` only (lines 48, 62). The service account is a configured stand-in until `workspace` stores it in UC-10/UC-11 (`tracker/service/JiraServiceAccount.java:6-10`, `ConfiguredJiraServiceAccount.java:9-13`); KAN-119 is not built.
- No consumer: UC-58 (Chatbot creates an issue) has no story in the Stories file and no code.
- MSG156 and MSG157 are not in `catalog.ts`.

## 3. What UC-13 must add or change

**Backend (inside `workspace/`, no file outside the module's `paths`).**

- `PUT /api/v1/departments/{departmentId}`, operationId `updateDepartment`, Admin only, `@Valid` body `{ "name": "..." }`, 200 with `DepartmentRef` (Q2). Errors: 400 MSG01 or MSG02 on `name`; 401 MSG07; 403 MSG08; 404 MSG04 for an unknown or Deleted Department (Q4); 409 MSG43 on `name`; 500 MSG06.
- Service, one `@Transactional` method: `scope.currentWorkspaceId()`; `strip()`; lock and read the Active row (`SELECT department_id, name FROM department WHERE public_id = :id AND status = 'ACTIVE' FOR UPDATE`, as `UserRoleUpdateRepository.java:32`); no row: `NotFoundException("MSG04", Map.of())` (as `UserDetailServiceImpl.java:47`); same name: Q8; `UPDATE department SET name`; on 23505: `BusinessRuleException("MSG43", Map.of(), "name")` (Q5); `UPDATE space SET name` (Q6); `audit.record(DEPARTMENT_CHANGE, "DEPARTMENT", id, details)` (Q7).
- No changeset: the two name indexes and the audit check already exist. A `version` column is not needed (Q3).
- No outbound call: the Jira checks of step 3 would be one, and `backend/AGENTS.md:144` forbids it inside the transaction. That is one more reason to keep the default Project out (Q1).

**Uniqueness on rename.** Both `uq_department_name` and `uq_department_name_lower` cover Deleted rows (no status filter), so a rename to a Deleted Department's name fails with 23505, as AC-001.8 does for create. The row itself is never a conflict: renaming `it support` to `IT Support` changes the indexed value of the same row only. The design must not add a pre-check that forgets to exclude the row being renamed.

**Trim and length (D7, D8, D9, D22, D27).** Reuse unchanged: strip leading and trailing whitespace with `String.strip()`, keep inner spaces, 50 characters counted as received, blank wins over length, Unicode-blank becomes `""` and gets MSG01. In the browser: `rules.required`, `rules.maxLength('Department name', 50)`, `trim()` before sending.

**Frontend (inside `features/workspace`).**

- 2.2: the Name cell links to 2.3.
- 2.3: replaces the placeholder. Reads the Department (Q11), shows its name with an Edit button, and the edit form with "Department name", Save and Cancel (Q12). On 200: MSG05, refresh the queries (Q10). On 409 MSG43: below the field, form open, text kept. On 404: the not-found state with MSG04.
- MSW `PUT */api/v1/departments/:departmentId` handler; `resetMockData` resets it.
- The contract change first: path, `UpdateDepartmentRequest` (Q9), `getDepartment` if Q11 picks (b); then `generate:api`.

**Spec (Change Mode).** New stories, for example STORY-003 (API) and STORY-004 (2.3 screen), with their own AC ids; the UC-13 line leaves Out of Scope; the Interface Contracts section gains the PUT; `DepartmentService` gains `update`. Re-approval is needed before code (`sdd.ts diff workspace`).

**Behaviours the spec needs to cover** (each testable): rename stored stripped, 200 with the stored name; Space renamed to the same name in the same transaction; one audit entry with before and after; Projects, tickets and status unchanged; empty or whitespace-only gives MSG01; over 50 gives MSG02; name of another Active Department gives MSG43; name of a Deleted Department gives MSG43; case-only difference from another Department gives MSG43; case-only change of its own name is allowed; two Admins renaming two Departments to one name at once give one 200 and one MSG43; two Admins renaming the same Department at once both succeed in order, with the audit `before` of the second equal to the first's `after`; unknown or Deleted Department gives 404 MSG04 and no write; non-Admin 403 MSG08, anonymous 401 MSG07, no bound Workspace 500 MSG06, all with no write; Space or audit failure rolls back the rename (500 MSG06); rename in Workspace A ignores Workspace B's names; the new name in `GET /api/v1/users/departments`; in the web app: Edit, Cancel (nothing sent, old name shown), client MSG01 and MSG02, trimmed body, one request on double click, MSG05 and the new name in 2.3 and 2.2, MSG43 below the field, other errors by code, non-Admin sees MSG08.

## 4. Caveats for the Planner

- **Seeder after a rename.** `MockUserListSeeder` inserts "Customer Support" and "IT Operations" by name on every boot of `identity-mock` (`identity/mock/MockUserListSeeder.java:29-37`). After an Admin renames "Customer Support", the next boot creates a new "Customer Support" with a new Space, and seeded accounts are attached to it by name. Demo profile only; accept and note.
- **Seeder Space names.** The seeder names Spaces `name || ' Wiki'` (`MockUserListSeeder.java:35`). A rename sets the Space name to the Department name exactly (D10), so the suffix disappears on rename. Demo data only.
- **Browser mock mode.** The `users` mock answers `GET /users/departments` from a static list and is tried first (KAN-120 Interim limit 4), so a renamed Department keeps its old name in the table in mock mode, and a PUT mock cannot check MSG43 against the users mock's names without importing them (forbidden by guideline 01). The page tests override the GET for their own case, as AC-002.9's test does.
- **Interim limits 1-3 and 5 of the spec still apply** (Workspace binding under `identity-mock` only, no real sign-in, no CSRF header, one datasource): a browser PUT to the real backend gets 403 until the CSRF quick change (KAN-120 D17).
- **No Department detail read model** exists for KAN-122 (Delete needs "its number of Projects" for MSG45, SRS line 1671) or the Managers tab. Whatever Q11 picks, KAN-122 will extend it.
- **Malformed id in the path.** `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` (`common/exception/GlobalExceptionHandler.java:51`) and has no handler of its own for a type mismatch. Identity maps a non-UUID user id to 404 MSG04 with a regex route (`identity/controller/UserDetailController.java:82-93`). The PUT (and a GET from Q11) should do the same, so `/departments/abc` answers like an unknown id (Q4), and the controller test should cover it.
- **Stacked branch.** Everything above depends on KAN-120's code; KAN-121 D2 already covers the rebase once #202 merges.

## 5. Open questions

Each has a recommendation, so the main session can decide and log it.

**Q1. Does KAN-121 include the default Project?** The SRS (UC-13 steps 2-3, screen 2.3 line 1657, GBR-40) says yes; the story (1 point, criteria rename only) says no. The default Project needs Projects that cannot be created yet (UC-16, `project-integration` `decomposed`), a project name the schema lacks, two Jira checks that have no client code (createmeta and permissions), a stored service account (KAN-119, not built), MSG156 and MSG157 in the catalog, and a consumer (UC-58, no story).
Options: (a) rename only; the default Project goes to Out of Scope with the reason, to be added when UC-16 and UC-58 have stories; (b) store `default_project_id` from a drop-down without the Jira checks; (c) full UC-13 now.
Recommended: (a). (b) stores a value nothing reads and skips two checks the SRS requires; (c) depends on three unbuilt modules and outgrows a 1-point story. The spec should say the Department Detail form has no Default Project field yet, and the main session should flag the gap between the SRS and the story to the user.

**Q2. Endpoint shape.**
Options: (a) `PUT /api/v1/departments/{departmentId}`, body `UpdateDepartmentRequest { name }`, 200 `DepartmentRef`; (b) `PATCH` with optional fields; (c) a sub-resource `PUT /api/v1/departments/{departmentId}/name`.
Recommended: (a). Every update in the contract is a `put` (no `patch` anywhere), `backend/AGENTS.md:103` allows actions as sub-resources but a rename is the resource's own field, and when the default Project arrives it is one more field of the same body.

**Q3. Concurrent edits of the same Department.** No source speaks to it; the codebase has no optimistic locking anywhere (no `@Version`, `If-Match` or `ETag`), and the DDL has no version column.
Options: (a) lock the row with `SELECT ... FOR UPDATE` inside the transaction; the second save waits and wins (last write wins), and its audit `before` is the first's `after`; (b) a `version` column with a 409 on mismatch; (c) no lock.
Recommended: (a). It is the identity precedent (`UserRoleUpdateRepository.java:32`), keeps the audit trail true, needs no changeset (the DDL has no such column, KAN-248 would have to carry it) and no new message (no MSG says "changed by someone else"). (c) can write a wrong `before`. It also serialises a rename with a later UC-14 delete of the same row.

**Q4. Unknown or Deleted Department.** UC-13's precondition is "The Department is Active"; no message is given for the other case.
Options: (a) 404 with MSG04 for both, via `NotFoundException("MSG04", Map.of())`; (b) 404 for unknown, 409 with a new code for Deleted; (c) allow renaming a Deleted Department.
Recommended: (a). A Deleted Department is hidden everywhere (UC-14 step 5), so to the Admin it does not exist; this is how `UserDetailServiceImpl.java:47` answers, and the 404 handler already defaults to MSG04 (`GlobalExceptionHandler.java:71-75`). (c) contradicts the precondition.

**Q5. How a taken name is detected on `UPDATE`.** `ON CONFLICT DO NOTHING` is insert-only.
Options: (a) run the `UPDATE` and turn a `DataIntegrityViolationException` with SQLState 23505 into `BusinessRuleException("MSG43", Map.of(), "name")`, as `UserRoleUpdateRepository.java:164-167` does; (b) a `SELECT` pre-check by `lower(name)` excluding the row, plus (a) for the race; (c) a pre-check only.
Recommended: (a). The two unique indexes are the only names an `UPDATE ... SET name` can violate, so any 23505 means MSG43; it is race-safe and one code path. The failed statement aborts the transaction, but nothing runs after it: the exception rolls back. (c) is racy; (b) is two paths for one rule.

**Q6. Space rename, including a missing Space.** GBR-29 says the Space is renamed together. Every Department created through UC-12 and the seeder has one, but nothing in the schema forces it.
Options: (a) `UPDATE space SET name = :name WHERE department_id = :id` in the same transaction, and treat 0 rows as an unexpected error (500 MSG06, rolled back, logged); (b) create the Space when it is missing; (c) ignore 0 rows.
Recommended: (a). The Space name is exactly the Department name (D10), so it is a plain copy. A missing Space is broken data that should be loud; (b) silently does UC-12's job, (c) silently breaks GBR-29.

**Q7. Audit details.**
Options: (a) `DEPARTMENT_CHANGE`, target `DEPARTMENT` and the Department id, details `{"operation":"UPDATE","before":{"name":"IT"},"after":{"name":"IT Support"},"spaceId":"..."}`; (b) `{"operation":"RENAME","name":"IT Support","previousName":"IT"}`; (c) a new action `DEPARTMENT_RENAME`.
Recommended: (a). `before` and `after` follow `ROLE_CHANGE` (`UserRoleUpdateServiceImpl.java:189-190`), "UPDATE" matches the UC name, and a later default Project fits in the same two objects. (c) needs a new check-constraint changeset, which KAN-120 D11 avoided on purpose.

**Q8. Save with no change.** The Admin saves the same name, or the same name with spaces around it.
Options: (a) 200 with the current Department, no `UPDATE`, no audit entry; (b) write and audit anyway; (c) 400.
Recommended: (a). The audit log records changes, and an entry whose `before` equals `after` says nothing. A case-only change (`it` to `IT`) is a change and is saved. The web app still shows MSG05, since the SRS gives no other message.

**Q9. Request DTO.**
Options: (a) a new record `UpdateDepartmentRequest` with the same annotations and compact constructor as `CreateDepartmentRequest`, and its own contract schema; (b) reuse `CreateDepartmentRequest` for the PUT body; (c) rename the record to a shared `DepartmentNameRequest`.
Recommended: (a). `backend/AGENTS.md:95` names DTOs `<Action><Entity>Request`; the copy is eight lines; when the default Project arrives only the update body grows. (c) churns KAN-120's code and generated client for no behaviour.

**Q10. Refreshing other screens.** UC-13 step 5: "The new name appears on every screen immediately." The server side holds (every reader joins live). In the browser, other queries keep a cached name up to 30 s.
Options: (a) on success, invalidate every query (`client.invalidateQueries()`); (b) invalidate the Departments list and the Department only; (c) (b) plus a list of known query keys from other features.
Recommended: (a). One line, refetches only the queries that are mounted, and needs no knowledge of other features' keys (guideline 01). Other users' browsers see it at their next fetch, which is what "immediately" can mean without push.

**Q11. How 2.3 reads the Department.**
Options: (a) a new `GET /api/v1/departments/{departmentId}` in `workspace`, Admin only, returning `DepartmentRef` (Active only, 404 MSG04 otherwise); (b) find the Department in the cached `GET /api/v1/users/departments` list; (c) pass the name through router state from 2.2.
Recommended: (a). A deep link or reload must work, the module owning Departments should serve its own read, and KAN-122 (Project count for MSG45) and the Managers tab will extend it. (b) ties a workspace screen tighter to identity's User List filter endpoint; (c) breaks on reload. If the main session wants the smallest diff, (b) works today because the list is small and Active-only.

**Q12. Edit form on 2.3.**
Options: (a) inline on the page: the name read-only with an Edit button; Edit shows the "Department name" field (prefilled), Save and Cancel; (b) a `FormModal` "Edit Department" opened by Edit, copying `NewDepartmentModal`; (c) rename from a row action on 2.2, skipping 2.3.
Recommended: (a). Screen 2.3 lists "Name \*" as a field of the screen, not of a popup; the SRS popup list has no edit popup (and `route-table.test.tsx` counts 8 popups); step 1 says the Admin opens the Department Detail. The submit logic is copied from `NewDepartmentModal` (submitting ref, `applyApiErrors`, trim). (c) skips step 1. The Delete button and Managers tab stay out (KAN-122; no story owns the tab), Q13.

**Q13. What else of 2.3 this story builds.**
Options: (a) only the name, Edit, Save, Cancel and a link back to 2.2; (b) also the Managers tab, read-only; (c) also the Delete button, disabled.
Recommended: (a). UC-13 needs only the name. The Managers tab needs a new read of Project Managers and their Projects with no AC in UC-13; Delete is KAN-122. Same reasoning as KAN-120 D13.

## 6. Codex cross-check

Not done. Codex CLI 0.167.0 was started read-only and ephemeral from the repo root with the prompt in the session scratchpad (`kan121-scout-codex.txt`); it stopped at once with exit 1, "You’ve hit your usage limit ... try again at 2:33 AM". It read nothing and added nothing. The main session can re-run the same prompt once the limit resets, before the Planner's builder check.

In its place this scout re-checked three likely blind spots itself: how a malformed path id is answered (added to section 4), whether any table stores a copy of the Department or Space name (none; section 2), and whether any optimistic-locking precedent exists (none; Q3).
