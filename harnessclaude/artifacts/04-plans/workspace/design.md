---
module: workspace
date: 2026-11-11
---

# Design: workspace

Spec: `artifacts/03-modules/workspace/spec.md`. Today the module holds UC-12 (KAN-120) and the UC-13 rename (KAN-121); later UCs extend this file through Change Mode. Facts about the existing code: `iteration-1/kan-120-scout.md`, `iteration-1/kan-121-scout.md`. Decisions: `iteration-1/kan-120-decisions.md`, `iteration-1/kan-121-decisions.md`. In this file KAN-121's decisions are written `K121-D6` and so on, since both logs start at D1.

## Architecture Overview

**Backend** (`vn.tsolve.workspace`), layered as `backend/AGENTS.md` asks:

```
POST /api/v1/departments
  DepartmentController     @PreAuthorize Admin, @Valid body, 201
    DepartmentService      interface
      DepartmentServiceImpl  @Transactional
        scope.currentWorkspaceId()     no bound Workspace: throws, 500 MSG06
        name = request.name().strip()
        repository.insertDepartment    no row returned: BusinessRuleException("MSG43", Map.of(), "name")
        repository.insertSpace
        audit.record(DEPARTMENT_CHANGE, "DEPARTMENT", departmentId, details)
        return DepartmentRefResponse
```

`common/audit/AuditService.record` joins the service's transaction, so a failure in any of the three writes rolls back all of them. Nothing leaves the database, so no event or job is needed.

The service body, in full:

```java
@Override
@Transactional
public DepartmentRefResponse create(CreateDepartmentRequest request) {
    scope.currentWorkspaceId();
    String name = request.name().strip();
    DepartmentRepository.Created department = repository
            .insertDepartment(name)
            .orElseThrow(() -> new BusinessRuleException("MSG43", Map.of(), "name"));
    UUID spaceId = repository.insertSpace(department.id(), name);
    audit.record(
            AuditAction.DEPARTMENT_CHANGE,
            "DEPARTMENT",
            department.publicId(),
            Map.of("operation", "CREATE", "name", name, "spaceId", spaceId));
    return new DepartmentRefResponse(department.publicId(), name);
}
```

The repository uses `NamedParameterJdbcTemplate`, as `UserCreationRepository` does:

```sql
-- insertDepartment: zero rows means the name is taken
INSERT INTO department (name) VALUES (:name)
ON CONFLICT DO NOTHING
RETURNING department_id, public_id;

-- insertSpace
INSERT INTO space (department_id, name) VALUES (:departmentId, :name)
RETURNING public_id;
```

`ON CONFLICT DO NOTHING` has **no target on purpose**. `department` will hold two unique indexes on the name: `uq_department_name` and the new `uq_department_name_lower`. With a target, the other index is not an arbiter, so a request racing past the first check raises a unique violation and answers 500 instead of MSG43. Without a target every unique index is an arbiter. `status` and `default_project_id` are left to their defaults (`ACTIVE`, NULL), and the same for `space.status`.

**Web app** (`features/workspace`): `WorkspaceSettingsPage` reads Departments with `useGetUserDepartments`, shows a Name-only `DataTable`, and opens `NewDepartmentModal`. The modal copies `features/review/components/RejectTicketModal.tsx`: `FormModal`, a `submitting` ref, `applyApiErrors`, then `showMessage(error.code, error.params)`. `hooks/useDepartmentMutations.ts` follows `useAddUser`: `handlesOwnErrors`, invalidate `getGetUserDepartmentsQueryKey()`, `showMessage('MSG05')`. The Add User form reads the same query, which satisfies UC-12 step 7.

Field `name` is the API property name. The label is "Department name". The modal sends `{ name: values.name.trim() }`. Button "New Department", title "New Department", field max 50 with `count={{ show: true, max: 50 }}`, no native `maxLength`.

### KAN-121: read and rename (STORY-003, STORY-004)

