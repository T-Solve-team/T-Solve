---
module: be-foundation
date: 2026-10-23
total_tasks: 4
owner: Dev
---

# Tasks: be-foundation — iteration 1

Spec: `artifacts/03-modules/be-foundation/spec.md` · Design: `artifacts/04-plans/be-foundation/design.md`

One task = one Jira issue = one branch `feat/KAN-<n>-be-foundation-<desc>` from an up-to-date `main` = one PR,
merged in this order. Every test method name cites the AC id it proves (`…_AC_001_1`). Each task ends with
`mvn spotless:apply` and `mvn verify` in `backend/` (root `CLAUDE.md`), then stops for the user's commit.

## Task 1: Errors as ProblemDetail with a message code

**Story:** STORY-001
**Feature:** problem-errors
**Jira:** KAN-? (to create) · branch `feat/KAN-<n>-be-foundation-problem-errors`
**Description:** Move the base exceptions to `vn.tsolve.common.exception`; `CodedException` (code, params, field)
with `NotFoundException` (404) and `BusinessRuleException` (409, optional field), keeping the message-only
constructors; `ProblemDetails` helper; `GlobalExceptionHandler extends ResponseEntityExceptionHandler`: `code` and
`params` on every body, validation → `fieldErrors` with MSG01/02/03/06 (precedence in the design), Spring MVC errors
→ MSG06, `AccessDeniedException` → 403 MSG08, anything else → 500 MSG06 without `detail`, logged. Update the imports
of the three existing callers. BUG-001's row → `Fixed` with the PR and the test.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/common/exception/CodedException.java`, `ProblemDetails.java`
- Move + modify: `common/error/NotFoundException.java`, `BusinessRuleException.java`, `GlobalExceptionHandler.java`
  → `common/exception/`
- Modify (imports only): `backend/src/main/java/vn/tsolve/knowledge/Solution.java`, `knowledge/SolutionService.java`,
  `ticket/TicketService.java`
- Modify: `docs/bug-log.md`
- Test: `backend/src/test/java/vn/tsolve/common/exception/GlobalExceptionHandlerTest.java` (standalone MockMvc with a
  test-only controller; no Spring context); one test also named `…_BUG_001`
**Covers:** AC-001.1–AC-001.9; edge cases 3, 9, 10, 11, 12, 13
**Dependencies:** none
**Estimated size:** medium

---

## Task 2: Five roles, deny-by-default security and CSRF

**Story:** STORY-002
**Feature:** roles-security
**Jira:** KAN-? (to create) · branch `feat/KAN-<n>-be-foundation-roles-security`
**Description:** `Role` enum and `Roles` constants; `TSolveUser` principal (`UUID id`, KAN-196 Q2) with `authorities()` (always
`ROLE_EMPLOYEE`, no duplicates) and `TSolveUser.current()`; `SecurityConfig`: public paths, `/api/**` authenticated,
everything else denied, `@EnableMethodSecurity`, CORS from `WebConfig`, CSRF SPA setup, entry point 401 MSG07,
access-denied handler 403 MSG07 for a CSRF failure and MSG08 otherwise, through `ProblemDetails`; exclude
`UserDetailsServiceAutoConfiguration`; `springdoc.swagger-ui.csrf.enabled`; `@WithTSolveUser` for tests; test
`config/application.yml`; `@WithTSolveUser` gives a fixed UUID id by default. Tell the `identity` owner how sign-in must fill the security context, and the
`ticket-repository` / `ticket-review` owners that their endpoints now need `@PreAuthorize`.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/common/security/Role.java`, `Roles.java`, `TSolveUser.java`,
  `SecurityConfig.java` (+ the CSRF SPA classes if the recipe needs them)
- Modify: `backend/src/main/java/vn/tsolve/TSolveApplication.java`, `backend/src/main/resources/application.yml`
- Create (test): `backend/src/test/java/vn/tsolve/common/security/WithTSolveUser.java`,
  `WithTSolveUserSecurityContextFactory.java`, `backend/src/test/resources/config/application.yml`
- Test: `backend/src/test/java/vn/tsolve/common/security/RolesTest.java`, `TSolveUserTest.java`,
  `SecurityConfigTest.java` (`@WebMvcTest` with a test-only controller: 401, 403, several roles, CSRF, CORS
  preflight, public paths incl. `/v3/api-docs.yaml`, denied paths)
**Covers:** AC-002.1–AC-002.13; edge cases 1, 2, 3, 4, 5, 6, 7, 8
**Dependencies:** Task 1 (`ProblemDetails`)
**Estimated size:** medium

---

## Task 3: The session endpoint `/me`

**Story:** STORY-003
**Feature:** me-session
**Jira:** KAN-? (to create) · branch `feat/KAN-<n>-be-foundation-me-session`
**Description:** The four lookup interfaces; DTO records with the contract's schema names, every component
`@Schema(requiredMode = REQUIRED)` and `department` also `nullable = true`; `SessionService` /
`SessionServiceImpl` (user and roles from `TSolveUser.current()`, optional lookups through `ObjectProvider`,
`departments` = `department` then each Project's Department, once each); `SessionController` `GET /api/v1/me`
(`operationId` `getMe`, `@PreAuthorize` EMPLOYEE). Every id component is a `UUID` (design "Ids in `/me` and the
principal"), so the generated schema matches the contract once fe-foundation Task 10 has made its ids UUID strings
(AC-003.6); the lookups take the user id as a `UUID`.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/common/session/controller/SessionController.java`,
  `service/SessionService.java`, `service/impl/SessionServiceImpl.java`, `lookup/DepartmentLookup.java`,
  `lookup/ProjectMembershipLookup.java`, `lookup/ManagedSpaceLookup.java`, `lookup/WorkspaceStatusLookup.java`,
  `dto/CurrentUserResponse.java`, `dto/UserSummaryResponse.java`, `dto/DepartmentRef.java`,
  `dto/ProjectMembership.java`, `dto/SpaceRef.java`, `dto/ProjectRole.java`
