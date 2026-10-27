---
module: tracker
date: 2026-10-21
---

# Design: tracker

Spec: `artifacts/03-modules/tracker/spec.md` (approved, version ba8598ba) · Module: `artifacts/03-modules/tracker/module.md`
Conventions: `backend/AGENTS.md` (package-by-module then layer, service interfaces, DTO records, ProblemDetail + MSG codes).

## Architecture Overview

```
Forge trigger ──POST + FIT + x-forge-oauth-system──►  [JiraAppSecurityConfig: FIT filter chain, /api/v1/jira-app/**]
Issue Panel  ──invokeRemote + FIT──────────────────►          │ JiraAppPrincipal(cloudId, workspaceId, accountId)
                                                              ▼
                                          JiraAppEventController ─► IssueEventService.receive(...)  → issue_event (RECEIVED) → 202
                                          JiraAppIssueController ─► JiraPanelService (panel, refresh, draft, submit, answer)
                                                                         │ calls project / identity / search / ticket / triage ports
IssueEventWorker (@Scheduled 5 s) ─► IssueEventService.claimDue() ─► IssueEventProcessor.process(event)
                                                                         ├─ TicketTrackerClientFactory → JiraClient (RestClient, system token)
                                                                         ├─ CREATED: triage → search → MSG80 comment (once)
                                                                         └─ UPDATED + Done: comments → ticket.createOrUpdateAiDraft
TrackerCleanupJob (@Scheduled daily 03:00) ─► delete finished events > 30 days
workspace package ─► JiraSiteService.registerSite(workspaceId, siteUrl) → GET {site}/_edge/tenant_info → jira_site
```

- **Two security filter chains.** `JiraAppSecurityConfig` declares a `SecurityFilterChain` with
  `@Order(1)` and `securityMatcher("/api/v1/jira-app/**")`: stateless, CSRF off, `oauth2ResourceServer().jwt()`
  with a custom `JwtDecoder` and a converter that turns the FIT into a `JiraAppPrincipal` with authority
  `ROLE_JIRA_APP`. The web app's chain (owned by `identity`) handles everything else. Controllers use
  `@PreAuthorize("hasRole('JIRA_APP')")`.
- **Receive / process split.** The event endpoints only validate, encrypt the token and insert (one short
  transaction). All Jira and dependency calls happen in the worker, never inside a `@Transactional` method
  (AGENTS §4): claim (tx) → process (no tx) → finish (tx).
- **Ports for other packages.** tracker defines the interfaces it needs in `vn.tsolve.tracker.service.port`
  (`TrackerProjectPort`, `TrackerIdentityPort`, `TrackerTriagePort`, `TrackerSearchPort`, `TrackerTicketPort`)
  with stub implementations in `service/port/stub/` active under the `stub-ports` profile until the owners
  provide real ones. Owners implement the port (or tracker adapts to their service interface when it exists).
- **Panel calls carry a system token.** The panel/Solution Form remote endpoints enable `appSystemToken`; the token is used within that request only and never stored (risk review).
- **Site check in the converter (2026-10-27).** After Nimbus validates signature, `exp`/`nbf`, the token
  validators check `iss`, `aud` and that `context.cloudId` is present (a failure is 401). The converter then
  resolves the cloudId through `JiraSiteLookup` (run as `ROOT`); an unregistered site gets no `ROLE_JIRA_APP`,
  so the chain answers 403 (AC-001.6) — a validator failure could only give 401. A JWKS fetch failure
  (`AuthenticationServiceException`) answers 503 MSG06 through the filter's failure handler (AC-001.8).
- **Fallback chain.** Adding the resource server removes Spring Boot's default chain, so
  `common/security/DefaultSecurityConfig` keeps "any other request needs authentication" until `identity`
  replaces it (flag for the identity owner).
