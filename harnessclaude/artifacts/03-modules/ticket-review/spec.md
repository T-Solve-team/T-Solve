---
module: ticket-review
date: 2026-10-20
owner: BA
---

# Spec: ticket-review

## Changelog
| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-10-20 | STORY-001 to STORY-006 | Initial review contract (originally based on SRS v3460 layout template); auth/email implementations excluded by user choice | Requested Approve / Reject / Request changes across backend and frontend |
| 2026-10-22 | STORY-002, STORY-004 | D1: Request changes supports selected/all eligible tickets with one comment; D2: remove AC-002.4 and empty-Solution handling; correct authoritative sources | FE Lead decisions D1/D2 (21/10), reaffirmed in PR review; Report3 documents will be corrected |
| 2026-10-23 | STORY-005, STORY-006 | Limit decision batches to 500; restrict unpublished detail to the managing PM or author; hide Review Queue navigation for non-PMs; clarify module ownership and integration prerequisites | FE/BE review of KAN-24 and explicit user decisions |
| 2026-10-24 | STORY-004, STORY-005, STORY-006 | Integrate canonical KAN-31 tickets/history and be-foundation audit; require trusted author IDs, refresh rejected decisions, reachable source validation and clean-machine verification | FE/BE rejection review; user chose dependency integration before merge and forbade deriving author rights from free-text writer names |

## Sources and precedence

- Authoritative business sources: `Report3/UC_list_and_Actors (1).docx`, `Report3/Business_Rules.docx`, and `Report3/System_Messages.docx` in the team documentation folder. A portable source index is in `harnessclaude/artifacts/00-baseline/Report3/README.md`.
- The SRS v3460 file is a layout template, not the requirements source.
- FE Lead D1 (21/10), reaffirmed by this review: Request changes supports several selected tickets with one shared comment, as FE-34 implements. This overrides the current single-ticket wording of GBR-09 / UC-30 until corrected.
- FE Lead D2: a Ticket always has a populated Solution. This overrides empty-Solution wording in GBR-04 / GBR-08 / GBR-09 and MSG20 / MSG70 until corrected. Review has no empty-Solution flag or MSG20 handling. The trusted submission integration supplies valid populated Solutions.
- Catalog edits in this PR stay within MSG13–MSG20, with MSG20 removed under D2. Other catalog entries are left for the frontend foundation change.

## Requirements

### Functional
1. Implement UC-29 and the Approve, Reject and Request changes branches of UC-30, with Ticket Detail support from UC-24.
2. Persist each decision and its audit event; preserve history data required by UC-32 without building the full history screen.
3. Use Report3 Project scope and review MSG codes, with FE Lead D1/D2 taking precedence over the document wording awaiting correction.
4. Expose explicit auth/Project and email integration seams; do not provide fake identities or pretend that an email was delivered.

### Non-Functional
1. No new npm dependency or backend framework. Follow backend layering and generated frontend API client conventions.
2. Concurrent writes and multi-ticket failures leave no partial status/history/audit/notification results.
3. Named AC and bug regression tests plus the repository's required checks pass before reporting implementation complete.

## User Stories

### STORY-001: View my review queue
- As a Project Manager, I want my Projects' waiting tickets so that I can select the tickets I can review.
- Acceptance:
  - AC-001.1: WHEN an authorized Project Manager loads the Review Queue, THE system SHALL return only Pending review Solution Form tickets in Projects they currently manage, ordered by earliest review due date by default.
  - AC-001.2: WHEN the Project Manager filters by Project or changes page or supported column sort, THE system SHALL apply the filter and paging on the server and retain the list state in the frontend URL.
  - AC-001.3: WHEN the Project Manager selects all tickets, THE system SHALL select all eligible ticket IDs matching the current Project filter across pages.
  - AC-001.4: IF the queue is empty or cannot load, THEN THE frontend SHALL show MSG13 for an empty queue or the API's SRS error code with a retry action for a failed query.
  - AC-001.5: IF the requester is unauthenticated, is not a Project Manager, or requests an unmanaged Project, THEN THE backend SHALL deny the request without returning that Project's ticket data.

