---
module: tracker
date: 2026-10-21
owner: BA
depth: Deep
---

# Spec: tracker

Server side of the Jira integration (package `vn.tsolve.tracker`). Brief: `artifacts/02-discovery/context-brief.md`.
Messages: SRS `System_Messages.docx` (MSGxx). Database: "T-Solve Database Tables" (31/10) and its
`01_catalog.sql` / `02_tenant.sql` — one tenant database per Workspace, no Workspace ID column in tenant tables.
Terms: **Jira App API** = the endpoints under `/api/v1/jira-app/**` called by the T-Solve Jira App through
Forge Remote; **FIT** = Forge Invocation Token; **site** = a Jira Cloud site, identified by its `cloudId`;
**principal** = the Jira accountId in the FIT of a UI call; **tenant database** = the database of the Workspace
the request belongs to; **discovery** = finding that database from the request (catalog `tenant_workspace`).

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-21 | STORY-001..010 | Initial spec | — |
| 2026-10-21 | STORY-002, 005, 006, 007, 008, 010 | Aligned with SRS System Messages: site entered by the Admin in Workspace Setup (MSG61); MSG80 suggestion comment for Simple issues only; no reminder comment, Solution Form closes with an empty Solution (MSG70); panel needs sign-in (MSG62); reporter answers MSG81 without sign-in | SRS is the team's contract. The reporter rule interprets MSG62 vs MSG81 — to be confirmed with the SRS author / triage owner |
| 2026-10-21 | STORY-005 | Add AC-005.5: MSG80 without the "Related documents" sentence when there are no links | Edge case found in planning; SRS silent — tell the SRS author |
| 2026-10-21 | STORY-005 | AC-005.2/005.5 say "Wiki document links"; tickets used for the suggestion are never linked | Knowledge has Wiki documents and ticket Solutions; SRS MSG80 links only Wiki ("tickets used are not linked") |
| 2026-10-27 | STORY-003, 004 | Add AC-003.7 (an event belongs to its site's Workspace) and AC-004.9 (processing stays in that Workspace); Non-Functional 8 | Multi-tenancy: Workspace = tenant, Hibernate `@TenantId` (architecture, Cross-Cutting Concerns) |
| 2026-11-01 | STORY-001, 003 | Rewrite for one database per tenant. AC-001.1 no longer checks the cloudId; remove AC-001.6. STORY-003 handles only "issue created": store a `suggestion_result` row (Project + issue key) and respond 202; remove AC-003.2, 003.3, 003.5, 003.7; add AC-003.8 (issue of no T-Solve Project). Non-Functional 3 and 8 replaced | New DB design (31/10): tenant DB per Workspace, no `jira_site` / `issue_event` tables; finding the tenant and refusing unknown sites belong to discovery (user, 2026-11-01) |
| 2026-11-01 | STORY-002, 004, 005, 006, 009 | Removed; moved to Out of Scope | No background job in tracker: the system token is not stored, events are not processed later (user, 2026-11-01). Site → cloudId is in the catalog (discovery / workspace) |
| 2026-11-01 | STORY-007, 010 | AC-007.1 drops "the triage decision" (triage_result removed 29/10); AC-010.1/010.2/010.4 read and record the answer in the issue's `suggestion_result` | DB design 31/10: `suggestion_result` keeps the MSG80 text and the reporter's YES / NO |
| 2026-11-01 | STORY-003 | AC-003.6 answers 400 with the field codes of the team's `GlobalExceptionHandler` (MSG01 / MSG02 / MSG06) instead of always MSG06 | `main` (KAN-25) renders validation errors per field; modules may not add their own handler (backend AGENTS §5) |

## Requirements

### Functional
1. Every Jira App API call is authenticated with a FIT from the T-Solve Forge app (STORY-001).
2. "Issue created" events arrive directly from a Forge trigger; an issue of a T-Solve Project is recorded as a
   suggestion result for that issue, and the event is acknowledged at once (STORY-003).
3. The Issue Panel gets its data and can recompute suggestions (STORY-007); the Solution Form saves drafts and
   submits (STORY-008); the reporter answers whether the suggestion helped (STORY-010).

### Non-Functional
1. The event endpoint responds within 1 second at the 95th percentile (Forge's limit is 5 seconds); it calls
   neither Jira nor any AI service.
2. Atlassian's JWKS is cached; a token signed with an unknown key id triggers at most one JWKS refresh.
3. Event calls carry no app system token. A panel call that reads Jira uses the system token of that same
   request only; the tracker never stores a system token.
4. Logs and stored error details contain only ids, issue keys, status codes and error types — never ticket
   content, personal data or FITs.
5. Every Jira App API endpoint declares its roles with `@PreAuthorize`.
6. Errors are RFC 9457 `ProblemDetail` responses carrying an SRS message code: 401/403 → MSG08,
   unexpected or unavailable → MSG06, unless a criterion names another code.
7. UI calls (panel, Solution Form) answer within 20 seconds, below Forge's 25-second limit.
8. The tracker runs inside one tenant database: it reads and writes tenant tables without a Workspace ID and
   never chooses or switches the database itself (discovery does).

## User Stories

### STORY-001: Verify calls from the Jira App
- As the T-Solve server, I want to accept Jira App API calls only from my Forge app so that nobody can forge events or read panel data.
- Acceptance:
  - AC-001.1: WHEN a request to the Jira App API carries a FIT whose signature verifies against Atlassian's JWKS, whose issuer is `forge/invocation-token`, whose audience equals the configured app id, which is within its `nbf`/`exp` window, and which has a `context.cloudId`, THE tracker SHALL pass the request to its handler with the cloudId and the principal (if any).
  - AC-001.2: IF the `Authorization` header is missing or is not a Bearer token, THEN THE tracker SHALL respond 401 with MSG08 without calling the handler.
  - AC-001.3: IF the FIT signature does not verify, or its key id is unknown after one JWKS refresh, THEN THE tracker SHALL respond 401 with MSG08.
  - AC-001.4: IF the FIT issuer is not `forge/invocation-token` or its audience is not the configured app id, THEN THE tracker SHALL respond 401 with MSG08.
  - AC-001.5: IF the FIT is expired or not yet valid, or has no `context.cloudId`, THEN THE tracker SHALL respond 401 with MSG08.
  - AC-001.7: THE tracker SHALL never write a FIT to a log or to an error response.
  - AC-001.8: IF Atlassian's JWKS cannot be fetched and no cached key matches, THEN THE tracker SHALL respond 503 with MSG06 and record the error type.

### STORY-003: Receive "issue created" events
- As the Jira App, I want my "issue created" events accepted within Forge's 5-second limit so that Forge does not retry them and every new issue of a T-Solve Project is known to T-Solve.
- Acceptance:
  - AC-003.1: WHEN an "issue created" event arrives whose `issue.fields.project.key` is the Jira project key of an ACTIVE T-Solve Project, THE tracker SHALL store a suggestion result with that Project, the issue key, no sources, no suggested solution and no reporter answer, and respond 202.
  - AC-003.4: IF a suggestion result for the event's issue key already exists (Forge delivered the event again), THEN THE tracker SHALL respond 202 without storing a second one or changing the existing one.
  - AC-003.6: IF the event body is not a valid "issue created" event (no issue key, an issue key longer than 50 characters, or no `issue.fields.project.key`), THEN THE tracker SHALL respond 400 with the team's validation message code (missing field MSG01, too long MSG02, unreadable body MSG06) and store nothing.
  - AC-003.8: IF the event's project key is not the Jira project key of an ACTIVE T-Solve Project, THEN THE tracker SHALL respond 202 and store nothing.

### STORY-007: Serve the Issue Panel
- As Staff, I want the panel to show suggestions and the Solution state for the ticket I am viewing.
- Acceptance:
  - AC-007.1: WHEN a principal linked to an active T-Solve user requests panel data for an issue of a T-Solve Project, THE tracker SHALL return the stored suggestions (as search allows that user, including MSG76 when search is not available to them) and the Solution Form state from the ticket package: AI draft or MSG67, unsubmitted draft (MSG77), submitted and awaiting review (MSG65), or changes requested (MSG64).
  - AC-007.2: IF the principal is not linked to a T-Solve user, THEN THE tracker SHALL return panel data that only asks the user to sign in (MSG62), plus the MSG81 question when STORY-010 applies.
  - AC-007.3: IF the principal is linked to a deactivated T-Solve user, THEN THE tracker SHALL return panel data with MSG10 and nothing else.
  - AC-007.4: IF the issue's project is not a T-Solve Project, THEN THE tracker SHALL return panel data that says the project is not connected, with no suggestions.
  - AC-007.5: WHEN a linked active user asks to search again, THE tracker SHALL read the issue's current summary and description from Jira, ask search to recompute suggestions, and return them.
  - AC-007.6: IF search or Jira does not answer within 20 seconds, THEN THE tracker SHALL respond 503 with MSG06.

### STORY-008: Use the Solution Form
- As Staff, I want to save, submit or close the Solution Form from inside Jira.
- Acceptance:
  - AC-008.1: WHEN a linked active user saves the Solution Form as a draft, THE tracker SHALL pass it to the ticket package with that T-Solve user as author and return the result.
  - AC-008.2: WHEN a linked active user finishes the Solution Form, THE tracker SHALL pass it to the ticket package with that T-Solve user as author and return the result.
  - AC-008.3: WHEN a linked active user closes the Solution Form opened after resolving the issue, THE tracker SHALL submit an empty Solution for review through the ticket package (MSG70).
  - AC-008.4: IF the principal is not linked to an active T-Solve user, THEN THE tracker SHALL respond 403 with MSG62 and not call the ticket package.
  - AC-008.5: IF the ticket package rejects the request, THEN THE tracker SHALL return its error and message code unchanged.

### STORY-010: Answer the suggestion as the reporter
- As a reporter, I want to say whether the T-Solve suggestion solved my problem without signing in to T-Solve.
- Acceptance:
  - AC-010.1: WHILE the issue's suggestion result has a suggested solution (the MSG80 comment) and no reporter answer, and the principal is the issue's reporter, THE tracker SHALL include the MSG81 question in the panel data, even if the principal is not linked to a T-Solve user.
  - AC-010.2: WHEN the reporter answers Yes or No, THE tracker SHALL store the answer and its time in the issue's suggestion result and return MSG82 for Yes or MSG83 for No.
  - AC-010.3: IF the principal is not the issue's reporter, THEN THE tracker SHALL respond 403 with MSG08 and not record an answer.
  - AC-010.4: IF the issue has no suggestion result with a suggested solution, or it was already answered, THEN THE tracker SHALL respond 409 with MSG06 and not record an answer.

## Out of Scope
- Discovery: finding the tenant database from the request (catalog `tenant_workspace`, by site name or Jira
  `cloudId`), and refusing a FIT whose cloudId belongs to no Workspace (was AC-001.6).
- Registering the Workspace's Jira site and its cloudId (was STORY-002) — Workspace Setup (UC-08) writes
  `tenant_workspace.jira_cloud_id` in the catalog.
- Processing a new issue after it is recorded: reading it from Jira, computing the AI Solution Suggestion,
  posting MSG80 (was STORY-004, STORY-005) — the AI Solution Suggestion owner, who fills the existing
  `suggestion_result` row (update, not insert).
- AI draft Solution when an issue is Done and the "issue updated" event (was STORY-006, AC-003.2, AC-003.3).
- Event clean-up (was STORY-009) — no event table.
- Suggestion search and permissions, AI drafting, Solution Form validation, Evidence files, Review Queue —
  packages `search`, `ticket`, `knowledge` (other owners); tracker only calls them.
- Linking a Jira account to a T-Solve user and the sign-in flow (MSG73–MSG75) — `identity`.
- Staff feedback on suggestions (MSG26) — a later change once `search` defines it.
- Retrying a failed Solution Form submit and MSG69 — `jira-app`.
- Blocking, reverting or validating Jira transitions (rejected in discovery).
- Trackers other than Jira (the factory allows them later); import from Jira with a Project token (MSG47–MSG50).

## Interface Contracts

### Exposed by this module
Paths are added to `frontend/openapi/tsolve-api.yaml` before code (backend rule 8).

| Endpoint | Caller | Auth | Story |
|----------|--------|------|-------|
| `POST /api/v1/jira-app/events/issue-created` | Forge trigger | FIT | 003 |
| `GET /api/v1/jira-app/issues/{issueKey}/panel` | Issue Panel | FIT + principal | 007, 010 |
| `POST /api/v1/jira-app/issues/{issueKey}/suggestions/refresh` | Issue Panel | FIT + principal | 007 |
| `PUT /api/v1/jira-app/issues/{issueKey}/solution-draft` | Solution Form | FIT + principal | 008 |
| `POST /api/v1/jira-app/issues/{issueKey}/solution` | Solution Form (Finish / close) | FIT + principal | 008 |
| `POST /api/v1/jira-app/issues/{issueKey}/suggestion-answer` | Issue Panel (reporter) | FIT + principal | 010 |

Java: `TicketTrackerClientFactory.getClient(TrackerType)` → `TicketTrackerClient` (`getIssue`, `getComments`,
`addComment`), implemented by `JiraClient` (used by STORY-007).

### Required from other modules
Declared by tracker as service interfaces it calls; the owners implement them (stubs until then).

| Owner | Needed operation |
|-------|------------------|
| `project` | Find the ACTIVE T-Solve Project for a Jira project key, or none (a minimal `project` module is added with this change until its owner extends it) |
| `search` | Compute / store / read suggestions for an issue as a given user |
| `ticket` | Read the Solution Form state; save draft; submit (incl. empty) |
| `identity` | Find the T-Solve user (and whether active) for a Jira accountId |
| discovery | Run each Jira App API request against the tenant database of the FIT's site |

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] `frontend/openapi/tsolve-api.yaml` has every endpoint above before its code
- [ ] Liquibase changeset for `department`, `project`, `suggestion_result` as in `02_tenant.sql` (temporary
      until the team's tenant schema replaces it); none edited after apply
- [ ] `mvn spotless:apply` then `mvn verify` pass
- [ ] Security review of FIT verification done

<!-- ACCEPTANCE CRITERIA: one testable statement per item, id AC-<story>.<n> (never reuse a
     removed id), in EARS form: THE <system> SHALL <response>, optionally led by
     WHEN <trigger>, WHILE <state>, IF <unwanted condition> THEN, or WHERE <feature is included>.
     A story with a WHEN criterion also gets an IF … THEN criterion for its failure path.
     SPEC EDIT RULE: Any edit to this file that changes business logic, requirements, stories, or
     acceptance criteria MUST add a new row to the Changelog table above. Once the user has approved the
     spec, any edit locks the module's code until they approve the new version (`sdd.ts approve <module>`). -->
