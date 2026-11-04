---
module: ticket-review
date: 2026-11-01
owner: BA
---

# Spec: ticket-review

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-01 | STORY-008 | Add KAN-159 read-only Review History frontend and proposed query/MSW contract | Continue remaining frontend work; reuse Department convention and existing decision history |
| 2026-11-01 | STORY-007 | Add KAN-158 Expired Tickets frontend, API proposal and mock send-back flow | User requested completion of FE today, one Jira branch per task; explicitly permits this branch to depend on KAN-24 |
| 2026-11-01 | STORY-001–006 | New-main frontend/MSW delivery contract for KAN-24; UUID ids, Department filter, explicit recovery and atomic mock decisions | User chose a new branch/PR from main, frontend + MSW/OpenAPI first, and Department under current guidelines; PR #26 remains separate |

## Sources and precedence

Jira KAN-24 and its KAN-137 comment inspected on 2026-11-01; frontend AGENTS/guidelines and current shared contracts; FE Lead D1/D2 and PR #26 review feedback. User decisions on this date settle scope and filter precedence. The old PR's approved backend specification is not imported as an authorization to change backend or shared infrastructure. D1 keeps multi-ticket Request Changes with one comment; D2 removes the impossible empty-Solution check and MSG20. Existing 1,000-character form limits and the previously agreed 500-ticket batch limit are retained. Production canonical Ticket integration, audit, Jira delivery and backend security remain future work.

## Requirements

### Functional

1. Complete screens 7.1 Review Queue, 7.2 Request Changes and 7.3 Reject Ticket and their decision controls on 5.5 Ticket Detail using the existing feature and UI kit.
2. Use generated hooks, UUID ids and mutable Workspace-scoped MSW fixtures to demonstrate complete decisions, failure handling and retained decision history.
3. Preserve the current Department filter and sortable Project column; no Project filter.
4. Treat the OpenAPI changes as a proposal awaiting backend integration; no claim of production authorization, transaction durability, audit or Jira delivery.

### Non-Functional

1. No new package, route/menu registration, manual generated-client edit, dependency upgrade or global test/CI relaxation.
2. Every acceptance criterion has a named test within this module's test paths. Format, lint, typecheck, full frontend tests, build and repeat API generation pass before completion is claimed.
3. Existing unrelated API paths, session mocks, user flows and generated contracts are preserved. Shared API and Ticket Detail integration require code-owner review.
4. User-entered comments and ticket content render as text; no sensitive content is added to logs or browser persistent storage. Fixture decisions/history last only within the mock session.

## User Stories

### STORY-001: View and select the review queue
- As a Project Manager, I want to review pending Solutions from Projects I manage using the standard Department filter.
- Acceptance:
  - AC-001.1: WHEN the Review Queue loads, THE frontend and mock API SHALL return only Pending review Solution Form tickets in the current Workspace's Projects managed by the signed-in Project Manager, ordered by earliest review due date by default.
  - AC-001.2: WHEN the user changes Department, page or supported column sort, THE frontend SHALL preserve that state in the URL and request corresponding server-side results; THE frontend SHALL offer no Project filter.
  - AC-001.3: WHEN the user selects across pages or chooses Select all, THE frontend SHALL use the complete eligible id set for the current filter, retain eligible off-page selections and clear selection on a filter change including browser Back/Forward navigation.
  - AC-001.4: IF the queue is empty or its request fails, THEN THE frontend SHALL show MSG13 or the catalog error with retry respectively; WHILE the initial request is pending, THE frontend SHALL show its loading state.
  - AC-001.5: IF a user is signed out or lacks Project Manager, THEN THE existing route guard SHALL deny queue access and THE mock API SHALL reject direct queue or decision calls without returning or changing another user's queue.
  - AC-001.6: WHEN cached eligibility is refreshing or a selected ticket is decided elsewhere, THE frontend SHALL reconcile selection only against fresh successful eligibility results and SHALL retain newly selected eligible ids while that refresh is pending.

