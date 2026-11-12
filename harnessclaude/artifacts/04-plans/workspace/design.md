---
module: workspace
date: 2026-11-13
---

# Design: workspace

Spec: `artifacts/03-modules/workspace/spec.md`. The module holds UC-12 Create Department (KAN-120, STORY-001..STORY-002) and UC-10/UC-11 Workspace Setup and Settings (KAN-119, STORY-003..STORY-009); later UCs extend this file through Change Mode. KAN-120: facts about the existing code `iteration-1/kan-120-scout.md`, decisions `iteration-1/kan-120-decisions.md` and Key Decisions (KAN-120) below. KAN-119: plan `iteration-1/kan-119/`, decisions Key Decisions (KAN-119) and "Decisions made without the user (KAN-119)" below. Decision ids (D…) are numbered per Jira story: say "KAN-119 D28" or "KAN-120 D7" when it is not clear.

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

### Workspace information (KAN-119)

```
WorkspaceController (/api/v1/workspace, @PreAuthorize ADMIN)
   └─ WorkspaceService (impl: WorkspaceServiceImpl, @Transactional)
        ├─ JiraSiteRule            static: validate + normalise the site (AC-009.3)
        ├─ TicketTrackerClient     tracker: checkServiceAccount(JiraAccount) (AC-009.1/2)
        ├─ JiraSiteLinker (port)   → InterimJiraSiteLinker (reads /_edge/tenant_info) [Interim]
        ├─ TokenCipher             AES-256-GCM, key WORKSPACE_SECRET_KEY, AAD = workspace id
        ├─ WorkspaceInfoRepository JPA, table `workspace` (one row)
        ├─ TenantScope             identity: currentWorkspaceId()
        └─ AuditService            TRACKER_CONFIGURATION_CHANGE / WORKSPACE
WorkspaceStatusLookupImpl  → common/lookup/WorkspaceStatusLookup (for /me)
WorkspaceJiraServiceAccount → tracker/service/JiraServiceAccount (replaces ConfiguredJiraServiceAccount)
```

Setup order (AC-003.2..11): field checks → `JiraSiteRule` (MSG61) → row exists? (409) → `checkServiceAccount`
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

`DEPARTMENT_CHANGE` is added in four places together: `AuditAction.java`, changeset 027, the `AuditAction` enum in the contract, and `ACTION_LABELS` in `audit-filters.ts` (label "Department change").

### Workspace information (KAN-119)

Changeset `029-workspace-info.sql` (author `workspace`):
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
backend/src/main/java/vn/tsolve/common/lookup/WorkspaceStatusLookup.java
backend/src/main/java/vn/tsolve/tracker/client/TicketTrackerClient.java, JiraClient.java   (+ checkServiceAccount)
backend/src/main/resources/db/changelog/changes/029-workspace-info.sql (+ master include)
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
| D14 | `WorkspaceStatusLookup` interface does not exist yet | Create it in `common/lookup` with `boolean isReady()` (be-foundation design.md:182 names this file) | wait for be-foundation | Lets `/me` wire it later; path added to `module.md` |
| D15 | Stand-in `ConfiguredJiraServiceAccount` | Delete it, its test and its 3 settings (`application.yml`, `.env.example`) | keep under a profile | Its own Javadoc says to delete it; one bean only (AC-006.2) |
| D16 | Decrypt failure | Log error, return empty | throw | No Jira call with a broken token; tracker already handles "no account" (AC-012.8) |
| D17 | Request `jiraSiteUrl` on PUT | Ignored | 400 | Read-only field; a client that echoes the form must not fail |
| D18 | Missing Jira timeouts | Reuse `tsolve.tracker.jira.connect-timeout` 5 s / `read-timeout` 10 s; interim linker uses the same values | new settings | NFR 3, no new knobs |
| D19 | After Setup, landing page | User List (`paths.users`) | Workspace Settings | SRS UC-10 step 7 |
| D20 | Spec depth / risk review | Deep; risk-review done inline below | five-persona subagent | Planner agent was stopped by the user; findings folded into the edge cases |
| D21 | "Token differs" on UC-11 (builder check) | Any non-blank token counts as changed: it is checked with Jira and `tokenChanged` = true; the stored token is never decrypted to compare | decrypt and compare | Simpler; never handles the old secret in clear for a comparison |
| D23 | `JIRA_SITE_URL` after removing the stand-in (Task 6) | Keep it in `.env.example` under the Jira import section; only the stand-in's email and token variables go | delete it everywhere | `ticket/client/JiraRestIssueSource` (ticket-repository) still reads it for browse links |
| D24 | No side menu on 2.1 (AC-007.1, Task 7) | Spec change: "with no menu" removed from AC-007.1; change request to `fe-foundation` (`AppShell`) | edit `AppShell` here | `AppShell` is `fe-foundation` code, locked while its spec is changed since approval |
| D25 | Detecting the 409 on 2.1 (Task 7) | Status 409 with no field error on `setupWorkspace` (its only non-field conflict) | read the technical code | The shared transport normalises unknown codes to `MSG06` and drops `WORKSPACE_ALREADY_SET_UP` |
| D26 | fe-foundation's route-table test still expects the 2.1 placeholder (Task 7) | Remove the 2.1 row, as its comment asks the building module to do; path added to `module.md` | leave the test failing | Same convention chatbot (12.1) and triage (10.1) followed |
| D27 | Developer for Task 8 after Codex hit its usage limit (until 14/11) | Claude `harnessclaude-developer` subagent with the same brief and rules | wait for Codex | Keeps KAN-119 moving; the Reviewer stays independent of the writer |
| D28 | Which Workspace `JiraServiceAccount.current()` reads in a worker job (evaluation, Task 6 CRITICAL) | `WorkerTenantService.currentWorkspaceId()` when present (optional bean), else `TenantScope`; AC-006.1 reworded | make `worker`'s `runInTenant` also bind `TenantScope` | `worker` is `completed`; the fix stays inside this module; a later platform tenant binding can replace both |
| D29 | Email-only update while the stored token cannot be decrypted (evaluation, Task 5) | 400 `MSG156` on `serviceAccountToken`, workspace id logged only; AC-005.5 extended | generic 500 | The Admin must re-enter the token after a key loss; MSG156 tells them which field |
| D30 | Jira check inside the `setup` transaction (evaluation, Task 4, backend/AGENTS.md §4) | Check before the transaction; link, persist and audit in a `TransactionTemplate`, like `update` | keep as is | No remote call while a DB transaction is open |
| D22 | Where the interim linker puts the `cloudId` (builder check) | Returned to the service and only logged (host, not secrets); nothing persists it until the catalog exists | add a `jira_cloud_id` column to the tenant table | The catalog owns `jira_cloud_id` (platform AC-001.15); a tenant column would duplicate it |

