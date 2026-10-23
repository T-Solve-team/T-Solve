---
module: be-foundation
date: 2026-10-23
owner: BA
---

# Spec: be-foundation

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-23 | STORY-001..STORY-004 | Initial spec from Report3 (GBR-28, System_Messages, UC-35, Architecture diagram) and the planning decisions Q1–Q7 | The backend base every module builds on (KAN-68); fixes BUG-001 |

## Requirements

### Functional
1. Every error the backend returns is an RFC 9457 `ProblemDetail` with the Report3 message code the web app shows
   (BUG-001; `backend/AGENTS.md` §5).
2. Spring Security knows the five Report3 roles; a user can hold several, their permissions add up and every user
   has Employee (GBR-28). Every endpoint is closed unless it declares its roles; the backend re-checks every
   request.
3. `GET /api/v1/me` returns the signed-in user with all their roles, Department, Departments, Projects, managed
   Spaces and whether the Workspace is set up, as the contract `frontend/openapi/tsolve-api.yaml` (`CurrentUser`)
   says. The data owned by later modules is plugged in through interfaces in `common` (decision Q1).
4. One audit service records the actions listed in `backend/AGENTS.md` §5 in the same transaction as the action;
   audit rows are never updated or deleted (UC-35 reads them).

### Non-Functional
1. Every acceptance criterion has a JUnit test whose method name cites its id (`…_AC_001_1`);
   `sdd.ts trace be-foundation` passes.
2. `mvn verify` (tests + `mvn spotless:check`) passes on every commit.
3. No new Maven dependency except Testcontainers for tests (`spring-boot-testcontainers`,
   `org.testcontainers:postgresql`, versions from the Spring Boot BOM); `backend/AGENTS.md` §8 is changed to allow
   database tests with Testcontainers where a criterion needs a real database (decision Q7). `mvn verify` then needs
   Docker running.
4. `common/` depends on no module (`backend/AGENTS.md` §1): later modules plug into `/me` and use the audit service;
   be-foundation never imports them.
5. No error body leaks a stack trace, an exception class name or an internal exception message.

## User Stories

### STORY-001: Errors as ProblemDetail with a message code
- As a web app developer, I want every backend error as `ProblemDetail` with a Report3 message code so that the web
  app shows the right message from its catalog and never shows backend text.
- Acceptance:
  - AC-001.1: WHEN a request fails with a `NotFoundException`, THE backend SHALL answer 404 with content type
    `application/problem+json` and a body with `status` and the exception's message code and parameters as `code`
    and `params`.
  - AC-001.2: WHEN a request fails with a `BusinessRuleException`, THE backend SHALL answer 409 with a
    `ProblemDetail` body with the exception's message code and parameters as `code` and `params`.
  - AC-001.3: WHERE a `BusinessRuleException` names a field, THE backend SHALL answer 409 with that field in
    `fieldErrors` (`field`, `code`, `params`), so the web app shows the message below the field (for example MSG11,
    MSG43).
  - AC-001.4: WHEN request validation fails, THE backend SHALL answer 400 with one `fieldErrors` entry per failed
    field: a missing required value → `MSG01` with `field_name`; a value over its length limit → `MSG02` with
    `field_name` and `max_length`; an invalid email → `MSG03`.
  - AC-001.5: IF a field fails a validation rule other than those in AC-001.4, THEN THE backend SHALL put that field
    in `fieldErrors` with `code` `MSG06`.
  - AC-001.6: IF a base exception carries no message code, THEN THE backend SHALL answer with its status and a
    `ProblemDetail` without `code`, with its message only in `detail` (the message-only constructor kept for
    existing callers, decision Q3).
  - AC-001.7: IF a request fails with an exception the handler does not know, THEN THE backend SHALL answer 500 with
    `code` `MSG06`, no `detail`, and log the exception.
  - AC-001.8: IF Spring MVC rejects a request itself, THEN THE backend SHALL answer with Spring's status and a
    `ProblemDetail` with `code` `MSG06` (unreadable body, wrong method, unsupported media type, missing parameter,
    unknown path).
  - AC-001.9: THE base exceptions SHALL live in `vn.tsolve.common.exception`, and the existing callers in
    `knowledge/` and `ticket/` SHALL compile against them unchanged except for their imports (BUG-001, decision Q3).

### STORY-002: Five roles and deny-by-default security
- As the team, we want the five Report3 roles in Spring Security with permissions that add up so that every
  endpoint declares who may call it and nothing is open by accident.