### STORY-002: Approve selected Solutions
- As a Project Manager, I want to approve one or more pending tickets so their Solutions become Published knowledge.
- Acceptance:
  - AC-002.1: WHEN Approve succeeds for eligible tickets, THE mock API SHALL publish every selected ticket, append one decision per ticket and remove them from the queue; THE frontend SHALL show MSG14 and clear the completed selection.
  - AC-002.2: WHEN two or more tickets are approved from the queue, THE frontend SHALL require MSG19 confirmation before submitting.
  - AC-002.3: WHEN one ticket is approved from the queue or Ticket Detail, THE frontend SHALL submit without the bulk confirmation and SHALL allow a managing Project Manager to approve a ticket they authored.
  - AC-002.5: IF the approval confirmation is cancelled, THEN THE frontend and mock API SHALL retain selection, confirmed ticket states and decision history without submitting a mutation.

### STORY-003: Reject with one reason
- As a Project Manager, I want to reject incorrect Solutions with a required shared reason.
- Acceptance:
  - AC-003.1: WHEN Reject is chosen for one or more eligible tickets, THE frontend SHALL open the existing Reject Ticket popup with MSG15 and one required reason for the entire selection.
  - AC-003.2: IF the reason is missing, whitespace-only or exceeds 1,000 characters, THEN THE frontend and mock API SHALL reject it with MSG01 or MSG02 and preserve the popup, selection and confirmed ticket states.
  - AC-003.3: WHEN rejection succeeds, THE mock API SHALL set each selected ticket to Rejected and append its reason and decision; THE frontend SHALL close the popup, clear completed selection and show MSG16.
  - AC-003.4: IF a Rejected ticket is submitted for another decision, THEN THE mock API SHALL reject the entire request without altering the ticket or its decision history.

### STORY-004: Request changes with one comment
- As a Project Manager, I want one comment applied to every selected Solution Form ticket.
- Acceptance:
  - AC-004.1: WHEN Request Changes is chosen on the queue or one eligible Ticket Detail, THE frontend SHALL open one comment form for all selected tickets.
  - AC-004.2: IF the comment is missing, whitespace-only or exceeds 1,000 characters, THEN THE frontend and mock API SHALL return MSG01 or MSG02 while preserving entered input, selection and confirmed ticket states.
  - AC-004.3: WHEN Request Changes succeeds, THE mock API SHALL set every selected ticket to Changes requested, append the same comment and one decision per ticket and remove them from the queue; THE frontend SHALL close the popup and show MSG17.
  - AC-004.4: IF a change request includes an accessible ticket not submitted through the Solution Form, THEN THE mock API SHALL reject the whole request with MSG18 before status validation; THE frontend SHALL explain that restriction accessibly where such an action is displayed.

### STORY-005: Keep decisions safe and recover from stale data
- As a Project Manager, I want failed or stale decisions to leave consistent ticket state and let me retry.
- Acceptance:
  - AC-005.1: WHEN review requests or responses carry ticket or Project identifiers, THE OpenAPI proposal, generated client, frontend and mock API SHALL use UUID strings without numeric coercion.
  - AC-005.2: IF a decision includes zero, duplicate, invalid or more than 500 ticket ids, THEN THE mock API SHALL reject it before writing; WHILE selection exceeds 500, THE frontend SHALL disable all decision actions and expose accessible guidance without silently truncating selection.
  - AC-005.3: IF any selected ticket is missing, unmanaged, foreign to the current Workspace or no longer Pending review, THEN THE mock API SHALL reject the whole batch without partial ticket or decision-history changes.
  - AC-005.4: WHEN a decision settles with success or failure, THE frontend SHALL invalidate affected queue, eligibility, Ticket Detail and Ticket List queries; IF the response is 404 or 409, THEN THE frontend SHALL refresh stale details/actions and reconcile selection against the refreshed complete eligible set.
  - AC-005.5: IF a mutation fails, THEN THE frontend SHALL show its catalog error and preserve entered popup data and remaining eligible selection for retry; WHILE submission is pending, THE frontend SHALL prevent duplicate mutation calls and disable affected decision controls.
  - AC-005.6: WHEN a queue mutation succeeds, THE frontend SHALL close its popup after the mutation response without waiting for unrelated query refetches; IF an invalidated query fails, THEN THE frontend SHALL expose its query error without reporting the completed decision as failed.
  - AC-005.7: WHEN a mock decision succeeds, THE mock API SHALL preserve Solution, Evidence, source Jira status and stored review due date, and append actor, action, ticket id, timestamp and applicable reason/comment to in-memory history; IF the request fails, THEN THE mock API SHALL leave that history unchanged.

