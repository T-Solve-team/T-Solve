---
module: be-foundation
date: 2026-10-23
---

# Design: be-foundation

Spec: `artifacts/03-modules/be-foundation/spec.md` (approved, version 26428535). Conventions: `backend/AGENTS.md`;
system decisions: `artifacts/03-modules/architecture.md`.

## Architecture Overview

Four parts of `vn.tsolve.common`, each with no dependency on any business module (`backend/AGENTS.md` §1):

```
request ──► CorsFilter ──► CsrfFilter ──► AuthorizationFilter ──► DispatcherServlet ──► controller
              │               │ 403 MSG07        │ 401 MSG07 / 403 MSG08                    │
              │               └──────────────────┴──► ProblemDetails (common/exception) ◄──┤ exceptions
              │                                                                            │ GlobalExceptionHandler
common/security   roles, the signed-in user, the filter chain, @WithTSolveUser (tests)     │
common/exception  base exceptions, GlobalExceptionHandler, ProblemDetails                  │
common/session    GET /api/v1/me ── SessionService ──► 4 lookups (implemented by later modules, optional)
common/audit      AuditService.record(…) ── JdbcTemplate INSERT in the caller's transaction ──► audit_events
```

- **Errors.** `GlobalExceptionHandler extends ResponseEntityExceptionHandler`, so every Spring MVC error is already
  a `ProblemDetail`; the handler only adds `code` (and `params`, `fieldErrors`). Security errors happen before the
  `DispatcherServlet`, so the entry point and the access-denied handler write the same body through one helper,
  `ProblemDetails`, as `application/problem+json`.
- **Security.** One `SecurityFilterChain`: `/actuator/health`, `/v3/api-docs`, `/v3/api-docs/**`,
  `/v3/api-docs.yaml` (what `npm run api:pull` downloads; `/v3/api-docs/**` does not match it), `/swagger-ui/**`,
  `/swagger-ui.html` permitted; `/api/**` authenticated; everything else `denyAll`. `@EnableMethodSecurity` for
  `@PreAuthorize`. No form login, no HTTP Basic, no `UserDetailsService`. CORS uses the existing `WebConfig`
  mapping (`http.cors(withDefaults())`). CSRF: cookie `XSRF-TOKEN` (not HTTP-only) and header `X-XSRF-TOKEN`,
  the Spring Security SPA setup. Session policy, OIDC and logout are left to `identity`.
- **Signed-in user.** `TSolveUser` (`UUID id`, email, fullName, roles) is the authentication principal. `identity` puts an
  `Authentication` with this principal in the security context at sign-in; until then only `@WithTSolveUser` does.
  `TSolveUser.authorities()` always holds `ROLE_EMPLOYEE` plus one `ROLE_<role>` per role (GBR-28).
- **Session.** `SessionController` → `SessionService` builds `CurrentUserResponse` from the principal and four
  lookups injected as `ObjectProvider<…>`: a missing implementation gives the empty answer (AC-003.4).
- **Audit.** `AuditServiceImpl` inserts with `JdbcTemplate`. Under the JPA transaction manager, JDBC uses the
  transaction's connection, so the row commits or rolls back with the caller's action (AC-004.5). It first checks
  `TransactionSynchronizationManager.isActualTransactionActive()` and the signed-in user, and throws
  `IllegalStateException` otherwise (AC-004.3, AC-004.4). It takes `workspace_id` from platform
  `TenantContext.requireWorkspaceId()`, which throws when no Workspace is bound (AC-004.9, AC-004.10); row-level
  security refuses the insert as the last guard (platform AC-003.3). The database refuses UPDATE, DELETE and
  TRUNCATE through a trigger (AC-004.6).

## Data Model

New Liquibase changeset `NNN-audit-events.sql`, author `be-foundation`. `NNN` is the next free number
when the branch merges: `002` is already used by `feat/KAN-31-import-ticket-csv` and `feat/ticket-review`, and two
changesets must not share a number on `main`. It runs after the changeset that gives `workspaces` its UUID id
(platform AC-001.1, owner of changeset 001) and after the platform changeset that sets up row-level security.
It follows the platform tenant-table convention for an append-only table (platform AC-001.8, KAN-196 Q2):

