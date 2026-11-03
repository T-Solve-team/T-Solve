---
module: tracker
date: 2026-10-21
---

# Design: tracker

Spec: `artifacts/03-modules/tracker/spec.md` · Module: `artifacts/03-modules/tracker/module.md`
Conventions: `backend/AGENTS.md` (package-by-module then layer, service interfaces, DTO records, ProblemDetail + MSG codes).
Database: "T-Solve Database Tables" (31/10), `01_catalog.sql`, `02_tenant.sql` — one tenant database per Workspace.

> **Rewritten 2026-11-01** for one database per tenant. Removed: `jira_site`, `issue_event`, `@TenantId` /
> `WorkspaceContext` / `WorkspaceTenantResolver`, the system token store (`TokenCipher`, `TSOLVE_TOKEN_KEY`),
> the event worker, the clean-up job and site registration. The earlier version is in git history
> (commit `07475ec`).

## Architecture Overview

```
Forge trigger ──POST + FIT──────────────►  [JiraAppSecurityConfig: FIT filter chain, /api/v1/jira-app/**]
Issue Panel  ──invokeRemote + FIT (+ system token)─►      │ JiraAppPrincipal(cloudId, accountId)
                                                          ▼
       JiraAppEventController ─► IssueCreatedService.receive(payload)
                                   ├─ ProjectService.findActiveIdByJiraProjectKey(key)   (module project)
                                   │     none → 202, nothing stored (AC-003.8)
                                   └─ insert suggestion_result(project_id, jira_issue_key) → 202
                                         unique violation (uq_suggestion_issue) → 202 (AC-003.4)
       JiraAppIssueController ─► JiraPanelService (panel, refresh, draft, submit, answer)   — later tasks
                                   └─ JiraClient with the request's system token (never stored)

discovery (not tracker) ─► picks the tenant database for the request before the controller runs
```

- **One tenant database, already chosen.** The tracker runs as if the request is already connected to the right
  tenant database (Non-Functional 8). Tenant tables have no Workspace ID, so entities carry none and there is no
  Hibernate multi-tenancy. Locally the single datasource of `application.yml` is the tenant database.
- **FIT chain.** `JiraAppSecurityConfig` keeps its `@Order(1)` chain with `securityMatcher("/api/v1/jira-app/**")`:
  stateless, CSRF off, `oauth2ResourceServer().jwt()` with the Nimbus `JwtDecoder` (RS256 only). Validators check
  `iss`, `aud` = app id and that `context.cloudId` is present; any failure is 401 MSG08. The converter builds
  `JiraAppPrincipal(cloudId, accountId)` with `ROLE_JIRA_APP` — it no longer looks the cloudId up (that was
  AC-001.6, now discovery). JWKS fetch failure → 503 MSG06 (AC-001.8).
- **Receive does everything in the request.** No background job: validate the body, look up the Project, insert
  one row, answer. One short transaction, no outbound call (Non-Functional 1).
- **Dedupe by the database.** `suggestion_result.uq_suggestion_issue UNIQUE (jira_issue_key)`: a Forge retry hits
  the constraint; `saveAndFlush` in the repository's own transaction, catch only that constraint → 202.
- **Who fills the row later.** The AI Solution Suggestion owner updates the row (`sources_used`,
  `suggested_solution`) — they must update, not insert. Flag in the PR.
- **Fallback chain.** `common/security/DefaultSecurityConfig` stays until `identity` replaces it.

## Data Model

Tables as in `02_tenant.sql`. Until the team turns the whole tenant schema into Liquibase, a **temporary**
changeset creates only what the tracker needs (user, 2026-11-01). It replaces `002-jira-issue-events`, which ran
only on developer databases and never left this branch (not on `main`).

`backend/src/main/resources/db/changelog/changes/002-tenant-project-suggestion.sql`
(id `002-tenant-project-suggestion`, author `tracker`):

