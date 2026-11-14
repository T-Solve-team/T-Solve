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
- **Fallback chain.** `common/security/DefaultSecurityConfig` stays until be-foundation `roles-security` replaces the fallback chains; `identity` adds only its `/api/v1/auth/**` chain (identity tasks.md, conflict C5).

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
├── service/       ProjectService (Optional<UUID> findActivePublicIdByJiraProjectKey(String) — UUID since 2026-11-07)
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

## STORY-011: Suggestion job (KAN-22, 2026-11-07)

The 01/11 rule "no background job in tracker" was about storing the Jira system token. The suggestion job uses no
token: it gets the issue text from the event and only calls search. Flow:

```
Forge trigger → JiraAppEventController → IssueCreatedServiceImpl.receive
   save(SuggestionResult)                       (repository transaction commits here)
   → SuggestionJobQueue.enqueue(SuggestionJobRequest)   → 202
          stand-in: InProcessSuggestionJobQueue → suggestionJobExecutor thread
          later:    JobRunr adapter (worker KAN-161) → BackgroundJob.enqueue(...)
   → SuggestionJobService.run(request)
        load SuggestionResult by issue key; missing or already filled → stop (AC-011.7)
        SuggestionSearch.suggest(projectId, summary, description)   (KAN-47; stand-in: StubSuggestionSearch)
        suggestion → SuggestionResult.fill(sources, suggestedSolution) + save   (AC-011.4)
        none → nothing (AC-011.5) · exception → log issue key + error type, nothing (AC-011.6)
```

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Job runner | Port `SuggestionJobQueue`; in-process stand-in on a bounded executor; JobRunr adapter when `worker` KAN-161 lands | `@Async` + `@EnableAsync`; our own job table + `@Scheduled`; install JobRunr in KAN-22 | Infrastructure is Dương's (KAN-161), business logic is ours: no edit to `pom.xml` or JobRunr config, no merge conflict (user, 2026-11-07). A dedicated executor avoids enabling `@Async` for the whole app |
| "After commit" | Enqueue after `save` returns — `receive` is not `@Transactional`, so the repository's own transaction has committed | `@TransactionalEventListener(AFTER_COMMIT)` | Making `receive` transactional would turn the duplicate-key catch (AC-003.4) into a rollback-only commit failure |
| Issue text | `summary` and `description` from the event payload; ADF description → plain text with the existing `AdfTextExtractor` | read the issue from Jira in the job; Forge function that reads it first | No token needed (Non-Functional 3); user, 2026-11-07. Confirm `description` is in the real payload with `forge tunnel` before coding — if absent, the job sends summary only and this row is revised |
| Search port | `SuggestionSearch.suggest(long projectId, String summary, String description) → Optional<IssueSuggestion>` in `tracker/service` | wait for KAN-47's interface | Tracker declares what it needs (spec, Required from other modules); KAN-47 implements it or adapts |
| Stand-in search | `StubSuggestionSearch`, property `tsolve.stub.suggestion` ← `TSOLVE_STUB_SUGGESTION`: empty = no suggestion, `unavailable` = throws, any other text = that text with no sources | always "no suggestion" | Demo of the whole flow and test of the failure path (user, 2026-11-07); same pattern as the other `TSOLVE_STUB_*` |
| `sources_used` format | `[{"type": "TICKET" | "WIKI", "id", "title", "similarity"}]` | own format | What triage's `SourcesUsed` already reads (KAN-149) |
| Double run | Guard in the job (AC-011.7) + conditional update `… WHERE suggested_solution IS NULL` | lock row | A JobRunr retry or a parallel run cannot overwrite a stored suggestion |
| Tenant | Job runs in the single current DB; with tenancy the JobRunr adapter wraps it in `runInTenant` (KAN-102) | carry tenant now | No tenant routing on `main` yet |

Files (Task 15):

