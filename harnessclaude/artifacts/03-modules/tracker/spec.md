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
| 2026-11-07 | STORY-011 | Add STORY-011 (KAN-22): a recorded new issue gets one suggestion job that asks search (KAN-47) and stores its sources and suggested solution in the issue's `suggestion_result`. Functional 2, Out of Scope and "Required from other modules" updated | Jira KAN-22 asks for the suggestion job. The job uses no Jira token and only calls search, so the 01/11 reason for "no background job" no longer applies. Job runner (JobRunr) = `worker` KAN-161 (Dương), search = KAN-47 (Nhat Minh), MSG80 comment = KAN-131; tracker calls them through ports with stand-ins (user, 2026-11-07) |
| 2026-11-07 | STORY-011 | Run the job on the `worker` module's JobRunr (KAN-102): AC-011.1 stores the issue text in the suggestion result and the job carries only a reference to it; AC-011.6 fails the job so the worker retries it; add AC-011.9 (in-process run while the worker is not enabled). Out of Scope and "Required from other modules" updated | `worker` KAN-102 merged (#139): a job carries only a record UUID and no ticket content, and JobRunr retries a job that fails. User chose retries and, until platform binds the Workspace of a request, a configured dev Workspace (user, 2026-11-07) |
| 2026-11-07 | STORY-012 (new), STORY-011 | Add STORY-012 (KAN-131): after the suggestion job stores a suggestion, the tracker posts it on the issue as the MSG80 comment with the Workspace's Jira service account, once, and records the comment's id and time; AC-011.7 reworded so a rerun posts a comment that is still missing instead of stopping. Functional 2, Non-Functional 9, Out of Scope, Interface Contracts and Required from other modules follow | KAN-131 completes the flow "issue created → AI answers on the issue". User decisions of 07/11: the backend posts with the Workspace's Jira service account (`workspace.service_account_email` / `service_account_token`, created when a tenant is onboarded) rather than a Forge function or the Forge system token; a stand-in reads it from configuration until `workspace` is built; `suggestion_result` gets `jira_comment_id` and `commented_at`; MSG80 is posted without its "Related documents" sentence and names no source, so no Wiki access check or restricted title reaches the comment, which everyone who can see the issue reads — a deviation from SRS MSG80 (`{wiki_links}`) to tell the SRS author |
| 2026-11-12 | STORY-013 | Add the Project module's read-only Jira project lookup, Browse check and deduplicated statuses; expose TrackerProjectService and extend TicketTrackerClient | KAN-127 (project-integration) needs the Browse check and the project's statuses; one Jira client per tracker module boundary, decisions D4, D28 of kan-127-decisions.md |
| 2026-11-12 | STORY-013 | Correct client findProject return type to Optional<String>, empty on project 404; no acceptance criterion changes | Explicit RUN 2 contract, KAN-127 D38 |

## Requirements

### Functional
1. Every Jira App API call is authenticated with a FIT from the T-Solve Forge app (STORY-001).
2. "Issue created" events arrive directly from a Forge trigger; an issue of a T-Solve Project is recorded as a
   suggestion result for that issue, and the event is acknowledged at once (STORY-003). The new issue then gets
   one suggestion job that asks search for a suggestion and stores the answer in that result (STORY-011), then
   posts it on the issue as the MSG80 comment (STORY-012).
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
9. The MSG80 comment is posted with the Workspace's Jira service account, read from the `workspace` module for
   each call; the tracker never stores, logs or returns its email or API token.

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

### STORY-011: Run the suggestion job for a new issue (KAN-22)
- As the system, I want every new issue of a T-Solve Project handed to the AI Solution Suggestion so that its reporter can get a suggested solution (UC-36, GBR-35).
- Acceptance:
  - AC-011.1: WHEN the tracker stores a new suggestion result (AC-003.1), THE tracker SHALL store the issue's summary and description as plain text in it, enqueue exactly one suggestion job that carries only a reference to that suggestion result, after the row is committed, and respond 202 without waiting for the job.
  - AC-011.2: IF the event is for an issue already recorded (AC-003.4) or for a project that is not an ACTIVE Project's (AC-003.8), THEN THE tracker SHALL enqueue no suggestion job.
  - AC-011.3: WHEN a suggestion job runs, THE tracker SHALL ask search for a suggestion with the job's Project, summary and description.
  - AC-011.4: WHEN search returns a suggestion, THE tracker SHALL store its sources and its suggested solution in the issue's suggestion result and change no other field.
  - AC-011.5: WHEN search returns no suggestion, THE tracker SHALL leave the issue's suggestion result with no sources and no suggested solution.
  - AC-011.6: IF search fails or does not answer, or the suggestion result cannot be read or stored, THEN THE suggestion job SHALL fail so that the job runner retries it, leave the issue's suggestion result unchanged, and log only the issue key and the error type.
  - AC-011.7: IF the issue's suggestion result no longer exists or already has a suggested solution when the job runs, THEN THE tracker SHALL not ask search and SHALL change no suggestion field; a stored suggestion whose comment is still missing is posted (AC-012.3).
  - AC-011.8: WHERE the new issue has the label `tsolve-chatbot` (created by the Chatbot), THE tracker SHALL record it and enqueue its suggestion job like any other issue (GBR-40).
  - AC-011.9: WHERE the `worker` job runner is not enabled, THE tracker SHALL run the suggestion job in its own process instead, without retries, and still respond 202 without waiting for it.