```sql
--changeset be-foundation:NNN-audit-events
DROP TABLE audit_event;                     -- dev rows only, with no Workspace to give them (AC-004.7)
CREATE TABLE audit_events (                 -- append-only: no updated_at
    id            UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID         NOT NULL REFERENCES workspaces (id),
    actor         VARCHAR(150) NOT NULL,
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50)  NOT NULL,
    resource_id   VARCHAR(200) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_workspace_id_created_at_idx ON audit_events (workspace_id, created_at);
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;
-- + the tenant policy of platform AC-003.1 and the grants to the application user, written exactly as the
--   platform changeset writes them for every tenant table (copied from it, not reinvented here)

--changeset be-foundation:NNN-audit-events-append-only splitStatements:false
CREATE FUNCTION audit_events_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER audit_events_no_update_delete BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_append_only();
CREATE TRIGGER audit_events_no_truncate BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION audit_events_append_only();
```

`created_at` is the database default: PostgreSQL's `now()` is the start time of the transaction, the moment of the
action. The index on `(workspace_id, created_at)` is the one the convention requires (first column
`workspace_id`); `dashboard-audit` adds the others its UC-35 filters need. The INSERT names `workspace_id` from
`TenantContext` and leaves `id` and `created_at` to the defaults. `audit_event` leaves the platform legacy
allow-list in the same PR (platform AC-001.11), or the convention guard test fails.

Response records (`common/session/dto`), named in the OpenAPI output as the contract names them
(`@Schema(name = …)`), so `npm run api:pull` keeps `CurrentUser`, `UserSummary`, `DepartmentRef`,
`ProjectMembership`, `SpaceRef`. Every field is required in the contract, so every record component carries
`@Schema(requiredMode = REQUIRED)`; `department` also carries `nullable = true` (required, may be null). Without
them springdoc marks no field required and Orval would generate every field of `CurrentUser` as optional:

| Record | Fields |
|--------|--------|
| `CurrentUserResponse` | `UserSummaryResponse user`, `List<Role> roles`, `DepartmentRef department` (nullable), `List<DepartmentRef> departments`, `List<ProjectMembership> projects`, `List<SpaceRef> managedSpaces`, `boolean workspaceReady` |
| `UserSummaryResponse` | `UUID id`, `String fullName`, `String email` |
| `DepartmentRef` | `UUID departmentId`, `String name` |
| `ProjectMembership` | `UUID projectId`, `String jiraProjectKey`, `UUID departmentId`, `String departmentName`, `ProjectRole roleInProject` |
| `SpaceRef` | `UUID spaceId`, `String name` |

springdoc writes a `UUID` component as `type: string, format: uuid`, the type the contract gives every id
(AC-003.6, fe-foundation Interface Contracts). The lookups take the user id as a `UUID`.