```sql
-- Temporary: copied from 02_tenant.sql (31/10) until the team's tenant schema changeset replaces it.
CREATE TABLE department (
    department_id       BIGSERIAL PRIMARY KEY,
    default_project_id  BIGINT,
    name                VARCHAR(100) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_department_name UNIQUE (name)
);

CREATE TABLE project (
    project_id        BIGSERIAL PRIMARY KEY,
    department_id     BIGINT       NOT NULL REFERENCES department (department_id),
    jira_project_key  VARCHAR(20)  NOT NULL,
    field_mapping     JSONB,
    done_status       VARCHAR(100),
    review_range      INT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_project_jira_key UNIQUE (jira_project_key)
);
CREATE INDEX idx_project_department ON project (department_id);

ALTER TABLE department
    ADD CONSTRAINT fk_department_default_project
    FOREIGN KEY (default_project_id) REFERENCES project (project_id);

CREATE TABLE suggestion_result (
    suggestion_id       BIGSERIAL PRIMARY KEY,
    project_id          BIGINT      NOT NULL REFERENCES project (project_id),
    jira_issue_key      VARCHAR(50) NOT NULL,
    sources_used        JSONB       NOT NULL DEFAULT '[]',
    suggested_solution  TEXT,
    reporter_answer     VARCHAR(5),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_suggestion_issue UNIQUE (jira_issue_key),
    CONSTRAINT ck_suggestion_answer CHECK (reporter_answer IN ('YES', 'NO'))
);
CREATE INDEX idx_suggestion_project ON suggestion_result (project_id, created_at);
```

- The `workspace` table of `001-init-core-schema` collides with the new tenant `workspace` table; that is for the
  team's schema change, not this changeset (it creates no `workspace`).
- `answered_at` (PDF) is not in `02_tenant.sql`; AC-010.2 needs it — ask the DB owner to add it before Task 13.
- Developer databases that ran the old `002-jira-issue-events`: drop `issue_event`, `jira_site` and delete that
  row from `databasechangelog` before starting the app.

Entities (no Workspace ID):
- `project` module: `Project` (`project_id`, `jira_project_key`, `status`; other columns not mapped until needed —
  `ddl-auto: validate` checks mapped columns only).
- `tracker` module: `SuggestionResult` (all columns; `sources_used` as JSON string via `@JdbcTypeCode(SqlTypes.JSON)`).

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Event delivery | Forge trigger → remote endpoint directly | Forge function relays with Async Events | Forge retries for free (4×); logic stays in testable Java (plan Q1) |
| Where a new issue is stored | `suggestion_result` row (Project + issue key) | own `jira_issue` table; `ticket` table | Table already in the DB design for "one row per issue, dedupe Forge retries"; `ticket` needs a Solution (user, 2026-11-01) |
| Project key | `issue.fields.project.key` from the payload | cut from the issue key | User choice 2026-11-01; confirm the field on the first real delivery (step 4) |
| Unknown project | 202, nothing stored | 4xx | Forge must not retry; issues of non-T-Solve projects are normal |
| Tenant | Request already runs in its tenant DB; no Workspace ID | `@TenantId` (2026-10-27) | One DB per Workspace (31/10); discovery picks the DB (user) |
| cloudId check | Not in tracker (discovery) | env `TSOLVE_JIRA_CLOUD_ID` | User, 2026-11-01; until discovery exists any site with the app is accepted |
| System token | Not requested for events; panel uses it in-request only | store encrypted for a worker | No background job (user, 2026-11-01) |
| Reading Project | Minimal `project` module with `ProjectService` | native query in tracker | AGENTS: other modules only through their `service/` (user, 2026-11-01) |
| Schema | Temporary changeset (department, project, suggestion_result) | wait for the team's tenant changeset | App must run for the demo (user, 2026-11-01) |
| FIT verification | Spring OAuth2 Resource Server (Nimbus, JWKS URI, custom validators) | jjwt + hand-written filter | Least hand-written security code; JWKS caching built in |
| ADF → text | tracker converts description and comments (`AdfTextExtractor`) | pass raw ADF | Other packages stay tracker-agnostic |
| Jira HTTP client | Spring `RestClient`, connect 5 s / read 10 s | WebClient | In spring-web already |