```
backend/src/main/java/vn/tsolve/tracker/
├── dto/           IssueCreatedPayload (+ summary, description as JsonNode ADF, labels — all optional),
│                  SuggestionJobRequest(issueKey, projectId, summary, description),
│                  IssueSuggestion(List<SuggestionSource> sources, String suggestedSolution), SuggestionSource
├── entity/        SuggestionResult (+ sources_used JSON string, suggested_solution; fill(...), hasSuggestion())
├── repository/    SuggestionResultRepository (+ findByJiraIssueKey, conditional fill update)
├── service/       SuggestionJobQueue, SuggestionSearch, SuggestionJobService
│   └── impl/      IssueCreatedServiceImpl (enqueue), SuggestionJobServiceImpl, InProcessSuggestionJobQueue
│                  (owns its 2-thread pool, queue 100), StubSuggestionSearch
└── client/        AdfTextExtractor (was an empty placeholder; now ADF → plain text)
   No `config/SuggestionJobConfig` (changed while implementing, 2026-11-07): an executor *bean* would make Boot drop
   its own `applicationTaskExecutor` for every module, so the stand-in queue owns its pool instead.
backend/src/main/resources/application.yml   (tsolve.stub.suggestion)
.env.example                                  (TSOLVE_STUB_SUGGESTION)
frontend/openapi/tsolve-api.yaml              (IssueCreatedPayload: summary, description, labels) + npm run generate:api
```

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 37 | STORY-011 | Timing | Forge resends the event of an issue already recorded | High | No second job (AC-011.2) | Task 15 |
| 38 | STORY-011 | Timing | Job runs twice for one issue (retry, parallel) | High | Second run changes nothing (AC-011.7, conditional update) | Task 15 |
| 39 | STORY-011 | Input extremes | No description, empty ADF, or ADF with only media | Medium | Job runs with an empty description | Task 15 |
| 40 | STORY-011 | Error cascades | Search throws or times out | High | Row unchanged; log has issue key + error type only (AC-011.6) | Task 15 |
| 41 | STORY-011 | Error cascades | Executor queue full (burst of new issues) | Medium | Job dropped and logged; row stays without a suggestion — accepted for the stand-in, JobRunr removes it | Task 15 |
| 42 | STORY-011 | Compliance | Issue text in logs | Critical | Never logged; job argument only | Task 15 |
| 43 | STORY-011 | Data integrity | Row deleted before the job runs | Low | Job stops (AC-011.7) | Task 15 |
| 44 | STORY-011 | User types | Issue created by the Chatbot (label `tsolve-chatbot`) | Medium | Same as any issue (AC-011.8) | Task 15 |

### Change 2026-11-07: run the job on the worker (KAN-102)

```
receive: saveAndFlush(SuggestionResult with jobRef = random UUID, issueSummary, issueDescription)
   → SuggestionJobQueue.enqueue(SuggestionJobRequest(jobRef, issueKey))          → 202
        worker enabled  (tsolve.worker.enabled=true): WorkerSuggestionJobQueue
            workspace bound? → TenantJobService.enqueue("tracker.suggestion", jobRef)
            else dev workspace configured? → WorkerTenantService.runIn(devId, enqueue)
            else / enqueue error → log issue key + error type, job lost (never throws)
        worker disabled (default): InProcessSuggestionJobQueue (AC-011.9, no retry)
SuggestionJobHandler (TenantJobHandler "tracker.suggestion").execute(jobRef) → SuggestionJobService.run(jobRef)
   load by jobRef; gone or already filled → stop (AC-011.7)
   search(projectId, row.issueSummary, row.issueDescription) → fillSuggestion (AC-011.3..5)
   failure → log key + type, throw SuggestionJobFailedException (no cause) → JobRunr retries (AC-011.6)
```

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Issue text | Columns `issue_summary`, `issue_description` on `suggestion_result` | text in the job | Worker rule: a job carries only a record UUID, no ticket content; the Jobs DB is shared by all Workspaces (README-worker) |
| Record id for the worker | ~~New column `job_ref UUID NOT NULL UNIQUE`~~ — superseded by "Change 2026-11-07: UUID keys" below: the job carries `suggestion_id` | change the BIGSERIAL key to UUID | Triage reads `suggestion_id`; a new column changes nothing for it |
| Retries | The job throws on failure; JobRunr's native retries | swallow (old AC-011.6) | User, 2026-11-07; the guard and conditional update make reruns safe |
| Failure exception | `SuggestionJobFailedException extends CodedException`, message = issue key + error type, **no cause** | rethrow the original | JobRunr stores the exception and its causes in the Jobs DB; a cause message can quote issue text |
| Workspace at enqueue | Use the bound one; else `tsolve.tracker.dev-workspace-id` (`TSOLVE_TRACKER_DEV_WORKSPACE_ID`) through `WorkerTenantService.runIn` | in-process fallback when unbound | User chose (a), 2026-11-07. Dev/mock only; removed when platform binds the request's Workspace |
| Which queue | `@ConditionalOnProperty(tsolve.worker.enabled)`: true → `WorkerSuggestionJobQueue`, false/missing → `InProcessSuggestionJobQueue` | `@ConditionalOnBean` | Bean-presence conditions on components depend on registration order |
| Handler bean | Always registered | only with the worker | Harmless when the worker is off; the worker collects every `TenantJobHandler` |