```
GET /api/v1/departments/{departmentId}
  DepartmentController.get      @PreAuthorize Admin, 200
    DepartmentServiceImpl.get   @Transactional(readOnly = true)
      scope.currentWorkspaceId()                 AC-003.21
      repository.findActive(id)                  empty: NotFoundException("MSG04", Map.of())  AC-003.2

PUT /api/v1/departments/{departmentId}
  DepartmentController.update   @PreAuthorize Admin, @Valid UpdateDepartmentRequest, 200
    DepartmentServiceImpl.update  @Transactional
      scope.currentWorkspaceId()                 AC-003.21
      name = request.name().strip()
      repository.lockActive(id)                  empty: NotFoundException MSG04 (AC-003.2); row lock (K121-D7)
      name.equals(current.name()): return        AC-003.14, no write (K121-D12)
      repository.renameDepartment(rowId, name)   23505: BusinessRuleException("MSG43", Map.of(), "name") (K121-D9)
      repository.renameSpace(rowId, name)        no row: IllegalStateException, 500 MSG06 (K121-D10)
      audit.record(DEPARTMENT_CHANGE, "DEPARTMENT", id, details)   (K121-D11)
      return DepartmentRefResponse(id, name)

/api/v1/departments/{not a UUID}  GET, PUT    regex route, @PreAuthorize Admin, NotFoundException MSG04 (K121-D8)
```

The service body of `update`:

```java
@Override
@Transactional
public DepartmentRefResponse update(UUID departmentId, UpdateDepartmentRequest request) {
    scope.currentWorkspaceId();
    String name = request.name().strip();
    DepartmentRepository.Current current = repository
            .lockActive(departmentId)
            .orElseThrow(() -> new NotFoundException("MSG04", Map.of()));
    if (name.equals(current.name())) {
        return new DepartmentRefResponse(departmentId, name);
    }
    repository.renameDepartment(current.id(), name);
    UUID spaceId = repository.renameSpace(current.id(), name);
    audit.record(
            AuditAction.DEPARTMENT_CHANGE,
            "DEPARTMENT",
            departmentId,
            Map.of(
                    "operation", "UPDATE",
                    "before", Map.of("name", current.name()),
                    "after", Map.of("name", name),
                    "spaceId", spaceId));
    return new DepartmentRefResponse(departmentId, name);
}
```

Repository additions (`record Current(long id, String name)`):

```sql
-- findActive (GET)
SELECT public_id, name FROM department WHERE public_id = :id AND status = 'ACTIVE';

-- lockActive (PUT): the second of two saves waits here and reads the first's name (AC-003.16)
SELECT department_id, name FROM department WHERE public_id = :id AND status = 'ACTIVE' FOR UPDATE;

-- renameDepartment: DataIntegrityViolationException with SQLState 23505 becomes MSG43 on name
UPDATE department SET name = :name WHERE department_id = :id;

-- renameSpace: space.department_id is UNIQUE, so 0 or 1 row; 0 rows is broken data
UPDATE space SET name = :name WHERE department_id = :id RETURNING public_id;
```

The 23505 check copies `UserRoleUpdateRepository.java:164-167` (`getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState())`); any other failure is rethrown. No pre-check by name: the unique indexes `uq_department_name` and `uq_department_name_lower` cover Deleted rows, so AC-003.10 to AC-003.12 and the race of AC-003.15 come from them, and a case-only change of the row's own name never conflicts with itself (AC-003.13). The failed statement aborts the transaction, but nothing runs after the exception, which rolls back.

`UpdateDepartmentRequest` copies `CreateDepartmentRequest` exactly: `@NotBlank(message = "MSG01") @Size(max = 50, message = "MSG02")` and the compact constructor that turns a Unicode-blank name into `""` (KAN-120 D27). KAN-120 D19 holds for the PUT too: `@Valid` runs before `@PreAuthorize`, so a non-Admin's invalid body gets 400; AC-003.18 says "with an allowed name".

The regex route copies `UserDetailController.java:30` (`UUID_PATTERN`) and `:82-93`: one method mapped to `/{invalidDepartmentId:(?!<UUID_PATTERN>$).+}` for GET and PUT, Admin only, throwing `NotFoundException("MSG04", Map.of())`. It is disjoint from the UUID routes, so the POST on the collection is untouched.

**Web app.** `WorkspaceSettingsPage`'s Name cell becomes `cells.link(paths.departmentDetail(d.departmentId), d.name)`, as `review/components/ReviewQueueTable.tsx:14`. `DepartmentDetailPage` reads `departmentId` with `useParams`, calls the generated `useGetDepartment`, and follows `users/components/UserAccountDetail.tsx:13-40`: `LoadingState` while pending, `ErrorState` with `error` and `onRetry` on error (MSG04 for 404 comes from the code), otherwise a link "Back to Workspace Settings" (`paths.workspaceSettings`) and a `SectionCard` "Department Detail" holding `DepartmentNameForm`. That component shows the name as a `Descriptions` item "Name" and an "Edit" button; Edit shows an antd `Form` with the `FormField` "Department name" (`rules.required('Department name')`, `rules.maxLength('Department name', 50)`, `<Input count={{ show: true, max: 50 }} />`, initial value the current name), Save (`htmlType="submit"`, `loading` while pending) and Cancel. Submit copies `NewDepartmentModal`: a `submitting` ref, `{ name: values.name.trim() }`, `applyApiErrors(form, error)` or `showMessage(error.code, error.params)`; on success it leaves edit mode. Cancel does nothing while `submitting.current` (AC-004.13), otherwise leaves edit mode and resets the form. `useRenameDepartment` in `hooks/useDepartmentMutations.ts` wraps `useUpdateDepartment` with `handlesOwnErrors`, `showMessage('MSG05')` and `client.invalidateQueries()` with no key (K121-D14); the refetched `getDepartment` shows the stored name.