## File Structure

```
backend/src/main/java/vn/tsolve/common/
├── config/        TrackerProperties (app id, JWKS URI)                       — remove token key, worker timings
└── security/      JiraAppSecurityConfig, FitJwtDecoderConfig, FitValidators, FitAuthenticationConverter,
                   JiraAppPrincipal(cloudId, accountId), DefaultSecurityConfig  — delete JiraSiteLookup,
                   WorkspaceContext; delete common/config/WorkspaceTenantResolver
backend/src/main/java/vn/tsolve/project/                                       — new, minimal
├── service/       ProjectService (Optional<Long> findActiveIdByJiraProjectKey(String))
│   └── impl/      ProjectServiceImpl
├── repository/    ProjectRepository
└── entity/        Project, ProjectStatus
backend/src/main/java/vn/tsolve/tracker/
├── controller/    JiraAppEventController (issue-created only, no system token header), JiraAppIssueController (later)
├── service/       IssueCreatedService, JiraPanelService (later)
│   └── impl/      IssueCreatedServiceImpl, JiraPanelServiceImpl (later)
├── client/        TicketTrackerClient, JiraClient, AdfTextExtractor          — panel use (later)
├── repository/    SuggestionResultRepository
├── entity/        SuggestionResult
└── dto/           IssueCreatedPayload (issue.key, issue.fields.project.key), panel DTOs (later)
   delete: entity/IssueEvent*, entity/JiraSite, repository/IssueEventRepository, repository/JiraSiteRepository,
           service/IssueEventService(+Impl), service/impl/JiraSiteServiceImpl, service/impl/TokenCipher,
           exception/TokenUnreadableException
backend/src/main/resources/db/changelog/changes/002-tenant-project-suggestion.sql  (replaces 002-jira-issue-events.sql)
backend/src/test/java/vn/tsolve/{common/security,project,tracker}/…  (test names cite AC ids)
frontend/openapi/tsolve-api.yaml   (issue-created: payload with issue.fields.project.key, no system token header,
                                    remove issue-updated)
.env.example                       (remove TSOLVE_TOKEN_KEY)
jira-app/TSolve_JiraApp/manifest.yml (remove auth.appSystemToken on the event endpoint and scope read:app-system-token)
```

## External Dependencies
- `spring-boot-starter-oauth2-resource-server`: FIT verification. **New dependency** — team approves in the PR.
- Atlassian Forge JWKS `https://forge.cdn.prod.atlassian-dev.net/.well-known/jwks.json` (configurable).
- Jira Cloud REST API v3 — panel calls only, with the request's system token.
- Discovery (other owner): connects each Jira App API request to its tenant database.

## Edge Cases & Risks