`Role`: `EMPLOYEE, TICKET_RESOLVER, PROJECT_MANAGER, KNOWLEDGE_MANAGER, ADMIN`. `ProjectRole`: `PROJECT_MANAGER, MEMBER`.
`AuditAction`: the ten values of AC-004.1.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Q1 Where `/me` gets the data of later modules | Lookup interfaces in `common/session/lookup`, implemented by `identity`, `project-integration`, `wiki`, `workspace`; injected as optional | `/me` in `identity`; be-foundation owns user/role tables | The data sits in modules above be-foundation; calling them would make cycles. Matches the module map (user, 23/10) |
| Q2 Sign-in before OIDC | None in production code; tests use `@WithTSolveUser` | Dev login under a `dev` profile; Google OIDC now | No auth hole can reach the VPS; OIDC is `identity`'s UC-01 (user, 23/10) |
| Q3 Package and existing callers | `common/error` → `common/exception`; only the imports of `knowledge/Solution.java`, `knowledge/SolutionService.java`, `ticket/TicketService.java` change; message-only constructors kept | Also give them MSG codes; keep `common/error` | Matches AGENTS.md §1 without guessing another module's codes (user, 23/10) |
| Merge with import-ticket-list's handler (29/10) | One `GlobalExceptionHandler` in `common/exception`; `MessageCodeException` (status, code, params) moves there unchanged and gets its own handler; a `NotFoundException` without code answers MSG04; `handleMaxUploadSizeExceededException` adds MSG28; `common/error` is deleted and the `ticket/` callers change their imports only | Keep both advices (same bean name: the app does not start); keep the approved AC-001.6 (import 404s lose MSG04) | KAN-31 refactored the old `common/error` handler in parallel; its contract (404 MSG04, 413 MSG28) keeps working (the user, 29/10: option A; AC-001.9–AC-001.12) |
| Q4 Audit schema and API | Changeset above (since 25/10 the `audit_events` table of row "Audit table (KAN-196 Q2)"); `record(AuditAction, String resourceType, String resourceId)`; actor = signed-in email | Add `actor_id` + `details JSONB`; keep the table | Report3 defines no audit fields; email works before `identity` and stays readable after a user leaves; a later overload adds details without breaking callers (user, 23/10) |
| Q5 Forge Invocation Token | Out of scope; the Jira App API module adds its own `SecurityFilterChain` | FIT check now; a placeholder deny chain | No path, app id or user mapping exists yet; deny-by-default already covers it (user, 23/10) |
| Q6 CSRF | Spring CSRF token on: cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`; CSRF failure → 403 MSG07 | `SameSite=Lax` only; leave to `identity` | Spring's safe default; the web app change is three lines (user, 23/10) |
| Q7 Tests that need a database | Testcontainers, image `pgvector/pgvector:pg16`, Liquibase applied; AGENTS.md §8 changed | Manual `psql` check; a Maven profile | An automated check of the append-only guarantee and of every changeset; Docker is already needed to run the app (user, 23/10) |
| Audit write | `JdbcTemplate` INSERT, no entity or repository | JPA entity `AuditEvent` + Spring Data repository in `common` | AGENTS.md §1 allows no entities in `common`; JDBC joins the caller's JPA transaction. `dashboard-audit` maps its own read model in `analytics` |
| Audit table (KAN-196 Q2) | Drop `audit_event` and create `audit_events` under the platform convention for an append-only table: UUID `id` (`gen_random_uuid()`), `workspace_id` with its foreign key and index, row-level security, `created_at`, no `updated_at`; `workspace_id` from `TenantContext.requireWorkspaceId()` in the INSERT; `audit_event` leaves the legacy allow-list | Rename and switch to UUID only, no `workspace_id` yet (the legacy list only shrinks, platform AC-001.13); keep `audit_event` on the legacy list until later; copy the old rows into a placeholder Workspace | The user, 25/10 (SPEC-P1-7 U1 = A, U2 = A): UUID ids and plural tables in every module, and a legacy table is converted when its owning module is built (platform AC-001.11); the old rows are dev data with no Workspace, and no Workspace exists after the changelog runs (platform AC-001.3) |
| Ids in `/me` and the principal (KAN-196 Q2) | `UUID` in `TSolveUser`, the four response records and the lookups' user id; springdoc writes `string` / `uuid` | Keep `Long` | The contract types every id as a UUID string (fe-foundation Interface Contracts; AC-003.6) |
| Role names in `@PreAuthorize` | `Roles` with `String` constants (`Roles.ADMIN = "ADMIN"`), used as `@PreAuthorize("hasRole('" + Roles.ADMIN + "')")`; `Role` enum for the principal and `/me` | Enum only (not usable in annotations); plain strings | Annotation values must be compile-time constants; a test keeps `Roles` and `Role` the same five names |
| OpenAPI schema of `/me` | `@Schema(requiredMode = REQUIRED)` on every component of the five records, `nullable = true` on `department`; a unit test reads the schemas with swagger-core's `ModelConverters` (no Spring context) and checks names, `required` lists and the nullable `department` against the contract | `@NotNull` on response records (cannot say "required but null" for `department`); no check, trust `api:pull` | AC-003.6: the generated types must stay as the contract says; springdoc's default OpenAPI 3.0 output writes `nullable: true`, which Orval turns into `DepartmentRef \| null` like the contract's `anyOf` |
| Optional lookups | `ObjectProvider<XxxLookup>.getIfAvailable()` | Default no-op beans with `@ConditionalOnMissingBean` | No extra classes; a module adds its bean and `/me` uses it |
| Validation → MSG code | Constraint `NotBlank`/`NotNull`/`NotEmpty` → MSG01 `{field_name}`; `Size` → MSG02 `{field_name, max_length}` (`max` from the constraint descriptor); `Email` → MSG03; other → MSG06. `field_name` = the request field name (the web app shows its own label). One entry per field; when one field fails several rules, MSG01 wins, then MSG02, MSG03, MSG06 | Spring's default `detail` text | AC-001.4/001.5; the web app's catalog renders the text |
| `AccessDeniedException` raised inside a controller or service | Handled in `GlobalExceptionHandler` → 403 MSG08 | Let it reach the catch-all | Method security throws inside the `DispatcherServlet`, so the advice sees it before Spring Security does; without its own handler the catch-all would make it a 500 |
| No generated password | `@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)` | Leave the default in-memory user | AC-002.10 |
| Test configuration | `backend/src/test/resources/config/application.yml` with non-secret test values for the placeholders (`SERVER_PORT`, `CORS_ALLOWED_ORIGINS`); the database test gets its connection from Testcontainers (`@ServiceConnection` on a `PostgreSQLContainer` bean, image `DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")`, else Testcontainers refuses the image); no `org.testcontainers:junit-jupiter` (`@Testcontainers`/`@Container` are not used), as NFR 3 allows only the two test dependencies | Properties on every test class | `classpath:/config/` overrides `application.yml` in tests only; no secret is committed |
| Swagger UI and CSRF | `springdoc.swagger-ui.csrf.enabled: true` in `application.yml` | Exempt Swagger from CSRF | "Try it out" sends the token like the web app |
| CSRF in Spring Security 6.3 | The SPA setup from the Spring Security reference: `CookieCsrfTokenRepository.withHttpOnlyFalse()`, a request handler that accepts the raw header value, and a filter that loads the token so the cookie is set on the first response; `csrf.spa()` instead if the managed version has it | Hand-written token logic | Documented recipe; nothing custom beyond it |