### STORY-002: Approve and publish
- As the Project Manager, I want to approve one or several tickets so that their Solutions become Published knowledge.
- Acceptance:
  - AC-002.1: WHEN the Project Manager approves eligible Pending review tickets, THE system SHALL set each ticket to Published, save one decision per ticket and remove those tickets from the queue.
  - AC-002.2: WHEN two or more tickets are approved from the queue, THE frontend SHALL require confirmation using MSG19 before sending the mutation and show MSG14 after success.
  - AC-002.3: WHEN one ticket is approved from the queue or Ticket Detail, THE frontend SHALL submit that decision without the bulk confirmation and show MSG14 after success.
  - AC-002.5: WHEN a Project Manager approves a ticket they wrote, THE system SHALL allow the decision and retain both writer and reviewer identity in history.

### STORY-003: Reject with a reason
- As the Project Manager, I want to reject incorrect Solutions with a reason so that the decision is final and explainable.
- Acceptance:
  - AC-003.1: WHEN the Project Manager rejects one or several eligible tickets, THE frontend SHALL show MSG15 and require one reason for the entire selection.
  - AC-003.2: WHEN rejection succeeds, THE system SHALL set each ticket to Rejected, persist its reason and decision, remove it from the queue and show MSG16.
  - AC-003.3: IF the rejection reason is missing or whitespace-only or exceeds 1,000 characters, THEN THE system SHALL return the corresponding MSG01 or MSG02 field error and preserve ticket state and the open popup.
  - AC-003.4: IF a Rejected ticket is submitted for another review decision, THEN THE backend SHALL reject the transition without changing it or returning it to the queue or Published results.

### STORY-004: Request changes on selected tickets
- As the Project Manager, I want writers to correct selected Solution Form tickets using one shared comment so that I can review their corrected Solutions later.
- Acceptance:
  - AC-004.1: WHEN the Project Manager selects one, several or all eligible Pending review Solution Form tickets in the Review Queue or opens one eligible ticket in Ticket Detail, THE frontend SHALL offer one Request changes form whose comment applies to every selected ticket and SHALL show Solution and saved Evidence references read-only in Ticket Detail.
  - AC-004.2: WHEN a valid change comment is submitted for selected eligible tickets, THE system SHALL atomically set every selected ticket to Changes requested, save the same comment and one decision per ticket, preserve each stored review due date, remove those tickets from the queue and show MSG17.
  - AC-004.3: IF the comment is missing or whitespace-only or exceeds 1,000 characters, THEN THE system SHALL return MSG01 or MSG02 and preserve ticket state and the popup.
  - AC-004.4: IF an authorized change request includes any accessible ticket that did not come from the Solution Form, THEN THE backend SHALL reject the entire request with MSG18 before checking its Pending review status and without changes, and THE frontend SHALL use MSG18 to explain the source restriction where applicable; an imported Published ticket SHALL exercise this path without constructing an impossible Pending review import.

### STORY-005: Enforce safe decisions and retain evidence
- As the team, I want decisions to be authorized, atomic and auditable so that retries and failures cannot corrupt review results.
- Acceptance:
  - AC-005.1: IF any selected ticket is missing, outside the reviewer's managed Projects, or no longer Pending review, THEN THE backend SHALL reject the entire decision request without partial writes; WHEN a decision settles with success or an error, THE frontend SHALL refresh the queue and affected detail queries and remove selected IDs absent from the complete eligible queue while retaining eligible off-page selections.
  - AC-005.2: WHEN any decision succeeds, THE system SHALL persist the actor, action, ticket resource, decision time, writer and reason/comment as applicable, with one append-only history record and one AuditService.record call per ticket in the same transaction.
  - AC-005.3: IF persistence fails or concurrent requests attempt to decide the same ticket, THEN THE system SHALL allow at most one successful decision per Pending review state and SHALL roll back failed requests completely; WHEN a concurrent decision causes 409 or a missing ticket causes 404, THE frontend SHALL refresh stale detail/actions and allow retrying the remaining eligible queue selection.
  - AC-005.4: WHEN a decision succeeds, THE system SHALL preserve the saved Solution, Evidence, Jira issue status and stored review due date.
  - AC-005.5: IF a decision payload is empty or contains invalid or repeated ticket IDs, THEN THE backend SHALL reject it before writing any decision records.
  - AC-005.6: WHEN a Published-ticket consumer queries the review service, THE system SHALL expose only Published tickets within the authorization scope supplied by the trusted integration.
  - AC-005.7: WHEN an actor reads a ticket within their trusted Project access scope, THE backend SHALL return an unpublished ticket only to its author or a Project Manager managing that Project, and SHALL return only Published tickets to other readers; IF a reader requests an unpublished ticket they neither manage nor authored, THEN THE backend SHALL return 404 MSG04 without its content or review comment.
  - AC-005.8: IF any review decision contains more than 500 ticket IDs, THEN THE backend SHALL return 400 MSG06 before acquiring ticket locks or writing records, and THE frontend SHALL disable all decision actions and show guidance to select at most 500 tickets; WHEN Select all exceeds that limit, THE frontend SHALL retain the complete selection until the user narrows it, without splitting it into partially committed requests.
  - AC-005.9: WHEN review reads or decides a ticket, THE system SHALL use the ticket-repository's canonical ticket identity, Solution/status and history through its service contract, without a second authoritative ticket/Solution store or incompatible ownership of ticket_review_decision; WHEN Project URL scope changes through filters or browser history, THE frontend SHALL clear the old scope's selection.
  - AC-005.10: WHEN a decision is committed, THE system SHALL call be-foundation's shared AuditService.record(AuditAction, resourceType, resourceId) in the same transaction and SHALL leave audit schema ownership and legacy message-only exception compatibility to be-foundation, without review mappings to removed resource/result columns or assigning new codes to legacy module callers.