- **Workspace per transaction (multi-tenancy, 2026-10-27).** `jira_site` and `issue_event` are tenant tables:
  each entity's `workspaceId` is `@TenantId`, filled by Hibernate from `WorkspaceContext` (architecture,
  Multi-tenancy). Where the Workspace is set:
  - **Receive:** the cloudId lookup runs as `WorkspaceContext.ROOT`; once the site is found, the controller
    sets that Workspace and calls `IssueEventService.receive` (new transaction), so the event is inserted
    under it (AC-003.7). An unknown cloudId never gets this far: the FIT validator rejects it (403 MSG08).
  - **Worker:** the claim and stale-claim queries are native SQL across all Workspaces, run as `ROOT`. For each
    claimed event the worker sets the event's `workspaceId` before processing and finishing it, so every
    JPA read and write of that event stays in its Workspace (AC-004.9). Calls to other modules run in that
    Workspace too.
  - **Cleanup job:** native delete across all Workspaces, as `ROOT`.
  - Entities take no `workspaceId` in their constructors: Hibernate sets it on insert.

## Data Model

New Liquibase changeset `backend/src/main/resources/db/changelog/changes/002-jira-issue-events.sql`
(id `002-jira-issue-events`, author `tracker`; the number is the next free one when written). The table is
`issue_event` (renamed from `tracker_event`, user 2026-10-22); its classes are named `IssueEvent*` (entity `IssueEvent`
with `@Table(name = "issue_event")`, `IssueEventType`, `IssueEventStatus`, `IssueEventRepository`, …).

```sql
CREATE TABLE jira_site (
    id            BIGSERIAL PRIMARY KEY,
    workspace_id  BIGINT       NOT NULL UNIQUE REFERENCES workspace (id),   -- one site per Workspace
    site_url      VARCHAR(255) NOT NULL,
    cloud_id      VARCHAR(64)  NOT NULL UNIQUE,                             -- AC-002.5
    registered_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE issue_event (
    id               BIGSERIAL PRIMARY KEY,
    workspace_id     BIGINT       NOT NULL REFERENCES workspace (id),   -- tenant, @TenantId (AC-003.7)
    cloud_id         VARCHAR(64)  NOT NULL,
    issue_id         VARCHAR(32)  NOT NULL,
    issue_key        VARCHAR(64)  NOT NULL,
    event_type       VARCHAR(20)  NOT NULL,          -- ISSUE_CREATED, ISSUE_UPDATED
    event_timestamp  BIGINT       NOT NULL,          -- Forge payload `timestamp` (epoch ms)
    status           VARCHAR(20)  NOT NULL,          -- RECEIVED, PROCESSING, PROCESSED, IGNORED, FAILED
    attempts         SMALLINT     NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ  NOT NULL,
    claimed_at       TIMESTAMPTZ,
    token_ciphertext BYTEA,                          -- AES-GCM(iv || ciphertext || tag); NULL once finished
    comment_posted   BOOLEAN      NOT NULL DEFAULT FALSE,   -- AC-005.4
    error_type       VARCHAR(50),
    received_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ,
    CONSTRAINT uq_issue_event UNIQUE (cloud_id, issue_id, event_type, event_timestamp)   -- AC-003.4
);
CREATE INDEX idx_issue_event_due ON issue_event (status, next_attempt_at);
CREATE INDEX idx_issue_event_received ON issue_event (received_at);
CREATE INDEX idx_issue_event_workspace ON issue_event (workspace_id);
```

The changeset was rewritten on 2026-10-27 to add `issue_event.workspace_id`; it had only run on one developer
database (reset), never on a shared one.

Claim query (native, `IssueEventRepository`):
`SELECT … FROM issue_event WHERE status = 'RECEIVED' AND next_attempt_at <= now() ORDER BY next_attempt_at
LIMIT :batch FOR UPDATE SKIP LOCKED`, then set `status = PROCESSING, claimed_at = now()` in the same transaction.
A PROCESSING row whose `claimed_at` is older than 10 minutes (worker crashed) returns to RECEIVED.