### STORY-012: Post the suggestion on the issue (KAN-131)
- As a reporter, I want the T-Solve suggestion to appear on my new issue as a comment so that I can try it without waiting for the team (UC-36, GBR-35).
- Terms: **service account** = the Workspace's Jira account (`workspace.service_account_email` and its API token) that T-Solve uses to read and write Jira issues; **MSG80 comment** = MSG80 filled with the suggestion.
- Acceptance:
  - AC-012.1: WHEN the suggestion job has stored a suggestion (AC-011.4), THE tracker SHALL add one comment to the issue in Jira with the service account, whose text is MSG80 with `{suggested_solution}` = the stored suggested solution and without its "Related documents" sentence.
  - AC-012.2: THE MSG80 comment SHALL never name, link or quote a source the suggestion was built from — neither a Wiki document nor a ticket; the sources stay only in the issue's suggestion result.
  - AC-012.3: WHEN the comment is added, THE tracker SHALL store the Jira comment id and the time in the issue's suggestion result (`jira_comment_id`, `commented_at`); WHEN a suggestion job runs for a suggestion result that has a suggested solution and no comment id, THE tracker SHALL add the comment without asking search again.
  - AC-012.4: IF the issue's suggestion result already has a comment id, THEN THE tracker SHALL add no comment.
  - AC-012.5: IF a comment was added but its id could not be stored (a rerun after a failure), THEN THE tracker SHALL find the existing MSG80 comment of the service account on the issue, store its id and add no second comment.
  - AC-012.6: WHEN the job stores no suggestion (AC-011.5), THE tracker SHALL add no comment.
  - AC-012.7: IF adding the comment fails (Jira unreachable, 5xx or 429, timeout), THEN THE suggestion job SHALL fail so that the job runner retries it, keep the stored suggestion, and log only the issue key, the HTTP status and the error type.
  - AC-012.8: IF the service account is missing, or Jira answers 401, 403 or 404, THEN THE tracker SHALL add no comment, keep the stored suggestion, end the job without a retry and log only the issue key and the reason.

### STORY-013: Read a Jira project for the Project module (KAN-127)
- As the Project module, I want to read the Jira project and its statuses using the Workspace's service account so that the Project Manager can test Browse projects and choose the initial Project settings without entering a token.
- Acceptance:
  - AC-013.1: WHEN `TrackerProjectService.findProject(key)` is called and the Workspace's service account receives 200 from `GET /rest/api/3/project/{key}` and `GET /rest/api/3/project/{key}/statuses`, THE tracker SHALL return `FOUND` with the canonical key returned by the project read and every status returned by the statuses read.
  - AC-013.2: WHEN statuses occur under several issue types, THE tracker SHALL return each status id once in first-seen order across issue types, keeping the first occurrence's name and category.
  - AC-013.3: WHEN a status is returned, THE tracker SHALL expose its string `id`, string `name` and boolean `done`, with `done` true exactly when that status's `statusCategory.key` is `done`.
  - AC-013.4: IF either Jira read answers 404, THEN THE tracker SHALL return `NOT_FOUND` without propagating an exception to the Project module.
  - AC-013.5: IF `JiraServiceAccount.current()` returns no service account, THEN THE tracker SHALL return `DENIED` without calling Jira.
  - AC-013.6: IF either Jira read answers 401, 403 or any other 4xx except 404 and 429, THEN THE tracker SHALL return `DENIED` without propagating an exception to the Project module.
  - AC-013.7: IF either Jira read answers 5xx or 429, times out, is unreachable or does not answer, THEN THE tracker SHALL return `UNAVAILABLE` without a partial project result or an exception crossing the service boundary.
  - AC-013.8: IF a successful Jira response has no nonblank canonical key, a status has no nonblank id or name, or a response body is unreadable, THEN THE tracker SHALL return `UNAVAILABLE` (KAN-127 D35).
  - AC-013.9: WHEN both reads succeed and the statuses response is a valid empty array, THE tracker SHALL return `FOUND` with an empty statuses list.
  - AC-013.10: THE lookup SHALL use `JiraServiceAccount.current()` and the existing `JiraClient` Basic authentication with a 5-second connect timeout and a 10-second read timeout, as STORY-012 does.
  - AC-013.11: IF the project read fails, THEN THE tracker SHALL make no statuses call.
  - AC-013.12: THE lookup's logs SHALL carry only the project key, HTTP status when available and lookup outcome, never the service-account email, API token or a Jira response body.
  - AC-013.13: THE lookup SHALL persist no data and return no service-account credential or Jira response body.