### STORY-006: Integrate auth and notifications explicitly
- As the integration developer, I want clear seams so that real authentication and delivery can be connected without rewriting decision logic.
- Acceptance:
  - AC-006.1: WHEN the backend determines reviewer identity or Project rights, THE system SHALL obtain them exclusively from a trusted server-side auth/Project interface and SHALL ignore client-supplied actor or management claims.
  - AC-006.2: IF the auth/Project integration is absent or cannot verify access, THEN THE module SHALL deny review access without a default reviewer or permissive fallback.
  - AC-006.3: WHEN a ticket is rejected or changes are requested, THE system SHALL save one pending email handoff per ticket containing recipient identity, decision, reason/comment and decision reference in the same transaction, without sending network email inside the transaction.
  - AC-006.4: IF the external email integration is unavailable, THEN THE system SHALL retain the committed decision and its pending notification without claiming delivery; the integration contract SHALL require the sender to log errors and retry at most three times within one hour under GBR-27.
  - AC-006.5: WHEN the frontend operates with mocking disabled, THE review UI SHALL use the generated real review endpoints and SHALL handle loading, empty, error and mutation-pending states.
  - AC-006.6: WHEN implementation is delivered, THE integration documentation SHALL identify auth, Project rights, ticket submission, Evidence retrieval, mail delivery/retry and downstream search/triage/chatbot integration as external prerequisites and distinguish automated contract tests from a complete live test.
  - AC-006.7: WHEN a user without the Project Manager role reads Ticket Detail, THE frontend SHALL hide navigation to the Review Queue.
  - AC-006.8: WHEN Maven verification runs on a clean checkout with Java 21 and Docker, THE backend tests SHALL obtain isolated PostgreSQL with Liquibase through Testcontainers and non-secret test configuration without host SERVER_PORT/database credentials, SHALL clean up their database lifecycle, and SHALL run in backend CI.

## Out of Scope
- Implementing Google OIDC, account administration, /me session provisioning or Project administration/assignments. The existing frontend session guard still requires its real upstream /me implementation.
- Implementing SMTP transport, email delivery worker or retries; this module persists handoffs and defines that integration's obligations.
- Jira submission/resubmission, issue-panel rendering, Evidence upload/download storage and AI drafting. Expose review data/comment for their integrations; never fabricate evidence URLs or Jira delivery.
- Search engine, triage, chatbot, full Review History screen, expiry scheduler, send back, unpublish and re-publish.
- Changing legacy Solution lifecycle endpoints or assigning their error codes from review. Existing review data conversion requires an explicit canonical identity mapping; no destructive or inferred migration is authorized.
- Production deployment, commits without agreement, pushing, merging or opening a PR.

## Interface Contracts