- Acceptance:
  - AC-002.1: THE backend SHALL define exactly five roles — `EMPLOYEE`, `TICKET_RESOLVER`, `PROJECT_MANAGER`,
    `KNOWLEDGE_MANAGER`, `ADMIN` — as constants usable in `@PreAuthorize`.
  - AC-002.2: WHEN a signed-in user is turned into a Spring Security authentication, THE backend SHALL grant
    `EMPLOYEE` and every role the user holds, even if the user's roles do not list `EMPLOYEE` (GBR-28).
  - AC-002.3: WHEN an endpoint allows several roles, THE backend SHALL let in a user who holds at least one of them.
  - AC-002.4: IF a request to `/api/**` comes with no signed-in user, THEN THE backend SHALL answer 401 with a
    `ProblemDetail` with `code` `MSG07`.
  - AC-002.5: IF access is denied to a signed-in user, THEN THE backend SHALL answer 403 with a `ProblemDetail` with
    `code` `MSG08` (the user has none of the endpoint's roles, or a service throws Spring's `AccessDeniedException`).
  - AC-002.6: IF an endpoint under `/api/**` has no `@PreAuthorize`, THEN THE backend SHALL still answer 401 to a
    request with no signed-in user.
  - AC-002.7: IF a request goes to any other path, THEN THE backend SHALL deny it (any path outside `/api/**`,
    `/actuator/health`, `/v3/api-docs/**` and the Swagger UI).
  - AC-002.8: THE backend SHALL answer `/actuator/health`, `/v3/api-docs/**` and the Swagger UI without a signed-in
    user (Docker health check; `npm run api:pull`; production turns the Swagger UI off by configuration).
  - AC-002.9: WHEN a browser sends a CORS preflight from an origin in `CORS_ALLOWED_ORIGINS`, THE backend SHALL
    answer it without a signed-in user.
  - AC-002.10: THE backend SHALL offer no sign-in of its own (no form login, no HTTP Basic, no generated password);
    sign-in comes from `identity` (decision Q2).
  - AC-002.11: THE backend SHALL require Spring's CSRF token on every `POST`, `PUT`, `PATCH` and `DELETE` to
    `/api/**`: it sets the cookie `XSRF-TOKEN` (readable by the web app) and checks the header `X-XSRF-TOKEN`
    (decision Q6).
  - AC-002.12: THE backend SHALL offer tests a way to sign in a user with any set of the five roles
    (`@WithTSolveUser`), so every module tests its role checks without a real sign-in.
  - AC-002.13: IF a `POST`, `PUT`, `PATCH` or `DELETE` to `/api/**` has no valid CSRF token, THEN THE backend SHALL
    answer 403 with a `ProblemDetail` with `code` `MSG07` and not run the endpoint (the user reloads or signs in
    again; MSG08 would wrongly say they lack permission).

### STORY-003: The session endpoint `/me`
- As a signed-in user, I want the web app to know who I am and everything my roles give me so that it opens my
  landing page, menus and screens.
- Acceptance:
  - AC-003.1: WHEN a signed-in user calls `GET /api/v1/me`, THE backend SHALL answer 200 with `user` (`id`,
    `fullName`, `email`) and `roles` taken from the signed-in user, `EMPLOYEE` included (AC-002.2).
  - AC-003.2: WHEN `/me` is answered, THE backend SHALL take `department` from the Department lookup, `projects`
    from the Project membership lookup, `managedSpaces` from the managed Space lookup and `workspaceReady` from the
    Workspace status lookup (Interface Contracts).
  - AC-003.3: WHEN `/me` is answered, THE backend SHALL set `departments` to the user's `department` (if any) plus the
    Department of each of their `projects`, each Department once, in that order.
  - AC-003.4: WHILE a lookup has no implementation yet (its module is not built), THE backend SHALL answer `/me` with
    `department` null, an empty list, or `workspaceReady` false for that part, and still answer 200.
  - AC-003.5: IF nobody is signed in, THEN THE backend SHALL answer `/me` with 401 and `code` `MSG07`.
  - AC-003.6: THE response of `/me` SHALL match the `CurrentUser` schema of `frontend/openapi/tsolve-api.yaml`
    (field names, nullability, `Role` and `ProjectRole` values); the contract is not changed.

### STORY-004: Audit events
- As an Admin, I want every listed action recorded once, with who did it to what and when, and never changed, so
  that the audit log (UC-35) can be trusted.
- Acceptance:
  - AC-004.1: THE backend SHALL list the audited actions as `AuditAction`: `APPROVE`, `REJECT`, `REQUEST_CHANGES`,
    `PUBLISH`, `UNPUBLISH`, `IMPORT`, `DELETE`, `ROLE_CHANGE`, `PERMISSION_CHANGE`, `TRACKER_CONFIGURATION_CHANGE`
    (`backend/AGENTS.md` §5).
  - AC-004.2: WHEN a module calls `AuditService.record` inside its transaction, THE backend SHALL store one audit
    event with the signed-in user's email as `actor`, the action, the resource type, the resource id and the current
    time (UTC).
  - AC-004.3: IF `record` is called outside a transaction, THEN THE backend SHALL throw and store nothing.
  - AC-004.4: IF `record` is called with no signed-in user, THEN THE backend SHALL throw and store nothing.
  - AC-004.5: WHEN the caller's transaction rolls back, THE backend SHALL keep no audit event of it.
  - AC-004.6: IF anything tries to update or delete a row of `audit_event`, THEN THE database SHALL reject it.
  - AC-004.7: THE `audit_event` table SHALL have `actor`, `action`, `resource_type`, `resource_id` and `created_at`,
    and no `result` column, through a new Liquibase changeset that keeps the rows already there (decision Q4).
  - AC-004.8: THE tests of AC-004.5, AC-004.6 and AC-004.7 SHALL run against a real PostgreSQL started by
    Testcontainers from the image `pgvector/pgvector:pg16`, with the Liquibase changelog applied as in production
    (decision Q7).

