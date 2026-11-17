---
module: workspace
date: 2026-11-13
---

# Design: workspace

Spec: `artifacts/03-modules/workspace/spec.md`. The module holds UC-12 Create Department (KAN-120, STORY-001..STORY-002), the UC-13 rename (KAN-121, STORY-003..STORY-004) and default Project (KAN-121, STORY-012..STORY-013, section "KAN-121: default Project" at the end) and UC-10/UC-11 Workspace Setup and Settings (KAN-119, STORY-005..STORY-011); later UCs extend this file through Change Mode. Facts about the existing code: `iteration-1/kan-120-scout.md`, `iteration-1/kan-121-scout.md`. KAN-120 decisions: `iteration-1/kan-120-decisions.md` and Key Decisions (KAN-120) below. KAN-121 decisions: `iteration-1/kan-121-decisions.md`, written `K121-D6` and so on in this file. KAN-119: plan `iteration-1/kan-119/`, decisions Key Decisions (KAN-119) and "Decisions made without the user (KAN-119)" below. Decision ids (D…) are numbered per Jira story: say "KAN-119 D28" or "KAN-120 D7" when it is not clear.

## Architecture Overview

### Departments (KAN-120)

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

### Workspace information (KAN-119)

```
WorkspaceController (/api/v1/workspace, @PreAuthorize ADMIN)
   └─ WorkspaceService (impl: WorkspaceServiceImpl, @Transactional)
        ├─ JiraSiteRule            static: validate + normalise the site (AC-011.3)
        ├─ TicketTrackerClient     tracker: checkServiceAccount(JiraAccount) (AC-011.1/2)
        ├─ JiraSiteLinker (port)   → InterimJiraSiteLinker (reads /_edge/tenant_info) [Interim]
        ├─ TokenCipher             AES-256-GCM, key WORKSPACE_SECRET_KEY, AAD = workspace id
        ├─ WorkspaceInfoRepository JPA, table `workspace` (one row)
        ├─ TenantScope             identity: currentWorkspaceId()
        └─ AuditService            TRACKER_CONFIGURATION_CHANGE / WORKSPACE
WorkspaceStatusLookupImpl  → common/session/lookup/WorkspaceStatusLookup#isCompanyInformationEntered (for /me; BUG-206)
WorkspaceJiraServiceAccount → tracker/service/JiraServiceAccount (replaces ConfiguredJiraServiceAccount)
```

Setup order (AC-005.2..11): field checks → `JiraSiteRule` (MSG61) → row exists? (409) → `checkServiceAccount`
(MSG156) → in one transaction: `linkJiraSite` (MSG153 / MSG61) → insert row → audit → commit. A unique-key violation
on the singleton constraint at insert (concurrent Setup) is mapped to 409.

## Data Model

### Departments (KAN-120)

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

### Workspace information (KAN-119)

Changeset `035-workspace-info.sql` (author `workspace`):
```sql
ALTER TABLE workspace RENAME TO legacy_workspace;          -- changeset 001 table; FKs follow the rename
CREATE TABLE workspace (
    workspace_id              UUID         CONSTRAINT pk_workspace_info PRIMARY KEY,
    company_name              VARCHAR(150) NOT NULL,
    sign_in_provider          VARCHAR(30)  NOT NULL,
    jira_site_address         VARCHAR(255),
    service_account_email     VARCHAR(254),
    service_account_api_token TEXT,
    token_expiry_date         DATE,
    singleton                 BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_workspace_singleton UNIQUE (singleton),
    CONSTRAINT ck_workspace_singleton CHECK (singleton)
);
-- rollback: DROP TABLE workspace; ALTER TABLE legacy_workspace RENAME TO workspace;
```
Also rename the legacy indexes/sequence only if a name clash appears (`workspace_pkey`, `workspace_id_seq`): the new
table's PK constraint is named `pk_workspace_info` explicitly to avoid the clash with the legacy `workspace_pkey`.

Token format in `service_account_api_token`: `v1:` + Base64(nonce[12] ‖ ciphertext‖tag[16]).

## Key Decisions

### Departments (KAN-120)

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

### Workspace information (KAN-119)

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Catalog link (Q1, user) | Port `JiraSiteLinker` + interim adapter | wait for platform Task 18; build the catalog slice here | Unblocks KAN-119; swapping to platform is one bean |
| Token key (Q2, user) | `WORKSPACE_SECRET_KEY`, AES-256-GCM via `javax.crypto` | reuse `CATALOG_SECRET_KEY`; Jasypt/pgcrypto | Keeps the secrets apart, no dependency, ~30 lines |
| Permission check (Q3, user) | `myself` + `mypermissions` (3 permissions, no project) at UC-10 and UC-11 | Browse only; per Project | Meets the Jira AC of KAN-119; UC-13 adds the per-Project check |
| Where the Jira call lives (Q3b, user) | `TicketTrackerClient.checkServiceAccount` in `tracker` | own client in `workspace` | One Jira client; error split already there |
| Company information (Q4, user) | Company name only | + token expiry; + address/logo | SRS 2.1/2.2 |
| Legacy table (Q5, user) | Rename to `legacy_workspace` | new name `workspace_info`; drop | Matches `02_tenant.sql`; FKs survive the rename |
| Clearing the service account (Q6, user) | Required; blank token keeps the stored one | allow clearing | Screen 2.2 marks it required |
| `workspaceReady` (Q7, user) | `WorkspaceStatusLookupImpl` | frontend calls `GET /workspace` | Existing be-foundation contract |
| Second Setup (Q8, user) | 409, web app goes to 2.2 | idempotent replay | Simple; the site can never be overwritten |