Decided in planning (builder check):
- The `/me` endpoint is `@PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")`, which every signed-in user has.
- `record` arguments must be non-null (`Objects.requireNonNull`, a programming error).
- The 500 log line is `log.error("Unhandled exception", e)` with no request body.
- The CSRF filter runs before authorization, so a `POST` with no signed-in user and no token is a CSRF failure
  first. The access-denied handler answers a CSRF failure with 401 MSG07 when nobody is signed in and 403 MSG07
  when someone is; AC-002.4 and AC-002.13 both hold.
- A denied path (AC-002.7) gives 401 MSG07 with no signed-in user (the entry point) and 403 MSG08 with one.
- `TSolveUser.current()` returns `Optional<TSolveUser>`: empty when the principal is anonymous or of another type.
- `/me` lists `roles` in the `Role` declaration order (`EMPLOYEE` first).

## File Structure

```
backend/
├── pom.xml                                         + spring-boot-testcontainers, testcontainers postgresql (test)
├── AGENTS.md                                       §8: database tests with Testcontainers where a criterion needs them
└── src/
    ├── main/java/vn/tsolve/
    │   ├── TSolveApplication.java                  exclude UserDetailsServiceAutoConfiguration
    │   ├── knowledge/Solution.java, SolutionService.java    import vn.tsolve.common.exception.* only
    │   ├── ticket/exception/*, ticket/service/impl/Import*ServiceImpl.java   import only
    │   └── common/
    │       ├── config/WebConfig.java               (unchanged)
    │       ├── exception/                          (moved from common/error; common/error deleted)
    │       │   ├── CodedException.java             abstract: code, params, field
    │       │   ├── MessageCodeException.java       abstract: status, code, params (from import-ticket-list)
    │       │   ├── NotFoundException.java          404
    │       │   ├── BusinessRuleException.java      409, optional field
    │       │   ├── ProblemDetails.java             builds ProblemDetail + writes it to a servlet response
    │       │   └── GlobalExceptionHandler.java     extends ResponseEntityExceptionHandler
    │       ├── security/
    │       │   ├── Role.java  Roles.java  TSolveUser.java
    │       │   ├── SecurityConfig.java             filter chain, CSRF, entry point, access-denied handler
    │       │   └── (CSRF SPA handler classes if the recipe needs them)
    │       ├── session/
    │       │   ├── controller/SessionController.java
    │       │   ├── service/SessionService.java  service/impl/SessionServiceImpl.java
    │       │   ├── lookup/DepartmentLookup.java  ProjectMembershipLookup.java  ManagedSpaceLookup.java  WorkspaceStatusLookup.java
    │       │   └── dto/CurrentUserResponse.java  UserSummaryResponse.java  DepartmentRef.java  ProjectMembership.java  SpaceRef.java  ProjectRole.java
    │       └── audit/
    │           ├── AuditAction.java  AuditService.java
    │           └── impl/AuditServiceImpl.java
    ├── main/resources/
    │   ├── application.yml                         + springdoc.swagger-ui.csrf.enabled
    │   └── db/changelog/db.changelog-master.xml  changes/NNN-audit-events.sql
    └── test/
        ├── resources/config/application.yml
        └── java/vn/tsolve/common/
            ├── exception/GlobalExceptionHandlerTest.java
            ├── security/WithTSolveUser.java  WithTSolveUserSecurityContextFactory.java
            │            RolesTest.java  TSolveUserTest.java  SecurityConfigTest.java
            ├── session/SessionServiceImplTest.java  SessionControllerTest.java  CurrentUserSchemaTest.java
            ├── audit/AuditServiceImplTest.java  AuditEventDatabaseTest.java
            └── TestcontainersConfiguration.java
docs/bug-log.md                                      BUG-001 → Fixed
```