- Test: `backend/src/test/java/vn/tsolve/common/session/SessionServiceImplTest.java` (Mockito, lookups present and
  missing), `SessionControllerTest.java` (`@WebMvcTest`: 200 with every contract field incl. `department: null`, 401),
  `CurrentUserSchemaTest.java` (swagger-core `ModelConverters`: schema names, `required` lists, nullable
  `department`, ids `string` / `uuid`, as in the contract)
**Covers:** AC-003.1–AC-003.6; edge cases 14, 15, 16, 17
**Dependencies:** Task 2 (`TSolveUser`, `@WithTSolveUser`, security); fe-foundation Task 10 (the contract's UUID ids)
**Estimated size:** small

---

## Task 4: Audit events

**Story:** STORY-004
**Feature:** audit-writer
**Jira:** KAN-? (to create) · branch `feat/KAN-<n>-be-foundation-audit-writer`
**Description:** `AuditAction`; `AuditService` / `AuditServiceImpl` (`JdbcTemplate` INSERT with `workspace_id` from
`TenantContext.requireWorkspaceId()`; throws without a transaction, a signed-in user or a bound Workspace; non-null
arguments); the changeset that drops `audit_event` and creates `audit_events` under the platform convention (design
Data Model; KAN-196 Q2), and `audit_event` taken off the platform legacy allow-list of the convention guard test
(platform AC-001.11); the changeset (next free number at merge) included in the
master changelog; the two Testcontainers test dependencies (`spring-boot-testcontainers`,
`org.testcontainers:postgresql`; no `junit-jupiter`) and `TestcontainersConfiguration` (a `@ServiceConnection`
`PostgreSQLContainer` bean, image `pgvector/pgvector:pg16` marked `asCompatibleSubstituteFor("postgres")`); `backend/AGENTS.md` §8 allows
database tests with Testcontainers; tell the `dashboard-audit` owner the table and the API.
**Files:**
- Create: `backend/src/main/java/vn/tsolve/common/audit/AuditAction.java`, `AuditService.java`,
  `impl/AuditServiceImpl.java`
- Create: `backend/src/main/resources/db/changelog/changes/NNN-audit-events.sql`
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`, `backend/pom.xml`, `backend/AGENTS.md`
- Test: `backend/src/test/java/vn/tsolve/common/audit/AuditServiceImplTest.java` (unit: no transaction, no user,
  values written), `AuditEventDatabaseTest.java` (Testcontainers, as the application user with a Workspace bound through
  `TenantContext.runIn`: row stored with that Workspace, rollback leaves none, UPDATE / DELETE / TRUNCATE refused,
  columns, `audit_event` gone, no Workspace bound refused), `backend/src/test/java/vn/tsolve/common/TestcontainersConfiguration.java`
**Covers:** AC-004.1–AC-004.10; edge cases 18, 19, 20, 21, 22, 23, 25, 26, 27
**Dependencies:** Task 2 (`TSolveUser.current()`); `platform` STORY-001…STORY-003 merged (`workspaces` with UUID
ids, `TenantContext`, row-level security setup, the guard test and its allow-lists)
**Estimated size:** medium