## File Structure

### Departments (KAN-120)

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
  components/DepartmentEditForm.tsx             new
  hooks/useDepartmentMutations.ts               + useSaveDepartment
  mocks/handlers.ts                             seeded list, GET and PUT handlers
```

All inside `module.md` `paths`; none added. `DepartmentRenameIT` copies the class annotations of `DepartmentCreationIT` so it shares the cached Spring context; fixtures use a random prefix.

MSW for KAN-121 (K121-D18): the local list starts with `{ departmentId: '00000000-0000-4000-8000-0000000000d1', name: 'IT' }` and `{ departmentId: '00000000-0000-4000-8000-0000000000d2', name: 'HR' }`, copied from `users/mocks/data.ts:6-7`, and `resetMockData` restores it. GET answers the Department or 404 MSG04. PUT applies the rules of the POST, answers 404 MSG04 for an unknown id, 409 MSG43 for another entry's name ignoring case, and otherwise stores and answers 200. The 2.2 table still comes from the `users` mock, so it shows the old name in browser mock mode (spec Interim limit 6). Seeding IT and HR also makes the POST mock answer MSG43 for them; the existing `WorkspaceSettingsPage.test.tsx` cases must be checked for a name that collides.

Seeder after a rename (K121-D20): under `identity-mock`, renaming "Customer Support" makes the next boot insert a fresh "Customer Support" with a new Space, and seeded accounts attach to it by name; the renamed Space loses the " Wiki" suffix the seeder gives. Demo data only.

The `UserListIT` fixture: `'list fixture alpha'` becomes `'list fixture beta'`; the assertion order stays.

`DepartmentCreationIT` copies the class annotations of `UserListIT` exactly (`@SpringBootTest` properties, `@AutoConfigureMockMvc`, `@Import(TwoTenantDatabases.class)`, `@WithTSolveUser(roles = Role.ADMIN)`) so it shares that cached Spring context, adds no `@MockBean`, and starts no container. Fixtures use a random prefix and are cleaned as `UserCreationIT` does.

MSW: `workspace/mocks/handlers.ts` answers POST with 201, or 409 MSG43 for a name already held case-insensitively in a small local list seeded with `IT` and `HR` (the names the users mock serves, not imported from it), and `resetMockData` restores the list. `users` answers `GET /users/departments` from a static list and is tried first, so in browser mock mode a new Department shows MSG05 but does not appear in the table. The page test overrides that GET for its own case. Changing `FEATURE_MOCKS` is forbidden by its own comment, and importing another feature's mock data by guideline 01.

### Workspace information (KAN-119)

```
backend/src/main/java/vn/tsolve/workspace/
  controller/WorkspaceController.java
  dto/WorkspaceSetupRequest.java  WorkspaceUpdateRequest.java  WorkspaceInfoResponse.java
  entity/WorkspaceInfo.java                    (table workspace)
  repository/WorkspaceInfoRepository.java
  service/WorkspaceService.java  service/impl/WorkspaceServiceImpl.java
  service/impl/WorkspaceStatusLookupImpl.java  WorkspaceJiraServiceAccount.java
  site/JiraSiteRule.java  JiraSiteLinker.java  InterimJiraSiteLinker.java  JiraSiteLinkRefusedException.java
  crypto/TokenCipher.java
