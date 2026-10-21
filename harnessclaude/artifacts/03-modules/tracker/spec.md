---
module: tracker
date: 2026-10-21
owner: BA
depth: Deep
---

# Spec: tracker

Server side of the Jira integration (package `vn.tsolve.tracker`). Brief: `artifacts/02-discovery/context-brief.md`.
Messages: SRS `System_Messages.docx` (MSGxx).
Terms: **Jira App API** = the endpoints under `/api/v1/jira-app/**` called by the T-Solve Jira App through
Forge Remote; **FIT** = Forge Invocation Token; **system token** = the Forge app system token sent in the
`x-forge-oauth-system` header; **site** = a Jira Cloud site, identified by its `cloudId`; **principal** = the
Jira accountId in the FIT of a UI call.

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-21 | STORY-001..010 | Initial spec | — |
| 2026-10-21 | STORY-002, 005, 006, 007, 008, 010 | Aligned with SRS System Messages: site entered by the Admin in Workspace Setup (MSG61); MSG80 suggestion comment for Simple issues only; no reminder comment, Solution Form closes with an empty Solution (MSG70); panel needs sign-in (MSG62); reporter answers MSG81 without sign-in | SRS is the team's contract. The reporter rule interprets MSG62 vs MSG81 — to be confirmed with the SRS author / triage owner |
| 2026-10-21 | STORY-005 | Add AC-005.5: MSG80 without the "Related documents" sentence when there are no links | Edge case found in planning; SRS silent — tell the SRS author |
| 2026-10-21 | STORY-005 | AC-005.2/005.5 say "Wiki document links"; tickets used for the suggestion are never linked | Knowledge has Wiki documents and ticket Solutions; SRS MSG80 links only Wiki ("tickets used are not linked") |

## Requirements

### Functional
1. Every Jira App API call is authenticated with a FIT from the Workspace's Jira site (STORY-001).
2. The Workspace's Jira site is resolved to its cloudId when the Admin enters it in Workspace Setup (STORY-002).
3. Issue events arrive directly from Forge triggers; they are recorded and acknowledged at once, and processed
   later (STORY-003, STORY-004).
4. A created ticket is triaged; a Simple ticket with a suggested solution gets the MSG80 comment (STORY-005).
5. A ticket that moves into the Done status category gets an AI draft Solution (STORY-006).
6. The Issue Panel gets its data and can recompute suggestions (STORY-007); the Solution Form saves drafts and
   submits (STORY-008); the reporter answers whether the suggestion helped (STORY-010).
7. Processed events are kept 30 days (STORY-009).