Changeset `017-tracker-suggestion-job-input.sql` (author `tracker`): add `job_ref` (filled with `gen_random_uuid()`
for existing rows, then NOT NULL + UNIQUE), `issue_summary TEXT`, `issue_description TEXT`. Tell the DB owner and
Trần Hải Anh (triage reads the table; its read-only entity does not map the new columns).

Files (Task 16): modify `entity/SuggestionResult`, `repository/SuggestionResultRepository` (+ `findByJobRef`),
`dto/SuggestionJobRequest` (jobRef, issueKey), `service/SuggestionJobService` (`run(UUID jobRef)`),
`service/impl/SuggestionJobServiceImpl`, `service/impl/IssueCreatedServiceImpl`,
`service/impl/InProcessSuggestionJobQueue` (conditional, catches the job's failure), `application.yml`, `.env.example`,
changelog master; create `service/impl/WorkerSuggestionJobQueue`, `service/impl/SuggestionJobHandler`,
`exception/SuggestionJobFailedException`, `changes/017-tracker-suggestion-job-input.sql`.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 45 | STORY-011 | Error cascades | Search keeps failing | Medium | JobRunr retries, then marks the job failed; row unchanged | Task 16 |
| 46 | STORY-011 | Compliance | Search exception message quotes the issue text | Critical | Stored failure has key + type only (no cause) | Task 16 |
| 47 | STORY-011 | Environment | Worker on, no Workspace bound, no dev Workspace set | Medium | Logged, 202, no job (never 5xx) | Task 16 |
| 48 | STORY-011 | Data integrity | Rows created before the changeset | Low | Get a `job_ref`; no text, so a job for them would send empty text | Task 16 |

Search timeout: the job sets none of its own; AC-011.6 "does not answer" is met by the `SuggestionSearch`
implementation timing out (KAN-47, on top of the `ai` client's chat timeout). A search that never returns would hold
one of the two stand-in threads (review 2026-11-07, accepted).

Risks: the in-process stand-in loses queued jobs on restart (accepted while the worker is off). Since the change of
2026-11-07 the issue text stays in `suggestion_result`; a JobRunr job carries only `job_ref`. If the Jobs database
is unreachable, enqueue can block the event request for the worker pool's connection timeout (review of Task 16,
finding 1 — for the `worker` owner).

### Change 2026-11-07: UUID keys for the tracker's table

Team rule (`backend/AGENTS.md`, Naming): a key is `<table>_id UUID DEFAULT gen_random_uuid()`, a reference is a UUID,
Java type `UUID`. `suggestion_result` still had `BIGSERIAL` / `BIGINT`. Scope chosen by the user (2026-11-07): only
the tracker's own table; `department` and `project` keep their keys (their owners change them with the team).

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| `suggestion_id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()`; entity `@GeneratedValue(strategy = UUID)` | keep BIGSERIAL | Team rule (user, 2026-11-07) |
| `project_id` | `UUID NOT NULL REFERENCES project (public_id)`; `ProjectService` returns the Project's `public_id` | keep `BIGINT` → `project.project_id` | The API already shows a Project by `public_id` (`008-search-public-ids`); user, 2026-11-07 |
| Job reference | The job carries `suggestion_id`; drop `job_ref` and `findByJobRef` | keep `job_ref` | With a UUID key the extra column says the same thing (user, 2026-11-07) |
| Migration | New changeset `019-tracker-suggestion-uuid-keys.sql`; `002` and `017` unchanged | rewrite `017` | `017` already ran on developer databases from the pushed branch; editing it breaks their checksum (user, 2026-11-07) |
| Search port | `SuggestionSearch.suggest(UUID projectId, …)` | keep `long` | The port is tracker's; KAN-47 implements it with the Project UUID |
| Triage | Its read-only `SuggestionResultRecord` maps both columns as `UUID`; the history keys managed Projects by `publicId` | leave triage on `Long` | Same table — the entity fails `ddl-auto: validate` otherwise. Tell Trần Hải Anh in the PR |

Changeset `019` (author `tracker`), in one transaction, keeping existing rows:
add `new_id UUID DEFAULT gen_random_uuid()` and `new_project_id UUID`; fill `new_project_id` from
`project.public_id`; drop the old key, `uq_suggestion_job_ref`, `job_ref`, `idx_suggestion_project`, `project_id`,
`suggestion_id`; rename the new columns; `NOT NULL`, primary key, foreign key to `project (public_id)`, and recreate
`idx_suggestion_project (project_id, created_at)`.

Files: `changes/019-tracker-suggestion-uuid-keys.sql`, changelog master; `project/entity/Project` (+ `publicId`),
`project/service/ProjectService` + impl (`Optional<UUID> findActivePublicIdByJiraProjectKey`),
`project/repository/ProjectRepository`; tracker `entity/SuggestionResult`, `repository/SuggestionResultRepository`
(`JpaRepository<SuggestionResult, UUID>`, no `findByJobRef`), `dto/SuggestionJobRequest` (suggestionId, issueKey),
`service/SuggestionJobService` (`run(UUID suggestionId)`), `service/SuggestionSearch`, `service/impl/*` that use
them; triage `entity/SuggestionResultRecord`, `repository/HistoryFilter`, `repository/SuggestionResultRecordRepository`,
`service/impl/SuggestionResultServiceImpl`; their tests.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 49 | STORY-003 | Data integrity | Rows recorded before `019` | Medium | Keep their data; get a new UUID key and their Project's `public_id` | Task 17 |
| 50 | STORY-011 | Data integrity | A job queued before `019` (carries the old `job_ref`) | Low | Finds no row and stops (AC-011.7) — developer databases only | Task 17 |

## STORY-012: Post the suggestion as the MSG80 comment (KAN-131, 2026-11-07)

```
SuggestionJobHandler / InProcessSuggestionJobQueue → SuggestionJobService.run(suggestionId)
   load row; gone → stop
   no suggestion yet → search → fillSuggestion (STORY-011); none → stop (AC-012.6)
   row has suggestion, jira_comment_id IS NULL → SuggestionCommentService.post(row)          (AC-012.1, 012.3)
        JiraServiceAccount.current()   empty → log "no service account", stop, no retry   (AC-012.8)
        TicketTrackerClient.findComment(issueKey, MSG80 prefix, account) → found: store its id   (AC-012.5)
        else TicketTrackerClient.addComment(issueKey, Msg80.text(suggestion)) → comment id
        repository.markCommented(id, commentId, now)   … WHERE jira_comment_id IS NULL      (AC-012.3)
   row has jira_comment_id → stop (AC-012.4)
   Jira 5xx / 429 / timeout / unreachable → SuggestionJobFailedException → JobRunr retries   (AC-012.7)
   Jira 401 / 403 / 404 → log key + status, stop without retry                               (AC-012.8)
```

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Who posts | The suggestion job, in the backend, with the Workspace's Jira service account (Basic auth: email + API token) | Forge function polling a queue; backend calling a Forge web trigger; the Forge app system token | User, 2026-11-07: a service account is created for every tenant at onboarding (`workspace.service_account_email` / `service_account_token`, DB design 31/10); simplest flow, no token from Forge is stored |
| Service account source | Port `tracker.service.JiraServiceAccount` → `Optional<JiraAccount(siteUrl, email, apiToken)>`; stand-in `ConfiguredJiraServiceAccount` reads `tsolve.tracker.jira.*` ← `JIRA_SITE_URL`, `JIRA_SERVICE_ACCOUNT_EMAIL`, `JIRA_SERVICE_ACCOUNT_TOKEN` (no defaults; any blank → empty) | read the `workspace` table now | `workspace` (UC-08/09, encryption) is not built; the port is what `workspace` implements later — same pattern as `SuggestionSearch` |
| Jira HTTP | `JiraClient implements TicketTrackerClient` with Spring `RestClient`, connect 5 s / read 10 s: `POST /rest/api/3/issue/{key}/comment` (ADF body), `GET /rest/api/3/issue/{key}/comment`, `GET /rest/api/3/myself` (the service account's accountId) | WebClient; a Jira SDK | Already chosen for the panel calls (Key Decisions); no new dependency |
| Comment text | `Msg80`: "T-Solve suggestion: {suggested_solution} Did this solve your problem? Answer in the T-Solve panel of this issue." — one ADF paragraph per line of the suggestion; no "Related documents" sentence | MSG80 with `{wiki_links}` | User, 2026-11-07 (spec AC-012.1, 012.2); text from SRS MSG80 |
| Find a lost comment | Before adding, list the issue's comments and take one whose author is the service account (`/myself` accountId) and whose text starts with "T-Solve suggestion:" | a Jira comment property; no check | Covers "added but id not stored" (AC-012.5) with two reads, only for a row without `jira_comment_id` |
| Mark as posted | `@Modifying update … set jiraCommentId, commentedAt where id = :id and jiraCommentId is null` | load and save | A parallel rerun cannot overwrite; same pattern as `fillSuggestion` |
| Error classes | `JiraUnavailableException` (5xx, 429, I/O, timeout) → the job throws `SuggestionJobFailedException` (retry); `JiraRejectedException` (401, 403, 404) and a missing account → logged, the job ends | retry everything | 401/403/404 do not heal with a retry (AC-012.8) |
| Logs | Issue key, HTTP status, error type; never the suggestion, the email or the token | — | Non-Functional 4 and 9 |

Changeset `020-tracker-suggestion-comment.sql` (author `tracker`): `ALTER TABLE suggestion_result ADD COLUMN
jira_comment_id VARCHAR(50), ADD COLUMN commented_at TIMESTAMPTZ;` — tell the DB owner (the DB design has no such
columns) and Trần Hải Anh (triage's read-only entity does not map them).

Files (Task 18): create `service/JiraServiceAccount`, `dto/JiraAccount`, `service/impl/ConfiguredJiraServiceAccount`,
`service/SuggestionCommentService`, `service/impl/SuggestionCommentServiceImpl`, `service/impl/Msg80`,
`exception/JiraUnavailableException`, `exception/JiraRejectedException`, `changes/020-tracker-suggestion-comment.sql`;
fill `client/TicketTrackerClient` (`findComment`, `addComment`) and `client/JiraClient`; modify
`entity/SuggestionResult` (+ `jiraCommentId`, `commentedAt`, `hasComment()`), `repository/SuggestionResultRepository`
(+ `markCommented`), `service/impl/SuggestionJobServiceImpl` (post after the fill, or when filled without a comment),
`application.yml`, `.env.example`, changelog master. No API change (no endpoint).

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 51 | STORY-012 | Timing | JobRunr reruns the job after the comment was posted and its id stored | High | No second comment (AC-012.4) | Task 18 |
| 52 | STORY-012 | Error cascades | Comment posted, then storing its id fails | High | The rerun finds the comment, stores its id, posts nothing (AC-012.5) | Task 18 |
| 53 | STORY-012 | Error cascades | Jira 503 / 429 / timeout | High | The job fails and JobRunr retries; the suggestion is kept (AC-012.7) | Task 18 |
| 54 | STORY-012 | Authorization | Token revoked (401), no permission in the project (403) or issue deleted (404) | Medium | No comment, no retry, logged (AC-012.8) | Task 18 |
| 55 | STORY-012 | Environment | No service account configured (local dev) | Medium | Suggestion stored, no comment, logged (AC-012.8) | Task 18 |
| 56 | STORY-012 | Compliance | A Jira error body or our log line | Critical | Never the suggestion text, the email or the token (Non-Functional 4, 9) | Task 18 |
| 57 | STORY-012 | Input extremes | A suggested solution with several lines or very long | Low | One ADF paragraph per line; cut with "…" if the comment would pass Jira's 32,767-character limit | Task 18 |

Risk: the service account must be allowed to comment in every T-Solve Project; a missing permission shows only as a
403 in the log (edge case 54) until `workspace` checks it at onboarding.

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


## STORY-013: Read a Jira project for the Project module (KAN-127, 2026-11-12)

Spec: `artifacts/03-modules/tracker/spec.md`, STORY-013. Task: `artifacts/04-plans/tracker/iteration-1/tasks.md`, Task 19 (`jira-project-read`). KAN-127 decisions: `artifacts/04-plans/project-integration/iteration-1/kan-127-decisions.md`, D4-D6, D28, D35, D38. This Change Mode extension leaves earlier stories and the comment client's existing methods intact.

```text
ProjectService.testConnection / create (Workspace already bound; no transaction)
  -> TrackerProjectService.findProject(key)
      JiraServiceAccount.current() empty -> DENIED; no HTTP
      TicketTrackerClient.findProject(account, key)
        JiraClient GET /rest/api/3/project/{key}
        project 404 -> Optional.empty -> NOT_FOUND; no statuses call
        canonical nonblank key present -> projectStatuses(account, canonicalKey)
          JiraClient GET /rest/api/3/project/{key}/statuses
          flatten issue types' statuses; validate exact text id/name and category
      service deduplicates ids in first-seen order; first occurrence wins
      FOUND(key, statuses), including valid empty statuses
      either read 404 -> NOT_FOUND
      either read 401/403/other 4xx (except 404,429) -> DENIED
      either read 429/5xx/I/O/timeout/unreadable or malformed success -> UNAVAILABLE
  -> TrackerProjectLookup; no client exception, credential, raw JSON, or partial result
Project maps outcomes to its HTTP contract; this service adds no REST endpoint.
```

`TrackerProjectLookup(Outcome outcome, String key, List<TrackerStatus> statuses)` has a nested Outcome enum FOUND/NOT_FOUND/DENIED/UNAVAILABLE; non-FOUND uses null key and an empty non-null immutable statuses list. `TrackerStatus(String id, String name, boolean done)` contains only those fields. `done` is true exactly for statusCategory.key `done`; missing/other category is false. Required key/id/name must be text and nonblank. Validate the statuses root as an array and each issue-type status collection before flattening; do not treat an object/null/unreadable body as the valid empty-array case. Neither service nor client persists, uses ProjectService, or opens a database transaction. Bound Workspace resolution remains the caller's responsibility.

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| D4 module boundary | Project calls tracker.service.TrackerProjectService and tracker.dto records | Project own HTTP client; expose tracker/client | Keep HTTP/credential/status handling in tracker; no Project dependency in this lookup |
| D5 credential | Existing JiraServiceAccount.current and JiraClient Basic auth | Import gateway Bearer; credential unification now | Current port is replaceable by KAN-119/KAN-125; real scoped tokens remain a documented limit |
| D6 Browse check | Project GET then statuses GET with canonical key | mypermissions; both permission and project calls | Project GET answers visibility and key; statuses supply the form defaults |
| D28 result | Four-outcome TrackerProjectLookup; deduplicate at service | Throw module exceptions; expose raw JSON | Project owns HTTP error mapping; first-seen dedup matches UI storage |
| D35 malformed response | UNAVAILABLE; valid empty statuses is FOUND | Partial success; silently skip invalid status | No fabricated settings or lost choices; 429 handled before other 4xx |
| D38 client signature | Optional<String> findProject(JiraAccount, String), project 404 empty | TrackerProject record; throw on project 404 | Explicit RUN 2 contract; synchronized interface row in tracker spec without AC edits. Statuses 404 still uses status-only client exception |
| Tests | Existing JiraClientTest with MockRestServiceServer plus new Mockito service test | Live Jira; new HTTP server/container | Check real RestClient serialization/error mapping quickly and deterministically |
| Logging | Project key, HTTP status if known, outcome only | Exception stack/cause or response body | Never expose service-account email/token or Jira JSON, including malformed-success paths |

Files, exact paths (new files are Create, never mislabeled Modify):

- Modify: `backend/src/main/java/vn/tsolve/tracker/client/TicketTrackerClient.java`
- Modify: `backend/src/main/java/vn/tsolve/tracker/client/JiraClient.java`
- Create: `backend/src/main/java/vn/tsolve/tracker/service/TrackerProjectService.java`
- Create: `backend/src/main/java/vn/tsolve/tracker/service/impl/TrackerProjectServiceImpl.java`
- Create: `backend/src/main/java/vn/tsolve/tracker/dto/TrackerProjectLookup.java`
- Create: `backend/src/main/java/vn/tsolve/tracker/dto/TrackerStatus.java`
- Test (Modify): `backend/src/test/java/vn/tsolve/tracker/client/JiraClientTest.java`
- Test (Create): `backend/src/test/java/vn/tsolve/tracker/service/impl/TrackerProjectServiceImplTest.java`

No new library, schema, config, Forge manifest, client factory or credential DTO. Reuse JiraAccount, JiraRejectedException.getStatus, JiraUnavailableException.getStatus and the existing RestClient error mapper. Catch project-lookup 404 only within the new findProject operation so comment semantics do not change. Both project/statuses success parse failures become status-only unavailable exceptions before the service maps the outcome. Do not log caught causes or account toString.

Decided in planning: statuses use the returned canonical key; invalid project response prevents the second HTTP call. A failed second call discards the project result. Existing Basic connect/read timeouts are 5s/10s; tests inject timeout exceptions rather than wait for time. No new public error handler or backend endpoint.

Edge Cases & Risks for this extension (continue after previous row 57): applicable dimensions are Integration, Input extremes, Scale, Environment, Error cascades, Compliance, Authorization and Timing. User types (internal caller only), State transitions and Data integrity (stateless read, no writes), and Business logic (no Project eligibility decision here) add no separate scenarios; caller-side cases are in project-integration rows 1-38. Existing tracker-wide dimension analysis still applies.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 58 | STORY-013 | Integration | Both reads succeed using canonical key, configured Basic headers and existing timeout setup | High | FOUND with canonical key/statuses, same account and 5s/10s timeouts (AC-013.1, AC-013.10) | Task 19 |
| 59 | STORY-013 | Input extremes / Scale | Repeated status id under several issue types; missing/other category | High | First name/category wins in encounter order, exact id/name, done only for done category (AC-013.2, AC-013.3) | Task 19 |
| 60 | STORY-013 | Authorization / Environment | No configured account; either read 404/401/403/other 4xx except 429 | High | No-account DENIED without HTTP; 404 NOT_FOUND; other 4xx DENIED; null key/empty statuses (AC-013.4-AC-013.6) | Task 19 |
| 61 | STORY-013 | Error cascades | Project/status read 429, 500, timeout, refused connection or I/O failure | High | UNAVAILABLE, no exception/partial project; 429 precedence (AC-013.7) | Task 19 |
| 62 | STORY-013 | Input extremes / Integration | Nontext/blank/missing key, id or name; invalid JSON or non-array statuses root | High | UNAVAILABLE without partial/defaulted result (AC-013.8) | Task 19 |
| 63 | STORY-013 | Scale | Valid empty statuses array; one status | Medium | FOUND with empty or singleton list, never a fabricated Done status (AC-013.1, AC-013.9) | Task 19 |
| 64 | STORY-013 | Timing / Error cascades | First read fails, but second could answer successfully | High | No second request after any failed/malformed first read (AC-013.11) | Task 19 |
| 65 | STORY-013 | Compliance | Successful/error Jira body or exception contains secret markers | Critical | Logs only key/status/outcome; DTOs and exceptions contain no service-account credential/body; zero persistence (AC-013.12, AC-013.13) | Task 19 |

Risk Review: **CAUTION** for the retained Basic-auth/scoped-token mismatch; this story is testable with a compatible test credential and mocks, and does not claim real scoped-token support. Project owns MSG48/503 mapping and transaction boundaries; tracker never calls back into Project on this read path. This extension is a dependency on the same KAN-127 issue branch, one feature/commit approved by the user before starting the next task.