Triage decision and MSG80 posted/answered state for the panel and STORY-010 are kept by `triage` (tracker
reports the posted comment and asks through the port), not
duplicated in tracker.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Event delivery | Forge trigger → remote endpoint directly | Forge function relays with Async Events | Forge retries for free (4×); logic stays in testable Java; matches diagram (user, plan Q1) |
| Background processing | DB table `issue_event` + worker | In-memory `@Async` | Survives restarts, dedupe, inspectable failures (plan Q2) |
| System token while waiting | Encrypted in the event row, deleted when done or after 2 h | In memory only | Survives restarts within the token's 2-hour validity (plan Q3) |
| Allowed sites | `jira_site` table filled from Workspace Setup (MSG61) | env var; auto-register + approve | SRS: Admin enters the site address (plan Q4/Q5 → SRS) |
| Worker retry | 1, 5, 15 min then FAILED; 401/403/404 FAILED at once | none; until token expiry | Covers short outages within token validity (plan Q6) |
| Retention | 30 days for finished events | forever; 7 days | Enough to investigate, small table (plan Q8) |
| FIT verification | Spring OAuth2 Resource Server (Nimbus `NimbusJwtDecoder` with JWKS URI, custom validators) | jjwt + hand-written filter | Least security code written by hand; built-in JWKS caching (design Q1) |
| Worker | `@Scheduled(fixedDelay = 5 s)` + `FOR UPDATE SKIP LOCKED` | JobRunr/Quartz; `@Async` | No new library; safe with several instances (design Q2) |
| Token encryption | JCA AES-256-GCM, random 12-byte IV, key from `TSOLVE_TOKEN_KEY` (base64, 32 bytes) | Spring Security Crypto `Encryptors` | No extra dependency, fast, simple to test (design Q3) |
| ADF → text | tracker converts description and comments to plain text (`AdfTextExtractor`) | pass raw ADF | Other packages stay tracker-agnostic (design Q4) |
| Jira HTTP client | Spring `RestClient`, connect 5 s / read 10 s timeouts | WebClient | In spring-web already; sync fits the worker (design Q5) |
| Dedupe key | `(cloud_id, issue_id, event_type, event_timestamp)` unique | event id (none in payload) | Forge retries resend the same body; verify with a real payload — Decided in planning |
| Status filter at receive | `changelog.items[].fieldId == "status"` or `associatedStatuses` present | read Jira on receive | No outbound call inside the 5-second window — Decided in planning |
| Comment API | `POST {apiBaseUrl}/rest/api/3/issue/{id}/comment` with ADF body; MSG80 as public comment | JSM servicedesk API | Reporter must see MSG80 (AC-005.2) — Decided in planning |
| Site → cloudId | `GET {siteUrl}/_edge/tenant_info` → `cloudId` | Admin types cloudId | Admin enters only the URL (MSG61); verify endpoint in task 1 |
| Multi-tenancy | Workspace = tenant; Hibernate `@TenantId` on `workspaceId` of both entities, no base class | PostgreSQL RLS; `@TenantId` + RLS; a base `@MappedSuperclass` | Team request (2026-10-27); no DB user or compose change; AGENTS keeps entities out of `common/` (user) |
| Jira base URL | `apiBaseUrl` from the FIT stored with the event | hard-coded `api.atlassian.com/ex/jira/{cloudId}` | Forge docs: route requests to the FIT's `apiBaseUrl` — Decided in planning |

## File Structure
Follows `backend/AGENTS.md` §1 as updated on 2026-10-21: a module has only its layers; configuration and
security config live in `common/`, which depends on no module.
```
backend/src/main/java/vn/tsolve/common/
├── config/        TrackerProperties (@ConfigurationProperties: app id, JWKS URI, token key, worker timings),
│                  WorkspaceTenantResolver (CurrentTenantIdentifierResolver<Long> + HibernatePropertiesCustomizer)
└── security/      WorkspaceContext (per-thread Workspace: set/clear, callAs, NONE = -1, ROOT = 0),
                   JiraAppSecurityConfig (@Order(1) chain for /api/v1/jira-app/**), FitJwtDecoderConfig,
                   FitValidators, FitAuthenticationConverter, JiraAppPrincipal,
                   JiraSiteLookup (interface: cloudId → workspaceId, implemented by tracker)
backend/src/main/java/vn/tsolve/tracker/
├── controller/    JiraAppEventController, JiraAppIssueController
├── service/       IssueEventService, JiraPanelService, JiraSiteService, TicketTrackerClientFactory
│   └── impl/      IssueEventServiceImpl, IssueEventProcessor, IssueEventWorker, TrackerCleanupJob,
│                  JiraPanelServiceImpl, JiraSiteServiceImpl (also implements JiraSiteLookup), TokenCipher
├── client/        TicketTrackerClient (interface), JiraClient, AdfTextExtractor, JiraTenantInfoClient
├── exception/     TokenUnreadableException, TrackerClientException (retryable flag), InvalidSiteException
├── repository/    IssueEventRepository, JiraSiteRepository
├── entity/        IssueEvent, IssueEventStatus, IssueEventType, JiraSite
└── dto/           IssueEventPayload, PanelResponse, SolutionDraftRequest, SubmitSolutionRequest,
                   SuggestionAnswerRequest, SuggestionAnswerResponse, TrackerIssue, TrackerComment
backend/src/main/resources/db/changelog/changes/002-jira-issue-events.sql
backend/src/test/java/vn/tsolve/{common/security,tracker}/…  (tests mirror main; method names cite AC ids)
frontend/openapi/tsolve-api.yaml            (Jira App API paths + schemas)
.env.example                                 (TSOLVE_JIRA_APP_ID, TSOLVE_TOKEN_KEY, FORGE_JWKS_URI)
```