## External Dependencies
- Spring Security 6.3 (already in `pom.xml`): filter chain, method security, CSRF.
- Testcontainers (new, test scope, version from the Spring Boot BOM): PostgreSQL with pgvector for the database
  tests (decision Q7).
- Nothing new at runtime.

## Edge Cases & Risks

Dimensions analyzed: User types, Input extremes, Timing, Scale, State transitions, Error cascades, Authorization,
Data integrity, Integration, Compliance · Skipped: Environment (backend only, no UI), Business logic (no amounts,
quotas or workflow here).

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-002 | Authorization | `/api/**` endpoint with no `@PreAuthorize`, no signed-in user | Critical | 401 MSG07 | Task 2 |
| 2 | STORY-002 | Authorization | `POST` with no or a wrong `X-XSRF-TOKEN` | Critical | 403 MSG07, endpoint not run | Task 2 |
| 3 | STORY-002 | Authorization | `@PreAuthorize` denies inside the `DispatcherServlet` | High | 403 MSG08, not 500 from the catch-all | Task 1, Task 2 |
| 4 | STORY-002 | Authorization | Signed-in user calls a path outside `/api/**` and the public paths | High | Denied (403) | Task 2 |
| 5 | STORY-002 | User types | Principal whose roles are empty, or list `EMPLOYEE` already | Medium | Authorities `ROLE_EMPLOYEE` (+ others), no duplicates | Task 2 |
| 6 | STORY-002 | User types | User with `TICKET_RESOLVER` + `KNOWLEDGE_MANAGER` calls an endpoint for `KNOWLEDGE_MANAGER`, `ADMIN` | Medium | Allowed | Task 2 |
| 7 | STORY-002 | Authorization | `GET` with no CSRF token | Medium | Allowed (safe method); response sets `XSRF-TOKEN` | Task 2 |
| 8 | STORY-002 | Integration | Preflight from an origin not in `CORS_ALLOWED_ORIGINS` | Medium | 403, no CORS headers | Task 2 |
| 9 | STORY-001 | Compliance | Unexpected exception whose message holds personal data | Critical | 500 MSG06, no `detail`, message only in the server log | Task 1 |
| 10 | STORY-001 | Input extremes | One field fails `@NotBlank` and `@Size`; two fields fail | Medium | One entry per field; MSG01 wins on the same field | Task 1 |
| 11 | STORY-001 | Input extremes | `@Size(max = 150)` exceeded | Medium | MSG02 with `max_length` 150 and `field_name` | Task 1 |
| 12 | STORY-001 | Integration | Malformed JSON body; unknown path for a signed-in user | Low | 400 / 404 with MSG06 | Task 1 |
| 13 | STORY-001 | Integration | `BusinessRuleException` naming field `email` with MSG11 | Medium | 409, `fieldErrors: [{field: email, code: MSG11}]` | Task 1 |
| 14 | STORY-003 | Scale | No lookup implemented, user with no Projects | High | 200; `department: null` present (not omitted), empty lists, `workspaceReady: false` | Task 3 |
| 15 | STORY-003 | Data integrity | Project Manager's Department also appears through their Projects | Medium | Listed once, `department` first | Task 3 |
| 16 | STORY-003 | Error cascades | A lookup throws | Medium | 500 MSG06 (no partial answer) | Task 3 |
| 17 | STORY-003 | Integration | `npm run api:pull` after this module | Medium | Schemas named `CurrentUser`, `UserSummary`, `DepartmentRef`, `ProjectMembership`, `SpaceRef`, with the contract's `required` lists and `department` nullable | Task 3 |
| 18 | STORY-004 | State transitions | Caller's transaction rolls back after `record` | Critical | No audit row | Task 4 |
| 19 | STORY-004 | Data integrity | `UPDATE`, `DELETE` or `TRUNCATE` on `audit_events` | Critical | Database error; rows unchanged | Task 4 |
| 20 | STORY-004 | State transitions | `record` with no transaction | High | `IllegalStateException`, no row | Task 4 |
| 21 | STORY-004 | User types | `record` with no signed-in user (a job, a test) | High | `IllegalStateException`, no row | Task 4 |
| 22 | STORY-004 | Data integrity | Rows already in `audit_event` when the changeset runs | High | Dropped with the table: dev data with no Workspace (AC-004.7; was "kept" before KAN-196 Q2) | Task 4 |
| 23 | STORY-004 | Input extremes | `resourceId` longer than 200 characters | Medium | Database error rolls back the action with it (no action without its audit) | Task 4 |
| 24 | STORY-004 | Timing | Two actions audited at the same time | Low | Two independent rows | — (plain inserts) |
| 25 | STORY-004 | Compliance | Actor email in the audit row | Low | Stored for UC-35; never written to the log | Task 4 (review) |
| 26 | STORY-004 | User types | `record` in a transaction with a signed-in user but no Workspace bound (a thread started without `runIn`) | High | Throws, no row (AC-004.10); row-level security would refuse it anyway | Task 4 |
| 27 | STORY-004 | Authorization | An action audited while Workspace A is bound, then rows read while Workspace B is bound | Critical | The row carries A's id (`record` takes no Workspace argument); the read in B sees none of A's rows (AC-004.9, platform AC-003.1) | Task 4 |