Decided in planning: the header comment of `DepartmentDetailPage.tsx` becomes "UC-13, UC-14" (the SRS's related UCs for 2.3).

Decided in planning: feature keys `rename-department-api` (Task 3) and `rename-department-screen` (Task 4).

Decided in planning: the contract lists 400, 401, 403, 404 and 409 for `updateDepartment`, and 401, 403 and 404 for `getDepartment`; 500 MSG06 is not listed, as for `createDepartment`. The path parameter is `departmentId`, `format: uuid`.

## Data Model

No new table or column. Writes go to the existing `department`, `space` and `audit_log_entry` tables. The Workspace is the database itself (ADR-0002), so a table-wide unique index means unique in the Workspace, and a Deleted Department keeps its row, so it stays reserved.

Two changesets, appended after `026-identity-app-user-keycloak-sub.sql` in `db.changelog-master.xml`. `027` is the next free number on disk.

`backend/src/main/resources/db/changelog/changes/027-workspace-audit-department-change.sql`:

```sql
--liquibase formatted sql

--changeset workspace:027-workspace-audit-department-change
ALTER TABLE audit_log_entry DROP CONSTRAINT ck_audit_log_entry_action;
ALTER TABLE audit_log_entry ADD CONSTRAINT ck_audit_log_entry_action CHECK (action IN (
    'APPROVE', 'REJECT', 'REQUEST_CHANGES', 'PUBLISH', 'UNPUBLISH', 'IMPORT', 'DELETE',
    'ROLE_CHANGE', 'PERMISSION_CHANGE', 'TRACKER_CONFIGURATION_CHANGE', 'DEPARTMENT_CHANGE'
));

--rollback ALTER TABLE audit_log_entry DROP CONSTRAINT ck_audit_log_entry_action;
--rollback ALTER TABLE audit_log_entry ADD CONSTRAINT ck_audit_log_entry_action CHECK (action IN ('APPROVE', 'REJECT', 'REQUEST_CHANGES', 'PUBLISH', 'UNPUBLISH', 'IMPORT', 'DELETE', 'ROLE_CHANGE', 'PERMISSION_CHANGE', 'TRACKER_CONFIGURATION_CHANGE'));
```

`backend/src/main/resources/db/changelog/changes/028-workspace-department-name-lower-unique.sql`:

```sql
--liquibase formatted sql

--changeset workspace:028-workspace-department-name-lower-unique
CREATE UNIQUE INDEX uq_department_name_lower ON department (lower(name));

--rollback DROP INDEX uq_department_name_lower;
```

`uq_department_name` stays. The check on `audit_log_entry` was last set in `018-audit-log-entry.sql`; no later changeset touches it.

KAN-121 adds no changeset: the two name indexes and the `DEPARTMENT_CHANGE` audit value already exist, and no version column is needed (K121-D7). `department.status` and `default_project_id` are not touched by a rename (AC-003.6).

`DEPARTMENT_CHANGE` is added in four places together: `AuditAction.java`, changeset 027, the `AuditAction` enum in the contract, and `ACTION_LABELS` in `audit-filters.ts` (label "Department change").

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| D4 Spec scope | UC-12 only; other UCs through Change Mode | Whole module now | The request is KAN-120. KAN-119 and KAN-121 to KAN-123 add their stories when they start |
| D5 Depth | Normal, with edge cases and builder check | Deep with risk review | Two rows in existing tables and one admin form; no new tenancy, auth or personal-data mechanism |
| D6 Source | SRS copy in `Sua_31-10` | Ask for another copy | The only copy whose numbering matches Jira. The stale manifest path is a separate fix |
| D7 Same name | Case-insensitive, unique index on `lower(name)` | Case-sensitive, existing constraint only | The only document on this point says so, and the index holds under concurrent requests |
| D8 Whitespace | Strip leading and trailing | Keep as typed | Matches Add User's email; whitespace-only is already empty under MSG01 |
| D9 Length | 50 with MSG02 | 100 | Screen 2.2 and guideline 09 say 50; no schema change |
| D10 Space name | The Department name | Name + " Wiki" | The only documented behaviour; the seeder value is demo data |
| D11 Audit action | New `DEPARTMENT_CHANGE` with `details.operation` | `DEPARTMENT_CREATE`; reuse an existing value | Matches the `*_CHANGE` names and UC-13 and UC-15 reuse it; an existing value would make the log false |
| D12 Files outside `paths` | Add the single files to `module.md` `paths` | Separate quick change | D11 cannot compile without them; `module.md` is not the spec |
| D13 Screen 2.2 scope | Button, form, Name-only table | Button and form only; all five columns | The Admin must see the result; the existing endpoint gives Active Departments |
| D14 Form type | `FormModal`, title "New Department" | Inline form | The kit's pattern for a small form; closing it is the Cancel flow. No source gives the title |
| D15 Persistence | JDBC repository | JPA entity on a BIGSERIAL key | `ON CONFLICT DO NOTHING` answers a duplicate without a failed transaction, and KAN-248 has no entity to redo. **Deliberate deviation from `backend/AGENTS.md` section 3** |
| D16 KAN-248 | Build on today's tables | Wait | The API uses only `public_id`, so KAN-248 touches only this repository's SQL |
| D17 CSRF header | Left out of KAN-120 | Quick change first; inside this commit | It affects every write, `shared/` needs the FE Lead's review, and real sign-in is not on `main` either |
| D19 Invalid body from a non-Admin | Keep `@Valid` on the controller; such a request gets 400, an allowed name gets 403 | Raw body parsed in the service, as `UserCreationController` does | `@Valid` runs before `@PreAuthorize`. The body rules are public in the contract and nothing is written either way, so `backend/AGENTS.md` section 3 is followed |
| D20 Code that assumes case-variant names | Change the `UserListIT` fixture and the `MockUserListSeeder` conflict clause | Case-insensitive check in code only | The `lower(name)` index (D7) rejects the fixture's two case-variant rows and can stop the demo profile at boot |
| D21 `ACTION_LABELS` entry | In Task 1 | In Task 2 | `Record<AuditAction, string>` does not compile once Task 1 regenerates the client, and each commit must pass `typecheck` |
| D22 Whitespace and character | What `String.strip()` and `String.length()` do on the server; `trim()` and antd's count in the browser | One Unicode-aware rule shared by both sides | No source defines them. The sides differ only for non-breaking spaces and emoji, and a server refusal still shows below the field |
| K121-D6 Endpoint | `PUT /api/v1/departments/{departmentId}`, 200 `DepartmentRef` | `PATCH`; `PUT .../name` | Every update in the contract is a `put`; the name is the resource's own field |
| K121-D7 Same Department saved twice at once | `SELECT ... FOR UPDATE`, last write wins | `version` column with 409; no lock | Identity precedent; true audit `before`; no changeset or new message |
| K121-D8 Unknown, Deleted or malformed id | 404 MSG04; regex route for non-UUID ids | 409 for Deleted; rename Deleted | Precondition "The Department is Active"; `UserDetailController` precedent; `common/` untouched |
| K121-D9 Taken name on `UPDATE` | Catch 23505, MSG43 on `name` | Pre-check plus catch; pre-check only | One race-safe path; the row never conflicts with itself |
| K121-D10 Space rename | Same transaction; 0 rows is 500 MSG06 | Create the Space; ignore 0 rows | GBR-29; a missing Space is broken data |
| K121-D11 Audit details | `operation UPDATE`, `before`, `after`, `spaceId` | `RENAME` with `previousName`; new action | `ROLE_CHANGE` shape; no changeset |
| K121-D12 No change | 200, no write, no audit | Write anyway; 400 | An entry with `before` equal to `after` says nothing |
| K121-D13 Request DTO | New `UpdateDepartmentRequest` | Reuse the create record; shared record | DTO naming rule; only the update body grows later |
| K121-D14 Other screens | `invalidateQueries()` with no key | Departments keys only; other features' keys | One line; no cross-feature knowledge |
| K121-D15 How 2.3 reads | New `getDepartment` in `workspace` | Cached `users/departments` list; router state | Deep link and reload work; KAN-122 extends it |
| K121-D16 Edit form | Inline on 2.3 | `FormModal`; row action on 2.2 | "Name *" is a field of 2.3; no edit popup in the SRS |
| K121-D17 Rest of 2.3 | Name, Edit, Save, Cancel, back link | Managers tab; disabled Delete | Only the name has criteria |
| K121-D18 MSW | Seed the `workspace` list with copies of the `users` mock's IT and HR ids and names | Own list only; no mock | Mock mode opens 2.3 from 2.2 without importing another feature |
| K121-D20 Seeder after a rename | Accept and note | Change the seeder | Demo profile only |

Decided in planning: `DepartmentRefResponse` from `identity/dto` is reused for the 201 body, since `workspace` already depends on `identity` and the contract's `DepartmentRef` has the same shape. `TenantScope` is imported from `identity/signin/port` as `review` already does; it is the interim port until the tenant layer lands.

Decided in planning: the audit label is "Department change", following the `Role change` pattern. Feature keys are `create-department-api` and `create-department-screen`.

Decided in planning: the contract lists 400, 401, 403 and 409 for this operation, as `createUser` does; 500 MSG06 is the generic answer of `GlobalExceptionHandler` and is not listed (builder check).

Decided in planning: AC-001.11 is covered at three levels. The IT clears the security context so `AuditService` throws after both inserts and asserts rollback; the service test makes `insertSpace` throw and asserts no audit call; the controller test makes the service throw and asserts 500 MSG06 (builder check).

Decided in planning: the Add User half of AC-002.9 rests on the shared query key. The page test asserts a second `GET /users/departments` after the POST; no separate Add User test (builder check).

Decided in planning: do not add the popup to the `// Popups of this feature` comment in `features/workspace/routes.ts`. It is not in the SRS popup list, and `route-table.test.tsx` counts exactly 8.

## File Structure

```
backend/src/main/java/vn/tsolve/
  workspace/controller/DepartmentController.java        new
  workspace/service/DepartmentService.java              new
  workspace/service/impl/DepartmentServiceImpl.java     new
  workspace/repository/DepartmentRepository.java        new (with record Created(long id, UUID publicId))
  workspace/dto/CreateDepartmentRequest.java            new
  common/audit/AuditAction.java                         + DEPARTMENT_CHANGE
  identity/mock/MockUserListSeeder.java                 ON CONFLICT (name) becomes ON CONFLICT
backend/src/main/resources/db/changelog/
  changes/027-workspace-audit-department-change.sql     new
  changes/028-workspace-department-name-lower-unique.sql new
  db.changelog-master.xml                               two includes at the end
backend/src/test/java/vn/tsolve/
  workspace/controller/DepartmentControllerTest.java    new
  workspace/service/impl/DepartmentServiceImplTest.java new
  workspace/DepartmentCreationIT.java                   new
  identity/UserListIT.java                              fixture name only
frontend/openapi/tsolve-api.yaml                        tag, path, CreateDepartmentRequest, enum value
frontend/src/shared/api/generated/**                    regenerated
frontend/src/features/audit-log/hooks/
  audit-filters.ts                                      + label
  audit-filters.test.ts                                 + one test
frontend/src/features/workspace/
  pages/WorkspaceSettingsPage.tsx                       replaces the placeholder
  pages/WorkspaceSettingsPage.test.tsx                  new
  components/NewDepartmentModal.tsx                     new
  hooks/useDepartmentMutations.ts                       new
  mocks/handlers.ts                                     POST handler
```

KAN-121 adds:

```
backend/src/main/java/vn/tsolve/workspace/
  controller/DepartmentController.java          + get, update, invalidId
  service/DepartmentService.java                + get, update
  service/impl/DepartmentServiceImpl.java       + get, update
  repository/DepartmentRepository.java          + Current, findActive, lockActive, renameDepartment, renameSpace
  dto/UpdateDepartmentRequest.java              new
backend/src/test/java/vn/tsolve/workspace/
  controller/DepartmentControllerTest.java      + read and rename cases
  service/impl/DepartmentServiceImplTest.java   + read and rename cases
  DepartmentRenameIT.java                       new
frontend/openapi/tsolve-api.yaml                tag text, path /api/v1/departments/{departmentId} (get, put), UpdateDepartmentRequest
frontend/src/shared/api/generated/**            regenerated
frontend/src/features/workspace/
  pages/WorkspaceSettingsPage.tsx               Name cell links to 2.3
  pages/WorkspaceSettingsPage.test.tsx          + AC-004.1
  pages/DepartmentDetailPage.tsx                replaces the placeholder
  pages/DepartmentDetailPage.test.tsx           new
  components/DepartmentNameForm.tsx             new
  hooks/useDepartmentMutations.ts               + useRenameDepartment
  mocks/handlers.ts                             seeded list, GET and PUT handlers
```

All inside `module.md` `paths`; none added. `DepartmentRenameIT` copies the class annotations of `DepartmentCreationIT` so it shares the cached Spring context; fixtures use a random prefix.

MSW for KAN-121 (K121-D18): the local list starts with `{ departmentId: '00000000-0000-4000-8000-0000000000d1', name: 'IT' }` and `{ departmentId: '00000000-0000-4000-8000-0000000000d2', name: 'HR' }`, copied from `users/mocks/data.ts:6-7`, and `resetMockData` restores it. GET answers the Department or 404 MSG04. PUT applies the rules of the POST, answers 404 MSG04 for an unknown id, 409 MSG43 for another entry's name ignoring case, and otherwise stores and answers 200. The 2.2 table still comes from the `users` mock, so it shows the old name in browser mock mode (spec Interim limit 6). Seeding IT and HR also makes the POST mock answer MSG43 for them; the existing `WorkspaceSettingsPage.test.tsx` cases must be checked for a name that collides.

Seeder after a rename (K121-D20): under `identity-mock`, renaming "Customer Support" makes the next boot insert a fresh "Customer Support" with a new Space, and seeded accounts attach to it by name; the renamed Space loses the " Wiki" suffix the seeder gives. Demo data only.

The `UserListIT` fixture: `'list fixture alpha'` becomes `'list fixture beta'`; the assertion order stays.

`DepartmentCreationIT` copies the class annotations of `UserListIT` exactly (`@SpringBootTest` properties, `@AutoConfigureMockMvc`, `@Import(TwoTenantDatabases.class)`, `@WithTSolveUser(roles = Role.ADMIN)`) so it shares that cached Spring context, adds no `@MockBean`, and starts no container. Fixtures use a random prefix and are cleaned as `UserCreationIT` does.

MSW: `workspace/mocks/handlers.ts` answers POST with 201, or 409 MSG43 for a name already held case-insensitively in a small local list seeded with `IT` and `HR` (the names the users mock serves, not imported from it), and `resetMockData` restores the list. `users` answers `GET /users/departments` from a static list and is tried first, so in browser mock mode a new Department shows MSG05 but does not appear in the table. The page test overrides that GET for its own case. Changing `FEATURE_MOCKS` is forbidden by its own comment, and importing another feature's mock data by guideline 01.

## External Dependencies
None new.

## Edge Cases & Risks

Dimensions analyzed: User types, Input extremes, Timing, Scale, State transitions, Error cascades, Authorization, Data integrity, Integration, Compliance · Skipped: Environment (`FormModal` and `DataTable` carry the labels, focus and keyboard behaviour), Business logic (no amounts, quotas or limits beyond the name rules)

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | Authorization | Signed-in non-Admin sends an allowed name | Critical | 403 MSG08, no rows (AC-001.12) | Task 1 |
| 2 | STORY-001 | Authorization | No signed-in user | High | 401 MSG07, service not called (AC-001.13) | Task 1 |
| 3 | STORY-001 | Authorization | Non-Admin sends an invalid body | Low | 400 from `@Valid`, no rows (D19) | none |
| 4 | STORY-001 | Authorization | Real browser POST has no CSRF header | Medium | 403 until the quick change (D17) | none |
| 5 | STORY-001 | Input extremes | Name missing, `""`, spaces only, or 51 spaces | High | 400 MSG01 on `name` (AC-001.5) | Task 1 |
| 6 | STORY-001 | Input extremes | Exactly 50 characters, then 51 | High | 201, then 400 MSG02 with `max_length` 50 (AC-001.1, AC-001.6) | Task 1 |
| 7 | STORY-001 | Input extremes | `"  IT  "` when `IT` exists | High | Stripped first, 409 MSG43 (AC-001.7) | Task 1 |
| 8 | STORY-001 | Input extremes | SQL text in the name | Medium | Stored as text through named parameters | none |
| 9 | STORY-001 | Data integrity | Name of a `DELETED` Department | High | 409 MSG43 (AC-001.8) | Task 1 |
| 10 | STORY-001 | Data integrity | Case-only difference, ASCII and Vietnamese (`Phòng IT`, `PHÒNG IT`) | High | 409 MSG43 (AC-001.9) | Task 1 |
| 11 | STORY-001 | Data integrity | Changeset 028 meets case-variant rows already stored | Medium | Liquibase stops at start; fix the data and restart. No production Workspace exists yet | none |
| 12 | STORY-001 | Data integrity | Admin creates `customer support`, then the demo profile restarts | Medium | Seeder's clause has no target, so the boot continues (D20) | none |
| 13 | STORY-001 | Timing | Two requests with one name at once | High | One 201, one 409 MSG43, one Department, Space and audit entry (AC-001.10) | Task 1 |
| 14 | STORY-001 | Error cascades | Audit write fails after both inserts | Critical | All rolled back, 500 MSG06 (AC-001.11) | Task 1 |
| 15 | STORY-001 | State transitions | Workspace not bound | High | 500 MSG06, no rows (AC-001.15) | Task 1 |
| 16 | STORY-001 | State transitions | Space is created with the Department | Critical | One Active Space, same name, no `space_manager` row (AC-001.2) | Task 1 |
| 17 | STORY-001 | User types | Same name in another Workspace | Critical | Allowed; the row is only in the bound database (AC-001.14) | Task 1 |
| 18 | STORY-001 | Compliance | Successful create | High | One audit entry with actor, action, target and details (AC-001.3) | Task 1 |
| 19 | STORY-001 | Compliance | Refused create (400, 403, 409) | High | No audit entry (AC-001.5 to AC-001.9, AC-001.12) | Task 1 |
| 20 | STORY-001 | Integration | `DEPARTMENT_CHANGE` missing from one of the four places | High | The IT write fails at the check; `typecheck` fails on the label (AC-001.16) | Task 1 |
| 21 | STORY-001 | Integration | New Department in the Add User list | High | `GET /users/departments` returns it (AC-001.4) | Task 1 |
| 22 | STORY-002 | Timing | Save clicked twice | High | One request; button busy (AC-002.8) | Task 2 |
| 23 | STORY-002 | Timing | Cancel while the request runs | Medium | Ignored until it settles, as `RejectTicketModal` (AC-002.14) | Task 2 |
| 24 | STORY-002 | Input extremes | Empty, spaces only, 51 characters in the browser | High | MSG01 or MSG02 below the field, no request, text kept (AC-002.6, AC-002.7) | Task 2 |
| 25 | STORY-002 | Input extremes | Name with spaces around it | High | Trimmed in the request (AC-002.8) | Task 2 |
| 26 | STORY-002 | Error cascades | 409 MSG43 from the server | High | Below the field, popup open, text kept (AC-002.10) | Task 2 |
| 27 | STORY-002 | Error cascades | 403 or 500 from the server | High | Message by code, popup open, text kept (AC-002.11) | Task 2 |
| 28 | STORY-002 | Error cascades | The Departments query fails | High | Error state with retry; button stays (AC-002.3) | Task 2 |
| 29 | STORY-002 | State transitions | Cancel, then reopen | High | No request, empty field (AC-002.12) | Task 2 |
| 30 | STORY-002 | State transitions | Success | High | Popup closed, MSG05, new row in the table (AC-002.9) | Task 2 |
| 31 | STORY-002 | Scale | No Department | Medium | MSG04 (AC-002.4) | Task 2 |
| 32 | STORY-002 | Scale | Many Departments | Low | The endpoint is unpaged and the table lists all; a Workspace has few Departments | none |
| 33 | STORY-002 | User types | Non-Admin opens the page | High | MSG08 page, no button (AC-002.13) | Task 2 |
| 34 | STORY-002 | Integration | Browser mock mode after a create | Low | MSG05 shows, the table does not change (see File Structure) | none |
| 35 | STORY-003 | Authorization | Non-Admin reads, or renames with an allowed name | Critical | 403 MSG08, no change (AC-003.18) | Task 3 |
| 36 | STORY-003 | Authorization | No signed-in user | High | 401 MSG07, service not called (AC-003.19) | Task 3 |
| 37 | STORY-003 | Authorization | Non-Admin sends an invalid body | Low | 400 from `@Valid`, no change (KAN-120 D19) | none |
| 38 | STORY-003 | Input extremes | Name missing, `""`, spaces only, 51 spaces, Unicode-blank | High | 400 MSG01 on `name` (AC-003.8) | Task 3 |
| 39 | STORY-003 | Input extremes | Exactly 50 characters, then 51 | High | 200, then 400 MSG02 with `max_length` 50 (AC-003.3, AC-003.9) | Task 3 |
| 40 | STORY-003 | Input extremes | Path id `abc` | High | 404 MSG04 for GET and PUT, not 400 or 500 (AC-003.2) | Task 3 |
| 41 | STORY-003 | Input extremes | `"  IT  "` sent for the Department already named `IT` | Medium | Stripped, equal: 200, no write, no audit (AC-003.14) | Task 3 |
| 42 | STORY-003 | Data integrity | Rename to another Active name, padded with spaces | High | 409 MSG43, old name kept, no audit (AC-003.10) | Task 3 |
| 43 | STORY-003 | Data integrity | Rename to a Deleted Department's name | High | 409 MSG43 (AC-003.11) | Task 3 |
| 44 | STORY-003 | Data integrity | Rename to `PHÒNG IT` when another is `Phòng IT` | High | 409 MSG43 (AC-003.12) | Task 3 |
| 45 | STORY-003 | Data integrity | Rename own `it support` to `IT Support` | High | 200, stored, Space renamed, audited (AC-003.13) | Task 3 |
| 46 | STORY-003 | State transitions | Rename a Deleted or unknown Department | High | 404 MSG04, no change (AC-003.2) | Task 3 |
| 47 | STORY-003 | State transitions | Rename keeps status, default Project and Projects | High | Columns and `project` rows unchanged (AC-003.6) | Task 3 |
| 48 | STORY-003 | Timing | Two renames of two Departments to one name at once | High | One 200, one 409 MSG43, one audit entry (AC-003.15) | Task 3 |
| 49 | STORY-003 | Timing | Two renames of the same Department at once | High | Both 200 in order, second `before` equals first `after` (AC-003.16) | Task 3 |
| 50 | STORY-003 | Error cascades | Department has no Space, or the audit write fails | Critical | Old name kept, 500 MSG06 (AC-003.17) | Task 3 |
| 51 | STORY-003 | State transitions | No Workspace bound | High | 500 MSG06, no change (AC-003.21) | Task 3 |
| 52 | STORY-003 | User types | Name held only in another Workspace | Critical | Allowed; only the bound database changes (AC-003.20) | Task 3 |
| 53 | STORY-003 | Compliance | Successful rename | High | One audit entry with `before`, `after` and `spaceId`; the create entry keeps the old name (AC-003.5, AC-003.22) | Task 3 |
| 54 | STORY-003 | Compliance | Refused rename (400, 403, 404, 409) | High | No audit entry (AC-003.2, AC-003.8 to AC-003.12, AC-003.18) | Task 3 |
| 55 | STORY-003 | Integration | Renamed Department in the Add User list and on 2.3 | High | Both GETs return the new name (AC-003.7) | Task 3 |
| 56 | STORY-003 | Data integrity | Demo seeder boots after a rename | Low | A fresh seeded Department is added (K121-D20) | none |
| 57 | STORY-004 | Timing | Save clicked twice | High | One PUT (AC-004.8) | Task 4 |
| 58 | STORY-004 | Timing | Cancel while the PUT runs | Medium | Ignored (AC-004.13) | Task 4 |
| 59 | STORY-004 | Input extremes | Empty, spaces only, 51 characters | High | MSG01 or MSG02 below the field, no request, text kept (AC-004.6, AC-004.7) | Task 4 |
| 60 | STORY-004 | Input extremes | Name with spaces around it | High | Trimmed in the PUT (AC-004.8) | Task 4 |
| 61 | STORY-004 | Error cascades | 409 MSG43 | High | Below the field, form open, text kept (AC-004.10) | Task 4 |
| 62 | STORY-004 | Error cascades | 404 (deleted meanwhile), 403 or 500 on Save | High | Message by code, form open (AC-004.11) | Task 4 |
| 63 | STORY-004 | Error cascades | GET fails or answers 404 | High | Error state with the code's message and retry, no Edit (AC-004.4) | Task 4 |
| 64 | STORY-004 | State transitions | Cancel, then Edit again | High | No request; field holds the current name, not the typed one (AC-004.12) | Task 4 |
| 65 | STORY-004 | State transitions | Success | High | Form closed, MSG05, new name on 2.3, every query refetched (AC-004.9) | Task 4 |
| 66 | STORY-004 | Environment | Deep link or reload on 2.3 | High | Reads by id from the URL (AC-004.2) | Task 4 |
| 67 | STORY-004 | User types | Non-Admin opens 2.3 | High | MSG08 page, no Edit (AC-004.14) | Task 4 |
| 68 | STORY-004 | Integration | Browser mock mode after a rename | Low | 2.3 shows the new name; the 2.2 table does not (Interim limit 6) | none |

Rows 35 to 68 (KAN-121): Business logic and Scale skipped (no amounts, and one row at a time); Environment covers the deep link only, since the form uses the kit's labelled fields.

Open questions for the spec owner: none.