## Out of Scope
- Discovery: finding the tenant database from the request (catalog `tenant_workspace`, by site name or Jira
  `cloudId`), and refusing a FIT whose cloudId belongs to no Workspace (was AC-001.6).
- Registering the Workspace's Jira site and its cloudId (was STORY-002) — Workspace Setup (UC-08) writes
  `tenant_workspace.jira_cloud_id` in the catalog.
- Computing the AI Solution Suggestion (retrieval, relevance threshold, LLM text) — `search` KAN-47; tracker only
  calls it (STORY-011).
- Entering, encrypting and replacing the Workspace's Jira service account and reminding the Admin before its
  token expires (UC-08, UC-09) — `workspace`; tracker only reads it (STORY-012).
- Showing the sources of a suggestion to the reporter (MSG80's "Related documents"): left out of the comment (user,
  2026-11-07); a later change may show them in the Issue Panel with a per-viewer access check.
- The job runner: JobRunr, its storage, retries, not running jobs of a Suspended Workspace, the per-Workspace cap
  — `worker` KAN-102 (merged) / KAN-161; tracker enqueues through the worker's `TenantJobService` when the worker is
  enabled, else runs the job in-process (AC-011.9).
- Binding the request's Workspace: platform / discovery. Until then the tracker binds a configured dev Workspace when
  it enqueues (design, dev and mock only).
- Skipping issues of a deleted Department (KAN-22): the `project` module does not map the Department yet; added
  when it does.
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
`addComment`), implemented by `JiraClient` (used by STORY-007 and STORY-012). KAN-127 adds the following read-only
service and client operations for STORY-013; it does not change those earlier contracts.

| Java interface | Input | Output and rules |
|----------------|-------|------------------|
| `tracker.service.TrackerProjectService.findProject(String projectKey)` | Normalised Jira key from Project; Workspace already bound | `tracker.dto.TrackerProjectLookup(outcome, key, statuses)`; project read first, statuses read next using the returned canonical key. Uses `JiraServiceAccount.current()`, not a Project credential. No database transaction or call back into Project. |
| `tracker.client.TicketTrackerClient.findProject(JiraAccount account, String projectKey)` | Workspace service account and key | `Optional<String>` containing the canonical key from `GET /rest/api/3/project/{key}`, or empty on 404. Same `JiraClient` and status-only client exceptions as STORY-012 for other failures (KAN-127 D38). |
| `tracker.client.TicketTrackerClient.projectStatuses(JiraAccount account, String projectKey)` | Same account and canonical key | `List<tracker.dto.TrackerStatus>` from `GET /rest/api/3/project/{key}/statuses`; flattened across issue types in encounter order. Service returns each id once, first occurrence wins. |

`TrackerProjectLookup` has `outcome` = `FOUND`, `NOT_FOUND`, `DENIED` or `UNAVAILABLE`; `key` is the canonical
nonblank Jira key only for `FOUND`, otherwise null; `statuses` is a non-null list, empty for non-FOUND outcomes.
`TrackerStatus(String id, String name, boolean done)` carries only those three fields. Missing category or any
category other than `done` yields `done: false`. A valid empty statuses array is a successful read. The service
returns no raw JSON or client exception. 429 takes precedence over the generic 4xx mapping. Missing account
makes no Jira call; failed project read makes no statuses call; a failed statuses read discards the project result.
Malformed successful responses follow KAN-127 D35. The Project module maps outcomes to HTTP; STORY-013 adds no REST
endpoint or Forge authentication requirement.

Decisions for this change: `artifacts/04-plans/project-integration/iteration-1/kan-127-decisions.md`, D4-D6,
D28 and D35. Existing Basic authentication remains an interim limit for real scoped service-account tokens
(D5, RESEARCH-003); this change does not implement gateway Bearer authentication.

### Required from other modules
Declared by tracker as service interfaces it calls; the owners implement them (stubs until then).

| Owner | Needed operation |
|-------|------------------|
| `project` | Find the ACTIVE T-Solve Project for a Jira project key, or none (a minimal `project` module is added with this change until its owner extends it) |
| `search` | Compute / store / read suggestions for an issue as a given user |
| `search` (KAN-47) | Suggest for a new issue: from its Project, summary and description, return the sources used and the suggested solution, or no suggestion (STORY-011; a configured stand-in until KAN-47) |
| `worker` (KAN-102) | `TenantJobService.enqueue(handlerType, recordId)` and a `TenantJobHandler` the tracker registers; JobRunr runs it in the Workspace and retries a failed run (STORY-011; in-process when the worker is not enabled) |
| `ticket` | Read the Solution Form state; save draft; submit (incl. empty) |
| `workspace` | The Workspace's Jira site URL and service account (email, decrypted API token) — a stand-in that reads configuration until `workspace` is built (STORY-012) |
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