### STORY-006: Inspect Ticket Detail and available decisions
- As a reader, I want to inspect a permitted ticket and see only the review actions available to me.
- Acceptance:
  - AC-006.1: WHEN a permitted Ticket Detail loads, THE frontend SHALL show confirmed ticket information, source/status, read-only populated Solution and saved Evidence references through the proposed generated API.
  - AC-006.2: IF the route id is invalid or the API returns 404, THEN THE frontend SHALL show MSG04 without coercing the id; IF loading fails otherwise, THEN THE frontend SHALL show its catalog error and retry.
  - AC-006.3: WHEN the current user manages the ticket's Project and the confirmed ticket is Pending review, THE frontend SHALL offer Approve, Reject and Request Changes; IF the user does not manage it or its status is no longer pending, THEN THE frontend SHALL expose no usable decision controls and THE mock API SHALL reject forged mutations.
  - AC-006.4: WHEN a user views an imported Published ticket, THE frontend SHALL render MSG18 as an accessible explanation for the Request Changes restriction without constructing an impossible pending imported ticket or using an empty-Solution flag.
  - AC-006.5: WHEN a permitted ticket is displayed, THE frontend SHALL show Evidence filename and readable size with safe API-provided references; IF a reference uses an unsafe URL scheme, THEN THE frontend SHALL render no executable link.
  - AC-006.6: IF an ordinary reader requests an unpublished ticket they neither authored nor manage, THEN THE mock API SHALL return 404 MSG04 without its Solution or review comment; THE frontend SHALL show the queue navigation only to users permitted to open it.

### STORY-007: View Expired tickets and send them back to review (KAN-158)
- As a Project Manager, I want to inspect Expired tickets of my Projects and send selected tickets back to the Review Queue.
- Acceptance:
  - AC-007.1: WHEN Expired Tickets loads, THE frontend and mock API SHALL list only EXPIRED Solution Form tickets in the current Workspace's Projects managed by the current Project Manager, with Ticket ID, Title, Project, Source, Expired on and previous status; THE frontend SHALL link each ticket to the existing Ticket Detail.
  - AC-007.2: WHEN the user changes Department, page or supported sort, THE frontend SHALL preserve list state in the URL and request server-side results; THE frontend SHALL use the existing Department filter convention without adding a Project filter.
  - AC-007.3: WHEN the user selects across pages or selects all filtered tickets, THE frontend SHALL use the complete eligible UUID set and preserve off-page selections; WHEN the Department changes including browser navigation, THE frontend SHALL clear selection.
  - AC-007.4: WHEN one to 500 unique eligible tickets are sent back, THE mock API SHALL atomically change them to PENDING_REVIEW, set their queue-entry time to the send-back time, recalculate each due date from the send-back calendar date plus its Project's current reviewRangeDays (GBR-10), preserve Solution and Evidence, and append a SEND_BACK record per ticket; THE frontend SHALL show MSG25 with the returned count and remove completed selections.
  - AC-007.5: IF any submitted UUID is invalid, duplicated, missing, foreign, unmanaged, no longer EXPIRED, or the batch is empty or exceeds 500, THEN THE mock API SHALL reject the entire batch without changing any ticket or history; THE frontend SHALL disable empty, oversized and pending submissions and prevent duplicate requests.
  - AC-007.6: IF a list or eligibility request fails, THEN THE frontend SHALL show the catalog error and retry; WHILE initial loading is pending, THE frontend SHALL show loading; IF no Expired tickets match, THEN THE frontend SHALL show MSG04.
  - AC-007.7: WHEN send-back settles, THE frontend SHALL refresh affected Expired list/eligibility, Review Queue/eligibility, Ticket Detail and Ticket List queries; IF send-back fails, THEN THE frontend SHALL retain eligible selections and show its catalog error, reconciling stale selections after 404 or 409 without reporting a completed mutation as failed because a later refetch failed.
  - AC-007.8: IF the caller is signed out or lacks Project Manager, THEN THE route guard and mock API SHALL deny access; IF a caller forges another Project or Workspace's UUID, THEN THE mock API SHALL expose no foreign ticket data and perform no write.