backend/src/main/java/vn/tsolve/tracker/client/TicketTrackerClient.java, JiraClient.java   (+ checkServiceAccount)
backend/src/main/resources/db/changelog/changes/035-workspace-info.sql (+ master include)
backend/src/main/resources/application.yml  (tsolve.workspace.secret-key: ${WORKSPACE_SECRET_KEY}; remove stand-in keys)
backend/src/test/resources/application*.yml (test key)
.env.example (WORKSPACE_SECRET_KEY)
frontend/openapi/tsolve-api.yaml  → npm run generate:api
frontend/src/features/workspace/{api,hooks,components,pages,mocks}/…
frontend/src/shared/messages/{catalog.ts,report3-messages.json,catalog.test.ts}
```

## External Dependencies

### Departments (KAN-120)

None new.

### Workspace information (KAN-119)

- None new. `javax.crypto` (JDK), Spring `RestClient`, existing Jira REST API v3 endpoints `myself`, `mypermissions`,
  Atlassian `/_edge/tenant_info`.

## Decisions made without the user (KAN-119)

The user delegated the remaining calls on 12/11 ("tự quyết và note lại"). Each is safe to revisit.

| # | Question | Chosen | Alternatives | Rationale |
|---|----------|--------|--------------|-----------|
| D1 | Message for an invalid site address | `MSG61` (System_Messages v3460) | platform's `MSG156` (AC-004.21) | v3460 gives `MSG156` to the service account; change request to platform listed in the spec |
| D2 | Frontend catalog marks `MSG61` Retired and lacks `MSG153`/`MSG156` | Add them as v3460 gives them; drop `MSG61` from the Retired test | keep the old catalog | `report3-messages.json` predates v3460; `frontend/src/shared/messages/**` added to this module's paths |
| D3 | Message for "already set up" | 409 with technical code `WORKSPACE_ALREADY_SET_UP`, no user text | invent `MSG158` | No such message in v3460; the web app redirects to 2.2, the user never sees an error |
| D4 | Over-length fields | `MSG02` with `max_length` | `MSG01` | `MSG02` is the baseline length message |
| D5 | Email format | `MSG03` | none | Baseline email message |
| D6 | SSRF rule | https, no port/user info/query/fragment/path, host `<one label>.atlassian.net`, no redirects in the tenant-info read | allow any https host; allowlist by DNS | Jira Cloud sites are `<name>.atlassian.net` (SRS example); blocks internal addresses |
| D7 | Tenant info unreadable | `MSG61` on `jiraSiteUrl` | 502 / `MSG06` | A site that does not answer is, to the Admin, not a valid site |
| D8 | Field of `MSG156` | `serviceAccountToken` | `serviceAccountEmail` | The token is what is usually wrong |
| D9 | Which failures are `MSG156` | 401, 403, any error status, timeout, unreachable, missing permission | Jira down → `MSG06` | SRS: "could not connect … or lacks the required permissions" covers both |
| D10 | UC-11 Jira call when only the name changes | No Jira call | always re-check | A Jira outage must not block renaming the company |
| D11 | Audit action | `TRACKER_CONFIGURATION_CHANGE`, target type `WORKSPACE` | add `WORKSPACE_UPDATE` to `AuditAction` | The action list is fixed by the dashboard-audit spec and the OpenAPI enum |
| D12 | `sign_in_provider` value | `GOOGLE` | null / config | NOT NULL in the DDL; sign-in is Keycloak with Google (ADR-0003) |
| D13 | Link and insert in one transaction | Link first, then insert, same transaction | insert then link after commit | A refused link stores no row; the left-over risk (commit fails after the link) is a platform change request (AC-004.23 idempotent) |
| D14 | `WorkspaceStatusLookup` interface does not exist yet | Create it in `common/lookup` with `boolean isReady()` (be-foundation design.md:182 names this file) | wait for be-foundation | Lets `/me` wire it later; path added to `module.md` Superseded by BUG-206: `/me` reads `common/session/lookup/WorkspaceStatusLookup#isCompanyInformationEntered`, so the impl implements that and `common/lookup` is deleted. |
| D15 | Stand-in `ConfiguredJiraServiceAccount` | Delete it, its test and its 3 settings (`application.yml`, `.env.example`) | keep under a profile | Its own Javadoc says to delete it; one bean only (AC-008.2) |
| D16 | Decrypt failure | Log error, return empty | throw | No Jira call with a broken token; tracker already handles "no account" (AC-012.8) |
| D17 | Request `jiraSiteUrl` on PUT | Ignored | 400 | Read-only field; a client that echoes the form must not fail |
| D18 | Missing Jira timeouts | Reuse `tsolve.tracker.jira.connect-timeout` 5 s / `read-timeout` 10 s; interim linker uses the same values | new settings | NFR 3, no new knobs |
| D19 | After Setup, landing page | User List (`paths.users`) | Workspace Settings | SRS UC-10 step 7 |
| D20 | Spec depth / risk review | Deep; risk-review done inline below | five-persona subagent | Planner agent was stopped by the user; findings folded into the edge cases |
| D21 | "Token differs" on UC-11 (builder check) | Any non-blank token counts as changed: it is checked with Jira and `tokenChanged` = true; the stored token is never decrypted to compare | decrypt and compare | Simpler; never handles the old secret in clear for a comparison |
| D23 | `JIRA_SITE_URL` after removing the stand-in (Task 6) | Keep it in `.env.example` under the Jira import section; only the stand-in's email and token variables go | delete it everywhere | `ticket/client/JiraRestIssueSource` (ticket-repository) still reads it for browse links |
| D24 | No side menu on 2.1 (AC-009.1, Task 7) | Spec change: "with no menu" removed from AC-009.1; change request to `fe-foundation` (`AppShell`) | edit `AppShell` here | `AppShell` is `fe-foundation` code, locked while its spec is changed since approval |
| D25 | Detecting the 409 on 2.1 (Task 7) | Status 409 with no field error on `setupWorkspace` (its only non-field conflict) | read the technical code | The shared transport normalises unknown codes to `MSG06` and drops `WORKSPACE_ALREADY_SET_UP` |
| D26 | fe-foundation's route-table test still expects the 2.1 placeholder (Task 7) | Remove the 2.1 row, as its comment asks the building module to do; path added to `module.md` | leave the test failing | Same convention chatbot (12.1) and triage (10.1) followed |
| D27 | Developer for Task 8 after Codex hit its usage limit (until 14/11) | Claude `harnessclaude-developer` subagent with the same brief and rules | wait for Codex | Keeps KAN-119 moving; the Reviewer stays independent of the writer |
| D28 | Which Workspace `JiraServiceAccount.current()` reads in a worker job (evaluation, Task 6 CRITICAL) | `WorkerTenantService.currentWorkspaceId()` when present (optional bean), else `TenantScope`; AC-008.1 reworded | make `worker`'s `runInTenant` also bind `TenantScope` | `worker` is `completed`; the fix stays inside this module; a later platform tenant binding can replace both |
| D29 | Email-only update while the stored token cannot be decrypted (evaluation, Task 5) | 400 `MSG156` on `serviceAccountToken`, workspace id logged only; AC-007.5 extended | generic 500 | The Admin must re-enter the token after a key loss; MSG156 tells them which field |
| D30 | Jira check inside the `setup` transaction (evaluation, Task 4, backend/AGENTS.md §4) | Check before the transaction; link, persist and audit in a `TransactionTemplate`, like `update` | keep as is | No remote call while a DB transaction is open |
| D22 | Where the interim linker puts the `cloudId` (builder check) | Returned to the service and only logged (host, not secrets); nothing persists it until the catalog exists | add a `jira_cloud_id` column to the tenant table | The catalog owns `jira_cloud_id` (platform AC-001.15); a tenant column would duplicate it |

## Risk review (KAN-119, inline, Deep spec)

Verdict: **CAUTION** — go, with the mitigations below folded into the tasks.
- Secret leak (token in logs/toString/audit/errors) → AC-011.7 tests per type; `JiraAccount.toString` already hides it.
- SSRF via site address → AC-011.3 rule before any call; no redirects in the interim linker; tests with
  `http://`, `https://localhost`, `https://169.254.169.254`, `https://a.atlassian.net.evil.com`, `https://x@a.atlassian.net`,
  `https://a.atlassian.net:8443`.
- Key loss = all tokens undecryptable → documented in `.env.example`; decrypt failure returns empty (D16); the Admin
  re-enters the token in 2.2.
- Cross-tenant copy of ciphertext → AAD = workspace id (AC-011.5).
- Half-done Setup (catalog linked, tenant commit failed) → D13 + platform change request.
- MSG153 untestable end-to-end until the catalog exists → [Interim] in spec; unit test with a refusing double.

## Edge Cases & Risks

### Departments (KAN-120)

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

### Workspace information (KAN-119)

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-005 | Concurrency | Two Admins submit Setup at once | High | One row; the other gets 409 (unique singleton) | Task 4 |
| 2 | STORY-005 | Security | Site `https://169.254.169.254` / `localhost` / evil suffix | Critical | MSG61, no outbound call | Task 3 |
| 3 | STORY-005 | Input | Site with trailing `/`, upper case, spaces | Medium | Normalised to `https://<host>` | Task 3 |
| 4 | STORY-005 | External | Jira 401 / 403 / 500 / timeout | High | MSG156, nothing stored, no body logged | Task 2, Task 4 |
| 5 | STORY-005 | External | Account authenticates but lacks MODIFY_REPORTER | High | MSG156 | Task 2 |
| 6 | STORY-005 | External | tenant_info 404 or no cloudId / redirect | High | MSG61, nothing stored | Task 3 |
| 7 | STORY-005 | State | Linker refuses (another Workspace) | High | MSG153, no row | Task 4 |
| 8 | STORY-005 | State | Setup when already set up | High | 409, site unchanged | Task 4 |
| 9 | STORY-011 | Security | Token in logs / audit / response | Critical | Never present | Task 1, Task 4, Task 5 |
| 10 | STORY-011 | Security | Ciphertext copied to another Workspace id | High | Decrypt fails | Task 1 |
| 11 | STORY-011 | Config | Key missing / wrong length | High | Startup fails, names setting only | Task 1 |
| 12 | STORY-007 | Input | Blank token on update | High | Stored token kept; check uses it if the email changed | Task 5 |
| 13 | STORY-007 | External | Only company name changed while Jira is down | Medium | Saved, no Jira call | Task 5 |
| 14 | STORY-007 | Input | Body carries `jiraSiteUrl` | Medium | Ignored | Task 5 |
| 15 | STORY-008 | State | Not set up | High | `current()` empty | Task 6 |
| 16 | STORY-008 | Security | Tampered ciphertext | High | Error log without value, empty | Task 6 |
| 17 | STORY-009 | UX | Double click Save | Medium | One request | Task 7 |
| 18 | STORY-009 | UX | 409 response | Medium | No error, go to 2.2 | Task 7 |
| 19 | STORY-010 | UX | Token never shown, empty means keep | High | Field empty; PUT omits token | Task 8 |
| 20 | STORY-006 | Access | Non-Admin calls any endpoint | High | 403 | Task 4, Task 5 |

## Change 2026-11-16: Jira service account through the gateway (KAN-344)

| Decision | Chosen | Alternatives | Rationale |
|---|---|---|---|
| Authentication | Scoped API token of an Atlassian service account, `Authorization: Bearer` to `https://api.atlassian.com/ex/jira/{cloudId}` (AC-011.1) | Basic email + token on the site URL (kept by tracker until now) | User decision 16/11 (option B): a real service account only has scoped tokens, which get 401 on the site URL (RESEARCH-003); least privilege, not tied to a person |
| Email field | Removed from Setup, Settings, the API and the audit; `service_account_email` column kept, no longer written or read | Keep it optional | Bearer needs no email; a dead field misleads the Admin (user, 16/11) |
| Cloud id store | New nullable column `workspace.jira_cloud_id VARCHAR(36)`, filled at Setup and when a token is replaced on a row that has none (AC-005.6, AC-007.11) | The platform catalog (`tenant_workspace.jira_cloud_id`, platform AC-004.19) | The catalog link is not built (platform Task 18); the tenant row needs no other module. When the catalog lands, `current()` may read it instead |
| Reading the cloud id | `JiraSiteLinker` gains `readCloudId(site)`: the same no-redirect `/_edge/tenant_info` read as `linkJiraSite`, called **before** the check and outside the transaction; only a canonical UUID is accepted (AC-011.8) | Call `linkJiraSite` before the check | The check needs the cloud id first; `linkJiraSite` stays the in-transaction link of AC-005.8 for the future catalog |
| Old rows | `current()` answers empty while `jira_cloud_id` is null (AC-008.4); the Admin enters a new (scoped) token, which fills it (AC-007.11) | Backfill by a job | Old rows hold classic tokens that cannot work with Bearer anyway; the Admin must re-enter the token |
| PR | Workspace and tracker in one PR (KAN-344); the import follows in its own PR | Separate PRs | The check is `JiraClient.checkServiceAccount` and `JiraAccount` is tracker's type; one `http()` builder serves every call |

## Change 2026-11-17: Jira access as the T-Solve Jira App (KAN-349)

Supersedes the service-account parts of "Change 2026-11-16" (KAN-344). RESEARCH-005 (option D + probe).

| Decision | Chosen | Alternatives | Rationale |
|---|---|---|---|
| Identity | The Forge app system token (`x-forge-oauth-system`, ≤ 4 h) | Service account + scoped token (KAN-344), OAuth client credentials | No customer secret, no per-project grant (probe), fits Marketplace rules (user decision 17/11) |
| Delivery | `POST /api/v1/jira-app/app-token` on the existing FIT chain (`JiraAppSecurityConfig`, role `JIRA_APP`); hourly scheduled trigger + install/upgrade trigger call the remote directly | Pull from a Forge function via `invokeRemote` | Forge docs: a function hop may deliver a token with much less than 2 h left |
| Store | Redis key `tsolve:jira-app-token:<workspaceId>`, value = `TokenCipher.encrypt(workspaceId, json{token, cloudId, receivedAt, exp})`, TTL = `exp − now` | In memory; tenant DB | Survives restarts, shared by web and worker (user decision 17/11) |
| Workspace of a call | `jiraapp.service.JiraWorkspaceLookup.workspaceOf(cloudId)` then `TenantScope.runIn` | A new lookup | Same mapping every Jira App endpoint uses (stub `TSOLVE_STUB_JIRA_SITES` until the platform catalog) |
| `exp` | Read from the token's JWT payload (Base64URL JSON, no signature check) | Trust a fixed 4 h | Forge sends tokens with 2–4 h left; the key must not outlive the token |
| Status for 2.2 | `WorkspaceInfoResponse(companyName, jiraSiteUrl, jiraAppConnected, jiraAppTokenReceivedAt)` built by the service from the row and the store | A separate endpoint | One read for the screen |
| Old credentials | Changeset 043 sets `service_account_email` and `service_account_api_token` to NULL; columns kept | Drop the columns | Forward-only, no entity churn; no customer secret left |
| Dead code | `TicketTrackerClient.checkServiceAccount`, `JiraPermissionMissingException`, the `MSG156` reason (KAN-344 follow-up, #265) and their tests removed | Keep | Nothing calls them after AC-011.1 is removed |
| Numbering | The token story is STORY-014 (AC-014.x), the Jira App line AC-010.10, the cleared columns AC-011.11 | Keep STORY-012 | KAN-121 reached `main` first with STORY-012 and STORY-013; removed ids are not reused |
| KAN-121 default Project check | `TrackerProjectService.checkChatbotProject` keeps reading `JiraServiceAccount.current()`, which now holds the app token | Its own token | One Jira access per Workspace; the probe showed the app user has Create issues and Modify reporter |

## KAN-121: default Project (STORY-012, STORY-013)

Change Mode of 17/11. Facts: `iteration-1/kan-121-default-project-scout.md` (from `origin/main` `db594997`). Decisions K121-D26 to K121-D42 in `iteration-1/kan-121-decisions.md`. Tasks 12 and 13 in `iteration-1/tasks.md`; edge cases rows 69 to 105 in `iteration-1/design.md`. Branch `feat/KAN-121-workspace-default-project` (K121-D26).

### API

```
GET /api/v1/departments/{departmentId}              (Admin) → 200 DepartmentDetail | 401 | 403 | 404 MSG04 | 500
PUT /api/v1/departments/{departmentId}              (Admin) body UpdateDepartmentRequest { name, defaultProjectId? (uuid | null) }
                                                     → 200 DepartmentDetail | 400 MSG01/MSG02 (name)
                                                       | 400 MSG04/MSG156/MSG157 (defaultProjectId) | 401 | 403 | 404 MSG04 | 409 MSG43 | 500

DepartmentDetail        { departmentId, name, defaultProject: DepartmentProjectOption | null, projects: DepartmentProjectOption[] }
DepartmentProjectOption { projectId (uuid), jiraProjectKey }
```

Contract (`frontend/openapi/tsolve-api.yaml`, OpenAPI 3.1): `getDepartment` and `updateDepartment` answer `DepartmentDetail` instead of `DepartmentRef`; `UpdateDepartmentRequest` gains `defaultProjectId: { type: [string, 'null'], format: uuid }`, not in `required` (K121-D29: absent = null); `defaultProject` is `oneOf: [{ $ref: DepartmentProjectOption }, { type: 'null' }]` as `UserAccountDetail.department` (`tsolve-api.yaml:3573`). Every field of the two new schemas is required. `DepartmentRef` is not touched (K121-D33).

Java: `vn.tsolve.workspace.dto.DepartmentDetailResponse(UUID departmentId, String name, DepartmentProjectOptionResponse defaultProject, List<DepartmentProjectOptionResponse> projects)` and `DepartmentProjectOptionResponse(UUID projectId, String jiraProjectKey)`. `UpdateDepartmentRequest` gains `UUID defaultProjectId` (no annotation; a value that is not a UUID is an unreadable body, 400 from the handler). `DepartmentService.get` and `update` return `DepartmentDetailResponse`; `create` keeps `DepartmentRefResponse`.

### Service flow

`DepartmentServiceImpl.update` loses `@Transactional` and gains a `TransactionTemplate` built from `PlatformTransactionManager`, as `WorkspaceServiceImpl.java:48, 68, 99` (Non-Functional 2: no outbound call inside a transaction, `backend/AGENTS.md:144`). New constructor parameters: `TrackerProjectService tracker`, `PlatformTransactionManager transactions`.

```
update(departmentId, request)
  scope.currentWorkspaceId()                                   AC-003.21, AC-012.14
  name   = request.name().strip()
  wanted = request.defaultProjectId()                          null or absent: none (K121-D29)
  before = repository.findActive(departmentId)                 empty: NotFoundException MSG04 (AC-003.2); no lock
  if wanted != null && !wanted.equals(before.defaultProjectId()):                 AC-012.10 (unchanged: no Jira)
      option = repository.findActiveProject(before.id(), wanted)                  empty: 400 MSG04 on defaultProjectId (AC-012.5)
      switch tracker.checkChatbotProject(option.jiraProjectKey()):                AC-012.7, outside any transaction
          READY                -> go on
          NOT_READY            -> 400 MSG157 {project_name: key} on defaultProjectId   (AC-012.9)
          DENIED, UNAVAILABLE  -> 400 MSG156 {tracker_name: "Jira"} on defaultProjectId (AC-012.8)
  return transaction.execute(status ->
      current = repository.lockActive(departmentId)                               empty: 404 MSG04; FOR UPDATE (K121-D7)
      chosen  = wanted == null ? null : repository.lockActiveProject(current.id(), wanted)
                                                                                   empty: 400 MSG04 (AC-012.13); FOR SHARE
      nameChanged    = !name.equals(current.name())
      defaultChanged = !Objects.equals(wanted, current.defaultProjectId())
      if !nameChanged && !defaultChanged: return detail(current)                  AC-003.14
      if nameChanged:    renameDepartment (23505: MSG43), spaceId = renameSpace   AC-003.3, AC-003.4, AC-012.15
      if defaultChanged: repository.setDefaultProject(current.id(), chosen == null ? null : chosen.rowId())   AC-012.4, AC-012.6
      audit.record(DEPARTMENT_CHANGE, "DEPARTMENT", departmentId, details)       AC-012.11
      return new DepartmentDetailResponse(departmentId, name, chosen option or null, repository.activeProjects(current.id())))
```

Errors on the field use `new FieldMessageCodeException(HttpStatus.BAD_REQUEST, code, params, "defaultProjectId")` (`common/exception`, as `WorkspaceServiceImpl.java:219-221`). Any exception inside the callback rolls back the whole save (AC-012.15, AC-003.17).

`get` stays `@Transactional(readOnly = true)`: `findActive`, then `activeProjects`, and answers `DepartmentDetailResponse` (AC-012.1, AC-012.3).

Audit details (K121-D40) are built in a `LinkedHashMap` because `defaultProject` may be null (`Map.of` refuses null):

```
{ operation: "UPDATE",
  before: { name: <old>, defaultProject: {projectId, jiraProjectKey} | null },   defaultProject only when it changed
  after:  { name: <new>, defaultProject: {projectId, jiraProjectKey} | null },
  spaceId: <uuid> }                                                              only when the name changed
```

A rename alone keeps today's details exactly (AC-003.5).

### SQL (`DepartmentRepository`, JDBC; no changeset)

`department.default_project_id BIGINT` and its FK already exist (`002-tenant-project-suggestion.sql:10, 28-30`); the latest changeset stays `042-workspace-jira-cloud-id.sql`. The record `Current` becomes `Current(long id, String name, UUID defaultProjectId, String defaultProjectKey)`; a new record `ProjectOption(long rowId, UUID projectId, String jiraProjectKey)`.

```sql
-- findActive (GET and the pre-read of PUT): a default Project that is not Active reads as none (AC-012.3)
SELECT d.department_id, d.name, p.public_id AS default_project_id, p.jira_project_key AS default_project_key
FROM department d
LEFT JOIN project p ON p.project_id = d.default_project_id AND p.status = 'ACTIVE'
WHERE d.public_id = :id AND d.status = 'ACTIVE';

-- lockActive: two statements (BUG-314). First lock the Department row alone:
SELECT department_id FROM department WHERE public_id = :id AND status = 'ACTIVE' FOR UPDATE;
-- then re-read it with the findActive join in a new statement. A single `LEFT JOIN … FOR UPDATE OF d`
-- rechecks only the locked row after the wait and keeps the stale Project row, so the second of two
-- concurrent saves audited an old before.defaultProject (AC-012.16).

-- activeProjects (AC-012.1)
SELECT public_id, jira_project_key FROM project
WHERE department_id = :departmentId AND status = 'ACTIVE' ORDER BY jira_project_key;

-- findActiveProject (before Jira) and lockActiveProject (in the transaction, + FOR SHARE): AC-012.5, AC-012.13
SELECT project_id, public_id, jira_project_key FROM project
WHERE public_id = :projectId AND department_id = :departmentId AND status = 'ACTIVE';

-- setDefaultProject (null clears)
UPDATE department SET default_project_id = :projectRowId WHERE department_id = :id;
```

Reading `project` with workspace's own SQL is K121-D32 (project already reads `department` the same way; calling `ProjectService` would be a cycle). `FOR SHARE` keeps the Project from being Removed between the check and the commit once a remove-Project story exists.

### Jira check (tracker, K121-D39)

Workspace calls only `tracker.service.TrackerProjectService` (`backend/AGENTS.md:78-79`). New pieces, all in `tracker`:

| Piece | Where | What |
|---|---|---|
| `ChatbotProjectCheck` | `tracker/dto/ChatbotProjectCheck.java` | enum `READY`, `NOT_READY`, `DENIED`, `UNAVAILABLE` |
| `TrackerCreateField` | `tracker/dto/TrackerCreateField.java` | record `(String fieldId, boolean required, boolean hasDefaultValue)` |
| `TrackerProjectService.checkChatbotProject(String projectKey)` | `tracker/service/TrackerProjectService.java`, impl `TrackerProjectServiceImpl` | the rule of AC-012.7; never throws for a Jira failure |
| `TicketTrackerClient.hasProjectPermissions(JiraAccount, String projectKey, List<String> permissions)` | `tracker/client/TicketTrackerClient.java`, impl `JiraClient` | `GET /rest/api/3/mypermissions?projectKey={key}&permissions=CREATE_ISSUES,MODIFY_REPORTER`; true when every named permission has `havePermission` true |
| `TicketTrackerClient.createFields(JiraAccount, String projectKey, String issueTypeName)` | same | `Optional<List<TrackerCreateField>>`: empty when the project has no issue type of that name (ignoring case) with `subtask` false; else the fields of `GET /rest/api/3/issue/createmeta/{key}/issuetypes/{id}` |

Calls, all through the existing gateway builder (`JiraClient.java:37, 190-202`, `Authorization: Bearer`, `https://api.atlassian.com/ex/jira/{cloudId}`) and its `call` error mapping (`:204-219`: 429/5xx/no answer → `JiraUnavailableException`, other 4xx → `JiraRejectedException`, no body kept):

1. `GET /rest/api/3/mypermissions?projectKey={key}&permissions=CREATE_ISSUES,MODIFY_REPORTER` → `permissions.CREATE_ISSUES.havePermission`, `permissions.MODIFY_REPORTER.havePermission`.
2. `GET /rest/api/3/issue/createmeta/{key}/issuetypes?startAt={n}&maxResults=200` → array `issueTypes` (items `id`, `name`, `subtask`), paged by `startAt`/`total` until all are read or a match is found.
3. `GET /rest/api/3/issue/createmeta/{key}/issuetypes/{issueTypeId}?startAt={n}&maxResults=200` → array `fields` (items `fieldId`, `required`, `hasDefaultValue`), paged the same way.

The keys `issueTypes` and `fields` are from the Jira Cloud REST v3 reference; `values` is read when they are absent (older answers). Task 12 makes the three calls once by hand on the staging site and notes the shapes in its log (Definition of Done). The project key goes into the path through the URI template, never by concatenation.

`checkChatbotProject` (copies the shape of `findProject`, `TrackerProjectServiceImpl.java:32-77`):

```
account = serviceAccount.current()             empty: DENIED (AC-012.8)
!jira.hasProjectPermissions(...)               DENIED
fields = jira.createFields(account, key, "Task")   empty: NOT_READY (AC-012.9)
any field required && !hasDefaultValue && fieldId not in {summary, description, project, issuetype, reporter, labels}: NOT_READY
else READY
catch JiraRejectedException (any 4xx, 404 included, K121-D35): DENIED
catch JiraUnavailableException, RuntimeException: UNAVAILABLE
```

Each outcome is logged at `warn` (`info` for READY) with the key and Jira's status only, as `findProject` does (AC-012.17). Timeouts are `JiraClient`'s `tsolve.tracker.jira.connect-timeout` 5 s and `read-timeout` 10 s per call (Non-Functional 6); a check makes three calls, more only when a list has over 200 entries.

### Web app

- `useGetDepartment` now returns `DepartmentDetail`; `DepartmentDetailPage` passes it to the form unchanged.
- `DepartmentEditForm` (renamed from `DepartmentNameForm`, PR #269 review; its Javadoc becomes "the name and default Project and their inline edit form, UC-13"): props `{ department: DepartmentDetail }`.
  - View: a second `Descriptions` item, label "Default Project", `department.defaultProject?.jiraProjectKey ?? 'None'` (AC-013.1).
  - Edit: below "Department name", `FormField name="defaultProjectId" label="Default Project" extra=<AC-013.4 text>` holding an antd `Select` with options `[{ value: '', label: 'None' }, ...department.projects.map(p => ({ value: p.projectId, label: p.jiraProjectKey }))]` and `disabled={rename.isPending}` (AC-013.2, AC-013.3, AC-013.9). Initial value `department.defaultProject?.projectId ?? ''`.
  - Submit: `{ name: values.name.trim(), defaultProjectId: values.defaultProjectId || null }` (AC-013.5). `applyApiErrors` already places a `fieldErrors` entry on the form field of the same name with its params (`shared/forms/apply-api-errors.ts:10-19`), so MSG04, MSG156 and MSG157 land under the select (AC-013.7).
  - Success and Cancel as today (AC-013.6, AC-013.8): the refetched detail shows the stored value; `form.resetFields()` restores it.
- Catalog: `MSG157: { type: 'field', text: '{project_name} cannot receive Chatbot issues: it has no issue type Task or requires other fields. Choose another Project.' }` in `shared/messages/catalog.ts` beside MSG156 (AC-013.10, K121-D41).
- MSW (`features/workspace/mocks/handlers.ts`): the seeded list keeps IT and HR and gains `defaultProjectId: null` and per-Department projects: IT has `OPS` (`00000000-0000-4000-8000-0000000000e1`) and `SUP` (`...0000000000e2`), HR has none. GET answers the `DepartmentDetail`; PUT keeps the name rules, answers 400 MSG04 on `defaultProjectId` for an id not in that Department's list, else stores it (null clears) and answers the detail. MSG156 and MSG157 are driven in tests with `server.use`. `resetMockData` restores the seed.

### Decisions (K121-D29 to K121-D42, summary)

| Decision | Chosen | Alternatives | Rationale |
|---|---|---|---|
| D29 Request | `defaultProjectId` in the rename PUT, absent = null | Own endpoint; absent = unchanged | One Save (UC-13 step 2); records cannot tell absent from null |
| D31 Other id | 400 MSG04 on `defaultProjectId` | MSG40; MSG157; provisional key | No catalog message fits better; 404 already means "no Department" |
| D32 Reading Projects | Workspace's own SQL | `ProjectService` | Would be a module cycle |
| D33 Read model | New `DepartmentDetail` | Widen identity's `DepartmentRef` | Keeps identity and project responses unchanged |
| D34 Required fields | Allowed {summary, description, project, issuetype, reporter, labels}, defaults ignored | Stricter sets | Exactly what UC-58 will send |
| D35 Failures | MSG156 for access/connection incl. 404; MSG157 only for Task/fields | 404 → MSG157; 503 MSG06 | Field message always; text matches cause |
| D36 Removed default | Reads as none; no clearing code | Clearing SQL now | No caller yet |
| D37 Unchanged default | No Jira call | Re-check every save | A rename must not fail when Jira is down |
| D39 Boundary | `TrackerProjectService.checkChatbotProject` | Client called from workspace | Compliant path exists (KAN-127 pattern) |
| D40 Audit | Same entry, `defaultProject` in before/after | New action | No changeset |

Decided in planning: when the pre-read shows the requested Project as the current default (no Jira call) but the locked row shows another one (a concurrent save changed it), the save stores the requested Project without a new Jira check; it passed the check when it was first chosen. (edge row 94)

Decided in planning: "None" is the empty string inside the form only; the request sends null.

Decided in planning (builder check): the pre-read of `update` (`findActive`, `findActiveProject`) runs in its own read-only `transaction.execute`, closed before the Jira call, so it keeps working when the tenant row guard of `architecture.md` needs a transaction for every read.

Decided in planning (builder check): errors come one at a time in this order: name rules (MSG01, MSG02, by `@Valid`), unknown Department (404 MSG04), the chosen Project (MSG04, MSG156, MSG157), then the name's uniqueness (409 MSG43, at the write). A taken name with a failing Project answers the Project's error first.

Decided in planning (builder check): when Jira lists several non-sub-task types named Task, the first in Jira's order is used. `createFields` reads `issueTypes`/`fields` and falls back to `values`; unit tests stub both shapes, and the manual staging call of the Definition of Done only confirms which one Jira sends (it blocks nothing).

Decided in planning (builder check): the scoped token's scopes are not checked separately. A token without the read scopes these calls need gets 401 from Jira, which is MSG156 ("lacks the required permissions"); the Setup check of AC-011.1 is unchanged.

Decided in planning (builder check): a field error under the select clears when the Admin picks another value (antd's default).

Decided in planning: the `module.md` `paths` of workspace gain the tracker files this feature changes (K121-D39); `TrackerProjectServiceImplTest` and `JiraClientTest` get the tracker tests.