- The `common/` changes are flagged in the PR (AGENTS, Notes for AI agents); the identity owner reviews the
  chain order.
- Calls into `project`, `identity`, `triage`, `search`, `ticket` go through **their** `service/` interfaces
  (AGENTS §1). This replaces the tracker-owned ports of the Architecture Overview: the interfaces are agreed
  with each owner before Tasks 8–9, and until an owner delivers, a stub bean in that owner's package is added
  by the owner or with the owner's agreement. Decided 2026-10-22 (new AGENTS layout).

## External Dependencies
- `spring-boot-starter-oauth2-resource-server`: FIT verification (JWKS, JWT). **New dependency** — backend
  AGENTS says to stop and flag it: the team approves the `pom.xml` change in the PR.
- Atlassian Forge JWKS `https://forge.cdn.prod.atlassian-dev.net/.well-known/jwks.json` (configurable).
- Jira Cloud REST API v3 through the FIT `apiBaseUrl`, authorised by the app system token.
- `{site}/_edge/tenant_info` for site → cloudId.
- `common` `AuditService.record(…)` — not built yet; site registration is a "Jira configuration change" and
  is audited by the workspace package that calls `registerSite` (AGENTS §5).
- Ports to `project`, `identity`, `triage`, `search`, `ticket` (other owners; stubs until ready).

## Edge Cases & Risks
<!-- Filled by the edge-cases skill (and risk-review for high-risk modules).
     Every Critical/High row maps to a test task in tasks.md. -->