### STORY-008: View Review History (KAN-159)
- As a Project Manager, I want to inspect past decisions on tickets of my managed Projects and open their details.
- Acceptance:
  - AC-008.1: WHEN Review History loads for a Project Manager, THE frontend and mock API SHALL list decision records only for the current Workspace's managed Projects, newest first, showing Date, Ticket ID, Title, Project, Decision, Project Manager, Author and Reason / comment, with links to the existing Ticket Detail.
  - AC-008.2: WHEN Department, decision, date range, page or supported sort changes, THE frontend SHALL preserve filters and pagination in the URL and request server-side results, resetting page when a filter changes; THE frontend SHALL retain the existing Department convention without a Project filter.
  - AC-008.3: WHEN both date endpoints are supplied, THE frontend and mock API SHALL filter inclusively by calendar date in Vietnam time; IF only one endpoint is supplied, a date is invalid or the start exceeds the end, THEN THE frontend SHALL show MSG39 without issuing the history query and THE mock API SHALL reject the invalid request without changing stored data.
  - AC-008.4: WHEN the history contains APPROVE, REJECT, REQUEST_CHANGES, UNPUBLISH, REPUBLISH or SEND_BACK, THE frontend SHALL display its decision label and applicable reason/comment as text, preserving distinct records for repeated decisions on the same ticket; THE frontend SHALL offer no edit, delete or decision mutation controls on this screen.
  - AC-008.5: WHEN an existing approve, reject, request-changes or send-back mock mutation succeeds, THE mock API SHALL append a record with a unique decision UUID, actor identity/name, timestamp, ticket and existing decision content, and THE frontend SHALL invalidate Review History queries; IF that mutation fails, THEN THE mock API SHALL append no history record.
  - AC-008.6: WHILE initial history loading is pending, THE frontend SHALL show loading; IF the query fails, THEN THE frontend SHALL show its catalog error and retry; IF no records match, THEN THE frontend SHALL show MSG04.
  - AC-008.7: IF the caller is signed out or lacks Project Manager, THEN THE route guard and mock API SHALL deny history access; IF the caller supplies a foreign or unmanaged Department filter, THEN THE mock API SHALL expose no foreign decision or ticket data.

## Out of Scope

- Production backend endpoints, identity/Project authorization, canonical Ticket/KAN-137 schema changes, migrations and durable audit/history. KAN-24 backend remains undelivered by this frontend slice.
- Actual Jira comment delivery (including MSG64 visibility), email, notification workers and real-time updates beyond the existing polling convention. Store the mock comment and proposed contract; do not claim successful external delivery.
- Production send-back processing (KAN-156), JOB-02 (KAN-157) and SSE/WebSocket (KAN-154). KAN-158 and KAN-159 deliver frontend, proposed APIs and in-memory fixtures; they do not implement a real expiry job or backend persistence. KAN-159 displays historical Unpublish/Republish records without adding those mutation workflows.
- Importing PR #26 or changing/closing it; creating or pushing the replacement PR still requires the user to request that action.

## Interface Contracts