### Non-Functional
1. The event endpoints respond within 1 second at the 95th percentile (Forge's limit is 5 seconds).
2. Atlassian's JWKS is cached; a token signed with an unknown key id triggers at most one JWKS refresh.
3. Stored system tokens are encrypted with AES-GCM using a key from the environment, and deleted when their
   event is finished or 2 hours after it was received, whichever is first.
4. Logs and stored error details contain only ids, issue keys, status codes and error types — never ticket
   content, personal data, FITs or system tokens.
5. Every Jira App API endpoint declares its roles with `@PreAuthorize`.
6. Errors are RFC 9457 `ProblemDetail` responses carrying an SRS message code: 401/403 → MSG08,
   unexpected or unavailable → MSG06, unless a criterion names another code.
7. UI calls (panel, Solution Form) answer within 20 seconds, below Forge's 25-second limit.

## User Stories

### STORY-001: Verify calls from the Jira App
- As the T-Solve server, I want to accept Jira App API calls only from my Forge app on our Jira site so that nobody can forge events or read panel data.
- Acceptance:
  - AC-001.1: WHEN a request to the Jira App API carries a FIT whose signature verifies against Atlassian's JWKS, whose issuer is `forge/invocation-token`, whose audience equals the configured app id, which is within its `nbf`/`exp` window, and whose `context.cloudId` is the cloudId of a Workspace's Jira site, THE tracker SHALL pass the request to its handler with the cloudId, the Workspace and the principal (if any).
  - AC-001.2: IF the `Authorization` header is missing or is not a Bearer token, THEN THE tracker SHALL respond 401 with MSG08 without calling the handler.
  - AC-001.3: IF the FIT signature does not verify, or its key id is unknown after one JWKS refresh, THEN THE tracker SHALL respond 401 with MSG08.
  - AC-001.4: IF the FIT issuer is not `forge/invocation-token` or its audience is not the configured app id, THEN THE tracker SHALL respond 401 with MSG08.
  - AC-001.5: IF the FIT is expired or not yet valid, THEN THE tracker SHALL respond 401 with MSG08.
  - AC-001.6: IF the FIT is valid but its cloudId is not the cloudId of any Workspace's Jira site, THEN THE tracker SHALL respond 403 with MSG08.
  - AC-001.7: THE tracker SHALL never write a FIT or a system token to a log or to an error response.
  - AC-001.8: IF Atlassian's JWKS cannot be fetched and no cached key matches, THEN THE tracker SHALL respond 503 with MSG06 and record the error type.

### STORY-002: Resolve the Workspace's Jira site
- As an Admin, I want to enter our Jira site address in Workspace Setup so that T-Solve accepts only our Jira, without me looking up a cloudId.
- Acceptance:
  - AC-002.1: WHEN the workspace package registers a site address for a Workspace, THE tracker SHALL read the site's cloudId from Atlassian and store the address and cloudId for that Workspace.
  - AC-002.2: IF the site address is not an `https://<name>.atlassian.net` address, THEN THE tracker SHALL reject it with MSG61 without calling Atlassian.
  - AC-002.3: IF Atlassian does not return a cloudId for the address (unknown site, network error, timeout of 10 seconds), THEN THE tracker SHALL reject it with an error type the workspace package can show, and keep the Workspace's previous site.
  - AC-002.4: WHEN a Workspace registers a new site address, THE tracker SHALL replace its previous site so that calls from the old cloudId are refused.
  - AC-002.5: IF the cloudId is already the site of another Workspace, THEN THE tracker SHALL reject it with MSG61.

### STORY-003: Receive issue events
- As the Jira App, I want my issue events accepted within Forge's 5-second limit so that Forge does not retry them and nothing is lost.
- Acceptance:
  - AC-003.1: WHEN an "issue created" event arrives, THE tracker SHALL record it as RECEIVED with the cloudId, issue id, issue key, event type, the encrypted system token and the receive time, and respond 202.
  - AC-003.2: WHEN an "issue updated" event arrives whose changelog contains a `status` change, THE tracker SHALL record it as RECEIVED in the same way and respond 202.
  - AC-003.3: WHEN an "issue updated" event arrives whose changelog contains no `status` change, THE tracker SHALL respond 202 without recording it.
  - AC-003.4: IF an event that is already recorded is delivered again, THEN THE tracker SHALL respond 202 without recording it a second time.
  - AC-003.5: IF an event arrives without the `x-forge-oauth-system` header, THEN THE tracker SHALL respond 400 with MSG06 and not record it.
  - AC-003.6: IF the event body is not a valid issue event (no issue id or key), THEN THE tracker SHALL respond 400 with MSG06 and not record it.

### STORY-004: Process recorded events
- As the T-Solve server, I want recorded events processed reliably in the background so that slow Jira or AI calls never block Forge.
- Acceptance:
  - AC-004.1: WHILE an event is RECEIVED and due, THE tracker SHALL claim it so that only one worker processes it at a time.
  - AC-004.2: WHEN the tracker processes an event, THE tracker SHALL read the issue from Jira with that event's system token: key, summary, description, issue type, priority, project key, status with its status category, resolution, created and resolution dates, reporter accountId, and assignee accountId and display name.
  - AC-004.3: IF the issue's project key does not belong to a T-Solve Project of the site's Workspace, THEN THE tracker SHALL mark the event IGNORED and do nothing else.
  - AC-004.4: IF an "issue updated" event's issue is not in the Done status category when read, THEN THE tracker SHALL mark the event IGNORED.
  - AC-004.5: IF processing fails with a retryable error (Jira 5xx or 429, timeout, a dependency unavailable), THEN THE tracker SHALL schedule the event again after 1, 5 and 15 minutes, and mark it FAILED with the error type after the third retry fails.
  - AC-004.6: IF processing fails with a non-retryable error (Jira 401, 403 or 404), THEN THE tracker SHALL mark the event FAILED with the error type at once.
  - AC-004.7: IF an event is still unfinished 2 hours after it was received, THEN THE tracker SHALL mark it FAILED with the error type `TOKEN_EXPIRED` and delete its token.
  - AC-004.8: WHEN an event becomes PROCESSED, IGNORED or FAILED, THE tracker SHALL delete its stored system token.

### STORY-005: Triage a created ticket
- As a reporter, I want a simple problem answered right away so that I may not need to wait for the team.
- Acceptance:
  - AC-005.1: WHEN a created ticket of a T-Solve Project is processed, THE tracker SHALL pass it to triage, ask search to compute and store suggestions for it, and record the triage decision for the panel.
  - AC-005.2: WHEN triage classifies the ticket as Simple with a suggested solution, THE tracker SHALL add one comment to the ticket with MSG80 filled with the suggested solution and the related Wiki document links, visible to the reporter and with no link to the tickets whose Solutions the suggestion was built from, then mark the event PROCESSED.
  - AC-005.3: WHEN triage classifies the ticket as Complex or Sensitive, or gives no suggested solution, THE tracker SHALL add no comment and mark the event PROCESSED.
  - AC-005.4: IF the event is processed again after the MSG80 comment was added (a retry after a later step failed), THEN THE tracker SHALL not add a second comment.
  - AC-005.5: IF triage returns no related Wiki document links, THEN THE tracker SHALL post MSG80 without its "Related documents" sentence.

### STORY-006: Draft a Solution when a ticket is Done
- As Staff, I want an AI draft Solution ready when I resolve a ticket so that I only check and submit it in the Solution Form.
- Acceptance:
  - AC-006.1: WHEN an "issue updated" event of a T-Solve Project is processed and the issue is in the Done status category, THE tracker SHALL read all of the issue's comments (every page) and pass the issue, comments and assignee to the ticket package to create or update the AI draft, then mark the event PROCESSED.
  - AC-006.2: IF the ticket package cannot create the AI draft, THEN THE tracker SHALL mark the event PROCESSED and the panel data SHALL report the draft as unavailable (MSG67).
  - AC-006.3: THE tracker SHALL add no comment to a ticket that moves into the Done status category.

### STORY-007: Serve the Issue Panel
- As Staff, I want the panel to show suggestions and the Solution state for the ticket I am viewing.
- Acceptance:
  - AC-007.1: WHEN a principal linked to an active T-Solve user requests panel data for an issue of a T-Solve Project, THE tracker SHALL return the stored suggestions (as search allows that user, including MSG76 when search is not available to them), the triage decision if any, and the Solution Form state from the ticket package: AI draft or MSG67, unsubmitted draft (MSG77), submitted and awaiting review (MSG65), or changes requested (MSG64).
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

### STORY-009: Clean up old events
- As the operator, I want old event rows removed so that the table stays small.
- Acceptance:
  - AC-009.1: WHEN the daily clean-up runs, THE tracker SHALL delete PROCESSED, IGNORED and FAILED events received more than 30 days earlier.
  - AC-009.2: THE tracker SHALL not delete RECEIVED events during clean-up.
  - AC-009.3: IF the clean-up fails, THEN THE tracker SHALL record the error type and leave the rows for the next daily run.

### STORY-010: Answer the suggestion as the reporter
- As a reporter, I want to say whether the T-Solve suggestion solved my problem without signing in to T-Solve.
- Acceptance:
  - AC-010.1: WHILE an issue has an MSG80 suggestion with no answer yet and the principal is the issue's reporter, THE tracker SHALL include the MSG81 question in the panel data, even if the principal is not linked to a T-Solve user.
  - AC-010.2: WHEN the reporter answers Yes or No, THE tracker SHALL pass the answer to triage and return MSG82 for Yes or MSG83 for No.
  - AC-010.3: IF the principal is not the issue's reporter, THEN THE tracker SHALL respond 403 with MSG08 and not record an answer.
  - AC-010.4: IF the issue has no MSG80 suggestion or it was already answered, THEN THE tracker SHALL respond 409 with MSG06 and not record an answer.

## Out of Scope
- Triage logic, suggestion search and permissions, AI drafting, Solution Form validation, Evidence files,
  Review Queue — packages `triage`, `search`, `ticket`, `knowledge` (other owners); tracker only calls them.
- Workspace Setup screen and its validation messages — `workspace`; tracker only resolves the site.
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
| `POST /api/v1/jira-app/events/issue-created` | Forge trigger | FIT + system token | 003 |
| `POST /api/v1/jira-app/events/issue-updated` | Forge trigger | FIT + system token | 003 |
| `GET /api/v1/jira-app/issues/{issueKey}/panel` | Issue Panel | FIT + principal | 007, 010 |
| `POST /api/v1/jira-app/issues/{issueKey}/suggestions/refresh` | Issue Panel | FIT + principal | 007 |
| `PUT /api/v1/jira-app/issues/{issueKey}/solution-draft` | Solution Form | FIT + principal | 008 |
| `POST /api/v1/jira-app/issues/{issueKey}/solution` | Solution Form (Finish / close) | FIT + principal | 008 |
| `POST /api/v1/jira-app/issues/{issueKey}/suggestion-answer` | Issue Panel (reporter) | FIT + principal | 010 |

Java: `JiraSiteService.registerSite(workspaceId, siteUrl)` for the workspace package (STORY-002);
`TicketTrackerClientFactory.getClient(TrackerType)` → `TicketTrackerClient` (`getIssue`, `getComments`,
`addComment`), implemented by `JiraClient`.

### Required from other modules
Declared by tracker as service interfaces it calls; the owners implement them (stubs until then).

| Owner | Needed operation |
|-------|------------------|
| `project` | Find the T-Solve Project for (Workspace, Jira project key), or none |
| `triage` | Triage an issue → class (Simple / Complex / Sensitive), suggested solution (may draw on related ticket Solutions), related Wiki document links; record the reporter's Yes/No answer |
| `search` | Compute / store / read suggestions for an issue as a given user |
| `ticket` | Create/update AI draft from a Done issue + comments; read the Solution Form state; save draft; submit (incl. empty) |
| `identity` | Find the T-Solve user (and whether active) for a Jira accountId |

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] `frontend/openapi/tsolve-api.yaml` has every endpoint above before its code
- [ ] Liquibase changesets for the site and event tables; none edited after apply
- [ ] `mvn spotless:apply` then `mvn verify` pass
- [ ] Security review of FIT verification and token storage done

<!-- ACCEPTANCE CRITERIA: one testable statement per item, id AC-<story>.<n> (never reuse a
     removed id), in EARS form: THE <system> SHALL <response>, optionally led by
     WHEN <trigger>, WHILE <state>, IF <unwanted condition> THEN, or WHERE <feature is included>.
     A story with a WHEN criterion also gets an IF … THEN criterion for its failure path.
     SPEC EDIT RULE: Any edit to this file that changes business logic, requirements, stories, or
     acceptance criteria MUST add a new row to the Changelog table above. Once the user has approved the
     spec, any edit locks the module's code until they approve the new version (`sdd.ts approve <module>`). -->