## Risk review (KAN-119, inline, Deep spec)

Verdict: **CAUTION** — go, with the mitigations below folded into the tasks.
- Secret leak (token in logs/toString/audit/errors) → AC-009.7 tests per type; `JiraAccount.toString` already hides it.
- SSRF via site address → AC-009.3 rule before any call; no redirects in the interim linker; tests with
  `http://`, `https://localhost`, `https://169.254.169.254`, `https://a.atlassian.net.evil.com`, `https://x@a.atlassian.net`,
  `https://a.atlassian.net:8443`.
- Key loss = all tokens undecryptable → documented in `.env.example`; decrypt failure returns empty (D16); the Admin
  re-enters the token in 2.2.
- Cross-tenant copy of ciphertext → AAD = workspace id (AC-009.5).
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

Open questions for the spec owner: none.

### Workspace information (KAN-119)

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-003 | Concurrency | Two Admins submit Setup at once | High | One row; the other gets 409 (unique singleton) | Task 4 |
| 2 | STORY-003 | Security | Site `https://169.254.169.254` / `localhost` / evil suffix | Critical | MSG61, no outbound call | Task 3 |
| 3 | STORY-003 | Input | Site with trailing `/`, upper case, spaces | Medium | Normalised to `https://<host>` | Task 3 |
| 4 | STORY-003 | External | Jira 401 / 403 / 500 / timeout | High | MSG156, nothing stored, no body logged | Task 2, Task 4 |
| 5 | STORY-003 | External | Account authenticates but lacks MODIFY_REPORTER | High | MSG156 | Task 2 |
| 6 | STORY-003 | External | tenant_info 404 or no cloudId / redirect | High | MSG61, nothing stored | Task 3 |
| 7 | STORY-003 | State | Linker refuses (another Workspace) | High | MSG153, no row | Task 4 |
| 8 | STORY-003 | State | Setup when already set up | High | 409, site unchanged | Task 4 |
| 9 | STORY-009 | Security | Token in logs / audit / response | Critical | Never present | Task 1, Task 4, Task 5 |
| 10 | STORY-009 | Security | Ciphertext copied to another Workspace id | High | Decrypt fails | Task 1 |
| 11 | STORY-009 | Config | Key missing / wrong length | High | Startup fails, names setting only | Task 1 |
| 12 | STORY-005 | Input | Blank token on update | High | Stored token kept; check uses it if the email changed | Task 5 |
| 13 | STORY-005 | External | Only company name changed while Jira is down | Medium | Saved, no Jira call | Task 5 |
| 14 | STORY-005 | Input | Body carries `jiraSiteUrl` | Medium | Ignored | Task 5 |
| 15 | STORY-006 | State | Not set up | High | `current()` empty | Task 6 |
| 16 | STORY-006 | Security | Tampered ciphertext | High | Error log without value, empty | Task 6 |
| 17 | STORY-007 | UX | Double click Save | Medium | One request | Task 7 |
| 18 | STORY-007 | UX | 409 response | Medium | No error, go to 2.2 | Task 7 |
| 19 | STORY-008 | UX | Token never shown, empty means keep | High | Field empty; PUT omits token | Task 8 |
| 20 | STORY-004 | Access | Non-Admin calls any endpoint | High | 403 | Task 4, Task 5 |