Dimensions analyzed: 1 User types, 2 Input extremes, 3 Timing, 7 Error cascades, 8 Authorization,
9 Data integrity, 10 Integration, 11 Compliance · Skipped: 4 Scale — one insert per event; 5 State transitions —
no state machine left in receive; 6 Environment — server-to-server; 12 Business logic — no amounts.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | Authorization | FIT signed by a valid Atlassian key but for another Forge app (`aud` differs) | Critical | 401 MSG08 | Task 3 |
| 2 | STORY-001 | Authorization | FIT with `alg: none` or HS256 signed with the public key | Critical | 401 — decoder accepts RS256 only | Task 3 |
| 3 | STORY-001 | Authorization | Valid FIT without `context.cloudId` | High | 401 MSG08 (AC-001.5) | Task 3 |
| 4 | STORY-001 | Integration | Atlassian rotates keys; FIT has a new `kid` | High | One JWKS refresh, then accepted | Task 3 |
| 5 | STORY-001 | Error cascades | JWKS endpoint down, no cached key matches | High | 503 MSG06, error type recorded | Task 3 |
| 6 | STORY-001 | Timing | Server clock 30 s behind Atlassian (`nbf` in the future) | Medium | Accepted within 60 s clock skew | Task 3 |
| 7 | STORY-001 | Compliance | Rejected request — FIT reaching a log line or ProblemDetail | Critical | Never logged; ProblemDetail has only code + generic text | Task 3, Task 6 |
| 8 | STORY-001 | Authorization | FIT from a Jira site of no Workspace (discovery not built yet) | High | Accepted by tracker — accepted risk until discovery; noted in the PR | — |
| 11 | STORY-003 | Timing | Forge retries the same event after a slow 202 | High | Unique key hit → 202, one row | Task 6 |
| 12 | STORY-003 | Timing | Two deliveries of the same event in parallel | High | One insert wins; the other catches `uq_suggestion_issue` → 202 | Task 6 |
| 13 | STORY-003 | Input extremes | Body without `issue.key` or `issue.fields.project.key`, or non-JSON | Medium | 400 MSG06, nothing stored | Task 6 |
| 14 | STORY-003 | Data integrity | Project exists but `status = REMOVED` | Medium | 202, nothing stored (AC-003.8) | Task 6 |
| 15 | STORY-003 | Error cascades | Database down during receive | High | 5xx → Forge retries (up to 4) | Task 6 |
| 16 | STORY-003 | Data integrity | Other integrity error (e.g. project row deleted between lookup and insert) | Medium | Not swallowed → 5xx, Forge retries | Task 6 |
| 17 | STORY-003 | Input extremes | Issue key longer than 50 characters | Low | 400 MSG06 (column is VARCHAR(50)) | Task 6 |
| 28 | STORY-007 | Authorization | Panel call for an issue key of another project | High | Project not connected → "not connected" | Task 11 |
| 29 | STORY-007 | Input extremes | `issueKey` path `../`, lowercase, SQL characters | Medium | Must match `^[A-Z][A-Z0-9_]+-[0-9]+$` → else 400 | Task 11 |
| 30 | STORY-007 | Error cascades | search hangs | Medium | 503 MSG06 at 20 s | Task 11 |
| 31 | STORY-008 | Authorization | Unlinked or deactivated user calls submit directly | Critical | 403 MSG62, ticket package not called | Task 12 |
| 32 | STORY-008 | Timing | Double click on Finish | Medium | Both passed through; ticket package decides | Task 12 |
| 33 | STORY-010 | Authorization | Non-reporter crafts a suggestion-answer call | Critical | 403 MSG08, nothing recorded | Task 13 |
| 34 | STORY-010 | Timing | Reporter answers twice | Medium | Second → 409 MSG06 | Task 13 |
| 36 | All | Compliance | Jira error body containing ticket text | Critical | Only status code and error type stored or logged | Task 7 |

Open questions for the DB owner: `suggestion_result.answered_at` (AC-010.2).

## Risk Review — 2026-10-21 — Verdict: CAUTION (updated 2026-11-01)

Still applies: the FIT chain and its ordering with `identity`'s chain; the new pom dependency; SSRF through the
FIT `apiBaseUrl` for panel Jira calls (host allowlist, no redirects); panel token used in-request only.
No longer applies: token storage, worker crash/claim, FAILED events, site URL registration.
New: until discovery exists, every Jira site that installs the app writes into the one database (edge case 8) —
acceptable on the demo site only.

## Builder Check — 2026-11-01 (inline)

- Decided in planning: AC-007.4 "not connected" is `projectConnected=false` in `PanelResponse`.
- Decided in planning: `suggestion_result` is mapped in tracker (it writes the row first and reads it for
  STORY-010); the AI Solution Suggestion owner updates the same row.
- No blocking questions.