Open questions for the spec owner: none.

## Risk Review — 2026-10-23 — Verdict: CAUTION

Agreements: deny-by-default with one chain is right; JDBC audit in the caller's transaction is the simplest way to
meet "same transaction"; the database trigger is the only real guarantee of append-only; the lookups keep `common`
free of module dependencies.

| Topic | Architect | Security | Performance | User | Devil's advocate | Resolution |
|-------|-----------|----------|-------------|------|------------------|------------|
| `TSolveUser` as principal | Couples `identity`'s sign-in to a `common` type | Good: one place builds authorities | — | — | `identity` may need an `OidcUser` principal | Read the user only through `TSolveUser.current()`; `identity` may wrap it. Tell its owner now |
| CSRF on now | Fine | Required for a cookie session | — | Web app gets 403 MSG07 until it sends the header | Nobody can sign in yet, so nothing breaks today | Web app quick change before `identity` turns on sign-in (spec Out of Scope) |
| Existing `knowledge`/`ticket` endpoints closed | Correct by rule | Good | — | Nobody calls them (web app on mocks) | They break manual testing on other branches | Tell `ticket-repository` and `ticket-review` owners: add `@PreAuthorize` when they build |
| Testcontainers | Real DB tests for later modules (pgvector) | — | `mvn verify` +10–20 s | Docker must run for tests | Manual check is enough for one trigger | User chose it (Q7); AGENTS.md says so |
| Changeset number | Shared folder, parallel branches | — | — | — | — | Take the next free number at merge |
| `/me` lookups | Four port interfaces in `common` | — | Up to four queries per `/me`; called once per page load | — | One port per owning module is the minimum | Accepted |

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| A controller without `@PreAuthorize` is open | Critical | `/api/**` authenticated in the chain + test (row 1) | Task 2 |
| CSRF bypass on state-changing calls | Critical | Spring CSRF on + tests (rows 2, 7) | Task 2 |
| Audit row survives a rolled-back action, or is edited | Critical | Same transaction + trigger + database tests (rows 18, 19) | Task 4 |
| 403 from method security turns into 500 | High | Explicit `AccessDeniedException` handler (row 3) | Task 1 |
| `identity` cannot use `TSolveUser` as is | High | One accessor; agree with the `identity` owner before they plan; a change goes through this spec | Task 2 (notify) |
| The new audit table breaks the platform convention or its guard test | High | `audit_events` written to platform AC-001.8, policy and grants copied from the platform changeset, `audit_event` off the legacy list in the same PR; the guard test runs in `mvn verify` (row 22 and KAN-196 Q2; replaces the 23/10 risk "Migration loses audit rows") | Task 4 |