## Out of Scope
- Signing in and out (OIDC, the session cookie, `POST /api/v1/auth/logout`), user accounts and roles data, MSG09 and
  MSG10 → `identity` (UC-01–09).
- The Forge Invocation Token check for calls from the Jira App → the module that builds the Jira App API adds its
  own `SecurityFilterChain` (decision Q5).
- Implementing the `/me` lookups → `identity` (Department of a Project Manager), `project-integration` (Projects),
  `wiki` (managed Spaces), `workspace` (Workspace status).
- Reading the audit log, its filters and screens → `dashboard-audit` (UC-35).
- Choosing message codes for the existing `knowledge/` and `ticket/` exceptions → `ticket-repository` (decision Q3).
- Data-level permission checks ("Projects they manage only", "own Departments only") → each feature module, which
  throws `AccessDeniedException` (AC-002.5) or `NotFoundException`.
- A separate dev or demo login (decision Q2).
- Sending the `X-XSRF-TOKEN` header from the web app (`frontend/src/shared/api/http.ts`) → a quick change of
  `fe-foundation` (Trần Trung Hiếu), needed before `identity` turns on sign-in (decision Q6).

## Interface Contracts

### Exposed by this module
- **REST** `GET /api/v1/me` → `CurrentUser` (contract `frontend/openapi/tsolve-api.yaml`, `operationId` `getMe`).
- **Errors** (`vn.tsolve.common.exception`): `NotFoundException` (404), `BusinessRuleException` (409), each built
  with a message code, optional `params` and, for `BusinessRuleException`, an optional field; plus the message-only
  constructors kept for existing callers. Every error body follows the contract's `ProblemDetail` and `FieldError`.
- **Roles** (`vn.tsolve.common.security`): the five role constants; the signed-in user principal (`id`, `email`,
  `fullName`, roles) that `identity` puts in the security context at sign-in; `@WithTSolveUser` for tests.
- **Audit** (`vn.tsolve.common.audit`): `AuditService.record(AuditAction action, String resourceType,
  String resourceId)`.
- **Database**: the `audit_event` changeset; the Liquibase master changelog stays the one entry point.

### Required from other modules
Interfaces in `common`, each implemented by the module named; until then `/me` uses the empty answer (AC-003.4):
- Department lookup (`identity`): the Department of a Project Manager by user id, or none.
- Project membership lookup (`project-integration`): the user's Projects with Jira project key, Department and their
  role in each.
- Managed Space lookup (`wiki`): the Spaces the user manages as Knowledge Manager.
- Workspace status lookup (`workspace`): whether the Admin has entered the company information.

## Definition of Done
- [ ] Every acceptance criterion has a test whose method name cites its AC id; `sdd.ts trace be-foundation` passes
- [ ] `mvn verify` passes
- [ ] BUG-001's row in `docs/bug-log.md` is `Fixed` with its PR and test
- [ ] The owners of `identity`, `project-integration`, `wiki`, `workspace`, `ticket-repository` and `dashboard-audit`
      are told what they plug in or call

<!-- ACCEPTANCE CRITERIA: one testable statement per item, id AC-<story>.<n> (never reuse a
     removed id), in EARS form: THE <system> SHALL <response>, optionally led by
     WHEN <trigger>, WHILE <state>, IF <unwanted condition> THEN, or WHERE <feature is included>.
     A story with a WHEN criterion also gets an IF … THEN criterion for its failure path.
     OUT OF SCOPE: name what is excluded, so it is not built by accident; adding it later is a
     spec change (Changelog row, re-approval).
     OPEN QUESTIONS: where the answer is missing, write [NEEDS CLARIFICATION: <question>] and ask
     the user — never fill the gap with a guess. `bun harnessclaude/hooks/sdd.ts check <module>`
     fails while any remain.
     SPEC EDIT RULE: Any edit to this file that changes business logic, requirements, stories, or
     acceptance criteria MUST add a new row to the Changelog table above (Date, Story if
     applicable, Change summary, Reason). Once the user has approved the spec, any edit locks the
     module's code until they approve the new version (`sdd.ts approve <module>`). -->