### Exposed by this module

- Preserve existing `/api/v1/review-queue`, `/api/v1/review-queue/ticket-ids` and `/api/v1/reviews/{approve,reject,request-changes}` proposal routes. Queue/ids use optional UUID `departmentId`; page/sort conventions stay shared. Every internal ticket/Project id becomes a UUID string, while displayed `sourceTicketId` remains the Jira/source key.
- Decision requests: unique `ticketIds` (1–500 UUIDs); Reject adds `reason`, Request Changes adds `comment` (trim-required, at most 1,000 characters). Success returns existing `{ count }`. Use current catalog Problem errors: validation 400, unauthenticated 401, unauthorized 403, missing 404 and stale 409, preserving field errors.
- Add a proposed generated Ticket Detail query at `/api/v1/tickets/{ticketId}`, reusing existing Ticket source/status/Project/Solution definitions where compatible. Details carry trusted author and manager identity/scope information from the mock/API contract; never infer authorship from display-name text. Do not claim the backend implements the proposal.
- MSW fixtures hold ticket states and append-only decision records in memory, reset through the existing mock reset entry point. Invalid batches leave every ticket and history entry unchanged. No production table or second Ticket authority is created.

### Required from other modules

KAN-159 proposes GET `/api/v1/review-history`, with optional UUID `departmentId`, optional `decision` enum (APPROVE, REJECT, REQUEST_CHANGES, UNPUBLISH, REPUBLISH, SEND_BACK), optional paired `dateFrom`/`dateTo` ISO calendar dates, and existing page/size/sort conventions. Default sort is timestamp descending with decision UUID as deterministic tie-breaker; supported explicit sorts are timestamp ascending/descending. Response is a generated paginated ReviewHistoryItem collection with decision UUID, ticket UUID/source key/title, Project UUID/name, decision, actor UUID/display name, nullable author name, timestamp, nullable reason and comment. Date boundaries use UTC+07:00, end-exclusive next midnight internally. Validation/authentication/authorization errors follow the existing review Problem contract. Records retain decision actor/time/action/content independently of subsequent ticket status changes; current ticket metadata supplies display/navigation and scope. An empty mock history remains valid until decisions occur; tests may seed all six actions. This contract is a proposal, not an existing production endpoint.

KAN-158 proposes GET `/api/v1/expired-tickets` (optional UUID departmentId, existing page/size/sort conventions), GET `/api/v1/expired-tickets/ticket-ids` (same filter, complete eligible UUID set), and POST `/api/v1/reviews/send-back` (`ticketIds`, 1–500 unique UUIDs, response `{count}`). Add generated ExpiredTicketItem/ExpiredTicketPage schemas carrying expiry and previous status; reuse existing Ticket and decision shapes. Error codes/statuses follow the existing review proposal. Mock expiry fixtures include the Project's current reviewRangeDays; calendar due dates follow the project's existing Vietnam-time convention. SEND_BACK records are session-only and may be consumed by the later KAN-159 slice. The Ticket Detail implementation is inherited from the explicitly approved KAN-24 branch base; this task does not duplicate that feature.

Use existing session roles and UUID Project memberships, catalog messages, shared form/table/error components, feature barrels and TanStack Query conventions from fe-foundation. Preserve all unrelated endpoints when regenerating the client. Future canonical backend integration must supply authoritative actor/Workspace/Project scope, persistence/audit and Jira delivery; this slice explicitly does not implement those seams.

## Definition of Done

- [ ] Current spec approved; every criterion has meaningful named tests under review feature paths.
- [ ] All frontend verification commands pass, generation is deterministic, no test timeout/error suppression or skipped regression hides failures.
- [ ] Automated checks cover queue, Ticket Detail, confirmation, validation, stale/failure/retry and permissions; reports disclose mock-only guarantees and backend dependencies. Manual browser testing is omitted per the user's instruction.
- [ ] Review and test evidence recorded; user approves each feature commit; push/PR only when requested.