### Exposed by this module
- `GET /api/v1/review-queue?projectId=&page=0&size=20&sort=reviewDueDate,asc` returns Spring-style `{content,page}`; only a documented allowlist of sort fields is accepted.
- `GET /api/v1/review-queue/ticket-ids?projectId=` returns `{ticketIds}` for the same authorization/filter scope.
- `POST /api/v1/reviews/approve`: `{ticketIds: number[]}`; `POST /api/v1/reviews/reject`: `{ticketIds: number[], reason: string}`; `POST /api/v1/reviews/request-changes`: `{ticketIds: number[], comment: string}`. Success returns `{count}`. IDs are positive integers, with at most 500 IDs per request; reasons/comments are required and limited to 1,000 characters.
- Ticket Detail read API returns review ticket identity, Project, writer, source, status, review dates, Solution sections, Evidence metadata and current review comment; server-side access is mandatory.
- `GET /api/v1/tickets/{id}` uses canonical ticket-repository IDs after dependency integration. No equality with IDs from the old review store is inferred. Preserve the frontend route and generated contract; ticket-repository owns the eventual tickets tag/controller consolidation.
- Java module interfaces exchange DTO records, never entities or repositories. Ticket-repository owns canonical tickets, saved Solutions, imports and decision history; review owns decision orchestration and notification handoffs. Required queue/locking/history operations must be exposed by the canonical service contract before merge.
- Failure responses are RFC 9457 Problem Details with SRS `code` and structured validation fields: 400 invalid input, 401 unauthenticated, 403 unauthorized, 404 missing resource, 409 state conflict, 500 persistence failure.
- Liquibase ownership must be reconciled with KAN-31 before merge. A numeric file prefix alone is not a changeset identity: author/id/path differ, but both current branches create incompatible ticket_review_decision tables. Preserve applied changesets; design and verify an ordered forward migration against fresh and existing schemas. Retain existing rows until an explicit canonical ID mapping and history preservation are verified; no drop/reset workaround is permitted.
- Shared frontend MSG catalog and generated API files need scoped updates to implement the Report3 decisions. Generated files are produced only by `npm run generate:api`; this shared-file work must receive the team's normal FE Lead review before merge.

### Required from other modules
- Auth/Project port: active authenticated actor and current managed Project/access scope; missing adapter denies access. Identity/rights never come from request JSON or query parameters.
- Canonical ticket-repository service (KAN-31): canonical IDs, locked batch read/update, source, status, populated Solution, saved Evidence and decision history in the caller's transaction. Import publication under GBR-23 belongs to that module; review does not manufacture Pending review imports to test MSG18.
- Submission integration supplies canonical ticket identity, trusted immutable writer user ID and already determined queue/review due dates. Author access compares trusted IDs only. KAN-31's free-text writer name never grants author rights; absent trusted author identity fails closed for author access. Managing PM access remains separately authorized. The submitting integration owns membership checks and initial date calculation under GBR-10.
- be-foundation: shared AuditService/AuditAction, role constants/current principal and compatible common exceptions; no independent review audit writer or second method-security configuration after integration.
- Email sender: consumes pending records and implements actual delivery/retry under GBR-27. A pending record is not delivery evidence.
- Existing /me endpoint supplies frontend session and managed Project references. This module does not replace it with mock data.
- Dependency state: KAN-31 is currently an unmerged branch and be-foundation is planned. This spec defines the required integration, not an assertion that either dependency already provides every service operation. The PR cannot merge until their contracts, migration order and combined verification are available. Mail worker/retries remain excluded as stated in AC-006.4; pending outbox records do not prove delivery or retry execution.

## Definition of Done
- [ ] Spec approved and design/tasks reviewed before code; module and feature transitions use sdd.ts.
- [ ] Every criterion has an automated test naming its AC ID; every fixed bug has a regression test naming its BUG ID.
- [ ] Backend Maven verify and spotless:check pass; frontend format:check, lint, typecheck, tests and build pass.
- [ ] OpenAPI is updated first; generated client is regenerated and verified.
- [ ] Browser verification covers queue selection, confirmation, field errors, Ticket Detail and multi-ticket Request changes with one shared comment.
- [ ] Bug log retains found defects, including resolved defects, with honest verification status.
- [ ] Missing live integrations and remaining test limitations are documented.
- [ ] Stop after each verified feature and request its individual commit agreement before starting the next feature.