Dimensions analyzed: 1 User types, 2 Input extremes, 3 Timing, 4 Scale, 5 State transitions, 7 Error cascades,
8 Authorization, 9 Data integrity, 10 Integration, 11 Compliance · Skipped: 6 Environment — server-to-server API,
no client rendering; times are `Instant`/UTC · 12 Business logic — no amounts or quotas in this module.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | Authorization | FIT signed by a valid Atlassian key but for another Forge app (`aud` differs) | Critical | 401 MSG08 | Task 3 |
| 2 | STORY-001 | Authorization | FIT with `alg: none` or HS256 signed with the public key | Critical | 401 — decoder accepts RS256 only | Task 3 |
| 3 | STORY-001 | Authorization | Valid FIT from a Jira site that is not any Workspace's site | Critical | 403 MSG08 | Task 3 |
| 4 | STORY-001 | Integration | Atlassian rotates keys; FIT has a new `kid` | High | One JWKS refresh, then accepted | Task 3 |
| 5 | STORY-001 | Error cascades | JWKS endpoint down, no cached key matches | High | 503 MSG06, error type recorded | Task 3 |
| 6 | STORY-001 | Timing | Server clock 30 s behind Atlassian (`nbf` in the future) | Medium | Accepted within 60 s clock skew (Nimbus default) | Task 3 |
| 7 | STORY-001 | Compliance | Request rejected — FIT or system token reaching a log line or ProblemDetail | Critical | Never logged; ProblemDetail has only code + generic text | Task 3, Task 6 |
| 8 | STORY-002 | Input extremes | Site URL `http://x.atlassian.net`, `https://evil.com/.atlassian.net`, trailing path or spaces | High | MSG61 without calling Atlassian | Task 4 |
| 9 | STORY-002 | Data integrity | Same cloudId entered for a second Workspace | High | MSG61 (unique `cloud_id`) | Task 4 |
| 10 | STORY-002 | State transitions | Workspace replaces its site while events from the old cloudId are RECEIVED | Medium | Worker finds no site for that cloudId → IGNORED | Task 8 |
| 11 | STORY-003 | Timing | Forge retries the same event (same body) after a slow 202 | High | Unique key hit → 202, one row | Task 6 |
| 12 | STORY-003 | Timing | Two deliveries of the same event in parallel | High | One insert wins; the other catches the unique violation → 202 | Task 6 |
| 13 | STORY-003 | Input extremes | Body without `issue.id` / `issue.key`, or non-JSON | Medium | 400 MSG06, nothing stored | Task 6 |
| 14 | STORY-003 | Integration | `avi:jira:updated:issue` without a status change (label edit) | Medium | 202, nothing stored | Task 6 |
| 15 | STORY-003 | Error cascades | Database down during receive | High | 5xx → Forge retries (up to 4) | Task 6 |
| 16 | STORY-004 | State transitions | Worker crashes after claiming (row stuck PROCESSING) | High | Back to RECEIVED after 10 minutes, processed again | Task 8 |
| 17 | STORY-004 | Timing | Two worker instances poll at the same moment | High | `SKIP LOCKED` → each row claimed once (SQL checked manually; logic unit-tested with a mocked claim) | Task 8 |
| 18 | STORY-004 | Error cascades | Jira 429 with `Retry-After` | Medium | Treated as retryable; next attempt at the 1/5/15-minute schedule | Task 8 |
| 19 | STORY-004 | Error cascades | Issue deleted before processing (Jira 404) | Medium | FAILED at once, token deleted | Task 8 |
| 20 | STORY-004 | Data integrity | `TSOLVE_TOKEN_KEY` changed while events wait | Medium | Decrypt fails → FAILED `TOKEN_UNREADABLE`; app refuses to start without a valid 32-byte key | Task 5, Task 8 |
| 21 | STORY-004 | State transitions | Event still RECEIVED 2 h after receipt (long outage) | High | FAILED `TOKEN_EXPIRED`, token deleted | Task 8 |
| 22 | STORY-005 | State transitions | MSG80 posted, then marking PROCESSED fails | High | `comment_posted` saved right after posting → retry posts nothing | Task 9 |
| 23 | STORY-005 | Error cascades | Crash between the comment POST and saving `comment_posted` | Medium | Possible duplicate MSG80 — accepted risk, narrow window | — |
| 24 | STORY-005 | Input extremes | Suggested solution with ADF-breaking characters, very long text, emoji | Medium | Built as ADF text nodes (no string concatenation of JSON) | Task 9 |
| 25 | STORY-006 | Scale | Done issue with 500 comments | Medium | All pages read (`startAt`/`maxResults` loop) | Task 7, Task 10 |
| 26 | STORY-006 | Input extremes | ADF with tables, mentions, media, code blocks, empty description | Medium | Text extracted; media skipped; null → empty string | Task 7 |
| 27 | STORY-006 | Timing | Done → reopen → Done within seconds | Medium | Two events (different timestamps), both processed; ticket package updates the draft | Task 10 |
| 28 | STORY-007 | Authorization | Panel call for an issue key of another site/project | High | Data read with the caller's site token only; project not in Workspace → "not connected" | Task 11 |
| 29 | STORY-007 | Input extremes | `issueKey` path `../`, lowercase, SQL characters | Medium | Must match `^[A-Z][A-Z0-9_]+-[0-9]+$` → else 400 | Task 11 |
| 30 | STORY-007 | Error cascades | search hangs | Medium | 503 MSG06 at 20 s | Task 11 |
| 31 | STORY-008 | Authorization | Unlinked or deactivated user calls submit directly (bypassing the UI) | Critical | 403 MSG62, ticket package not called | Task 12 |
| 32 | STORY-008 | Timing | Double click on Finish | Medium | Both passed through; ticket package decides (its idempotency) | Task 12 |
| 33 | STORY-010 | Authorization | Non-reporter crafts a suggestion-answer call | Critical | 403 MSG08, nothing recorded | Task 13 |
| 34 | STORY-010 | Timing | Reporter answers twice | Medium | Second → 409 MSG06 | Task 13 |
| 35 | STORY-009 | Scale | 200k finished rows to delete | Low | Batched deletes (e.g. 5 000 per statement) | Task 14 |
| 36 | All | Compliance | Jira error body containing ticket text | Critical | Only status code and error type stored or logged | Task 7, Task 8 |

Open questions for the spec owner: none (MSG80 without links → AC-005.5).

## Risk Review — 2026-10-21 — Verdict: CAUTION

Personas analysed separately (Architect, Security, Performance, User, Devil's advocate); summary below.

Agreements (4+ personas):
- The receive/process split and the FIT chain are sound; no auth bypass found in the design as written.
- The approach rests on Forge Remote behaviours known only from docs (trigger → remote endpoint with a system
  token, payload `timestamp`, FIT `apiBaseUrl`, `_edge/tenant_info`). If any is wrong, STORY-003/004 change.
  Verify them on a real Forge app before writing production code.
- Panel calls also need Jira reads (search again, reporter check), so they need a system token too.

| Topic | Architect | Security | Performance | User | Devil's advocate | Resolution |
|-------|-----------|----------|-------------|------|------------------|------------|
| Forge Remote assumptions | Contract with Forge undocumented in parts | Header names must be exact or tokens leak into wrong places | — | — | One wrong assumption breaks receive + worker | Spike task Task 1 first; adjust spec/design before Task 3 |
| Panel Jira reads | Same client as worker | Token for UI calls must not be stored | One Jira call per refresh, fine | Refresh must stay < 20 s | Could the panel send summary itself? Then the server trusts client data | Enable `appSystemToken` on panel endpoints; use the token in-request only, never persist |
| Outbound URLs | — | SSRF: `apiBaseUrl` and the Admin-entered site URL decide where the server connects | — | — | Both come from trusted-ish sources | Site URL regex `^https://[a-z0-9-]+\.atlassian\.net/?$`; `apiBaseUrl` host must be `api.atlassian.com` or `*.atlassian.net`; RestClient follows no redirects |
| Two filter chains | Needs `identity`'s chain to exist and order after `@Order(1)` | Missing chain = endpoints fall to default config | — | — | — | Agree order with the identity owner before Task 3 |
| Dependencies on 5 packages | Coupling through ports, stubs keep slices demoable | — | — | Demo shows stub data until owners deliver | Could slip the whole flow | Ports + `stub-ports` profile; agree port signatures in Task 1 notes |
| FAILED events invisible | — | — | — | Nobody notices lost triage | — | WARN log `issue_event` id + error type; count FAILED in a log line per run |
| FIT replay within `exp` | — | Low: TLS, minutes-long `exp`, idempotent receive | — | — | Not worth a jti store | Accepted |
| New pom dependency | AGENTS says flag it | — | — | — | — | Team approves `spring-boot-starter-oauth2-resource-server` in the PR |

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Forge Remote behaviour differs from docs | High | Spike on a real Forge dev site, record payload shape (ids only) and headers | Task 1 |
| Token for panel calls persisted or logged | High | In-request use only; test that no panel path writes a token | Task 11 |
| SSRF through site URL or `apiBaseUrl` | High | URL regex, host allowlist, no redirects; tests | Task 4, Task 7 |
| FIT chain ordering with `identity` | High | Agree order; `@WebMvcTest` proving `/api/v1/jira-app/**` uses the FIT chain | Task 3 |
| Silent FAILED events | Medium | WARN log with event id + error type | Task 8 |

## Builder Check — 2026-10-21 (inline)

- Decided in planning: after posting MSG80, tracker calls `TrackerTriagePort.markSuggestionPosted(issue)`, so
  `triage` owns the "suggestion posted / answered" state used by AC-010.1/010.4. tracker's `comment_posted`
  only guards retries (it disappears after 30 days with the event row). (builder check)
- Decided in planning: AC-007.4 "not connected" is a boolean `projectConnected=false` in `PanelResponse`; the
  wording on screen belongs to `jira-app`. (builder check)
- Decided in planning: port method signatures are fixed in Task 1 with each owner and written into
  RESEARCH-001; stubs return fixed sample data. (builder check)
- No blocking questions.
