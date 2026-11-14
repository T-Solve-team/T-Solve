---
module: ticket-review
date: 2026-11-01
owner: BA
---

# Spec: ticket-review

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-14 | STORY-010 | Merge of KAN-333 and KAN-162: the branded HTML email of AC-010.2 and AC-010.7–010.9 is composed when the decision commits and queued in the worker email outbox, instead of being rendered by a job at send time (KAN-162) | PR #230 merged into main while KAN-162 was open; both changes were approved separately on 2026-11-14 |
| 2026-11-14 | STORY-010 | The review email is composed when the decision commits and queued in the worker email outbox; the per-decision review-notification job, which loaded the decision, ticket and writer at send time, is removed (KAN-162) | Worker STORY-004 adds a shared outbox so the sender never reads the business database; user chose that every email goes through it |
| 2026-11-14 | STORY-010 | The email falls back to the reviewer's email for a blank name and drops "Reviewed by" when the reviewer's account is gone (AC-010.7 reworded) (KAN-333) | Review finding on PR #230: a blank name or a deleted reviewer rendered "Reviewed by  ·" |
| 2026-11-14 | STORY-010 | The review email becomes a branded English HTML email with a plain-text part: ticket card (key, Project, title, reviewer, decision time), reason or comment, a button to Ticket Detail on the Workspace site, sender name T-Solve (AC-010.2 reworded, AC-010.7–010.9 added) | User asked for a professional-looking email once real Resend delivery worked; user chose English, a Ticket Detail button and the text "T" mark over a logo image |
| 2026-11-10 | STORY-013 | The Expired list reports and sorts by the expiry time and previous status that JOB-02 stores, keeping the derived values only for tickets without them (KAN-307) | KAN-156 shipped derived values until JOB-02 stored the real ones (KAN-157, changeset 025); user chose to keep the derived values for EXPIRED rows whose columns are null instead of a backfill changeset |
| 2026-11-08 | STORY-013 | Serve Expired tickets and send them back to the Review Queue from stored data, storing one SEND_BACK review decision per ticket and returning it in Review History (KAN-156) | KAN-158 served the Expired page from MSW only; user chose review_decision for SEND_BACK, SEND_BACK in History, derived expiry fields and project.review_range with a 3-day default |
| 2026-11-08 | STORY-014 | JOB-02 sets overdue Pending review and Changes requested tickets to Expired at 00:00 Vietnam time in every Active Workspace, through a JobRunr recurring job that fans out one tenant job per Workspace (KAN-157) | UC-33 and GBR-10; user chose the JobRunr fan-out, ticket columns plus a per-Project log line (no decision or audit row), and a 15-minute retry up to 3 attempts. STORY-012 and STORY-013 are taken by KAN-154 and KAN-156 |
| 2026-11-07 | STORY-012 | Push Review Queue changes to open Review Queue screens over SSE, signalled by a tenant database NOTIFY (KAN-154) | Jira AC asks for SSE or WebSocket; user chose PostgreSQL NOTIFY so submissions from any writer are caught |
| 2026-11-07 | STORY-011 | Serve Review History from the stored review decisions of the production Workspace (KAN-159) | Demo flow scenario 2 step 10; the merged frontend only had mocks |
| 2026-11-07 | STORY-010 | Email the writer after a committed Reject or Request Changes decision through a tenant JobRunr job (KAN-155) | Demo flow scenario 2 step 7; user chose job delivery so SMTP failure never affects the decision |
| 2026-11-07 | STORY-009 | Persist the time a ticket entered the Review Queue | Preserve the actual entry time independently of later ticket edits |
| 2026-11-06 | STORY-009 | Define the production Review backend for KAN-24 using the Workspace schema reference and UUID keys | Complete the Jira story beyond its merged frontend and mocks |
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
4. Treat the OpenAPI changes in STORY-001 through STORY-008 as proposals. STORY-009 covers production integration for the KAN-24 operations.
5. Implement the production queue, eligible ids, Ticket Detail and three review decisions under STORY-009. Use the Department filter agreed for the merged queue.

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

### STORY-009: Review submitted Solutions in the production Workspace (KAN-24)

- As a Project Manager, I want to review Solutions in my own Projects so that decisions and feedback are available after the request ends.
- Acceptance:
  - AC-009.1: WHEN a Project Manager queries the Review Queue or its eligible ids, THE API SHALL use the signed-in Workspace and return only pending Solution Form tickets for Projects they manage, with the same Department filter on both endpoints and earliest review due date first by default.
  - AC-009.2: WHEN a permitted user opens Ticket Detail, THE API SHALL return persisted ticket, Solution and Evidence data and the information the frontend needs to display only the decisions the current user may make.
  - AC-009.3: IF a caller is unauthenticated or does not manage the target ticket's Project, THEN THE decision API SHALL refuse the request with 401 or 403 respectively and leave the ticket unchanged. IF a ticket id belongs to a different Workspace, THEN THE API SHALL expose no ticket data and perform no write.
  - AC-009.4: WHEN an authorized Project Manager approves eligible tickets, THE API SHALL publish all selected tickets and remove them from the Review Queue, including when the manager authored an eligible ticket.
  - AC-009.5: WHEN an authorized Project Manager rejects eligible tickets with one nonblank reason of at most 1,000 characters, THE API SHALL move all selected tickets to the final Rejected state and store that reason for each decision.
  - AC-009.6: WHEN an authorized Project Manager requests changes to eligible Solution Form tickets with one nonblank comment of at most 1,000 characters, THE API SHALL move all selected tickets to Changes requested and store the same comment for each decision. WHEN the writer opens the associated Jira issue in the T-Solve panel, THE system SHALL show the manager and the comment in MSG64.
  - AC-009.7: IF any decision input is invalid or any selected ticket is missing, outside the caller's scope or no longer eligible, THEN THE API SHALL refuse the entire batch without changing any ticket or recording a decision.
  - AC-009.8: WHEN a decision succeeds, THE backend SHALL commit the ticket state and one durable review decision per ticket in the same Workspace transaction, preserving Solution, Evidence and review due date. Each decision SHALL retain the reviewer, action, time and applicable reason or comment.
  - AC-009.9: THE production backend SHALL use database/02_tenant.sql as the reference for Review table structure and relationships. THE tenant changelog SHALL use UUID keys under the backend conventions for the shared ticket, evidence and review_decision tables. The Solution fields SHALL remain on ticket.
  - AC-009.10: WHEN the production web application loads or submits a Review action, THE frontend SHALL use the published OpenAPI operations and backend data through the existing generated client.
  - AC-009.11: WHEN a Solution Form ticket is first saved as Pending review or transitions back to Pending review, THE tenant database SHALL record that entry time in review_queue_entered_at. WHILE a ticket stays Pending review, THE system SHALL preserve that entry time and SHALL keep the existing review due date rules (GBR-08, GBR-10).
  - AC-009.12: WHEN the Review Queue returns a ticket, THE API SHALL expose the recorded entry time as enteredQueueAt. IF an older ticket has no recorded entry time, THEN THE API SHALL return null for enteredQueueAt and THE frontend SHALL show an empty value indicator.

### STORY-010: Email the writer about Reject and Request Changes (KAN-155)

- As a ticket writer, I want an email with the review decision and its reason or comment so that I learn about it without opening T-Solve.
- Acceptance:
  - AC-010.1: WHEN a Reject or Request Changes decision commits, THE backend SHALL, after the commit and in the decision's Workspace, queue one email per decided ticket in the worker email outbox (worker STORY-004). WHEN an Approve decision commits, THE backend SHALL queue no email.
  - AC-010.2: WHEN the backend queues a review email, THE backend SHALL load the committed review decision, its ticket, its Project, the reviewer's account and the writer's account in that Workspace and compose one English email addressed to the writer's account email, with an HTML body (AC-010.7) and a plain-text body holding the same facts, with the subject "[<source ticket key>] Your ticket was rejected" for Reject and "[<source ticket key>] Changes requested for your ticket" for Request Changes. THE worker SHALL send it from the display name "T-Solve" (worker AC-001.1).
  - AC-010.3: IF queueing a review email fails or job queueing is disabled in the API process, THEN THE decision SHALL stay committed and THE API SHALL return the same success response without sending an email.
  - AC-010.4: IF SMTP delivery of a queued review email fails, THEN THE worker email outbox SHALL retry it under worker AC-004.7, and THE committed decision SHALL be unaffected.
  - AC-010.5: IF a decided ticket has no writer account, THEN THE backend SHALL queue no email for that ticket and SHALL still queue the emails of the other decided tickets.
  - AC-010.6: THE review email queueing SHALL not write the recipient address, reason or comment to application logs.
  - AC-010.7: THE HTML part SHALL show, in this order: a header with a "T" mark and the name T-Solve in the brand colour #5577FF; a status badge ("Rejected" in red or "Changes requested" in amber); a heading and a greeting with the writer's full name ("Hi there" when no name is stored); a ticket card with the source ticket key, the Project name, the ticket title, the reviewer's full name (the reviewer's email when the stored name is missing or blank; no "Reviewed by" text when the reviewer's account no longer exists) and the decision time in Vietnam time (UTC+7) as "d MMM yyyy, HH:mm (GMT+7)"; the stored reason under "Rejection reason" or the stored comment under "Review comment"; a button "Open ticket" (Reject) or "Update ticket" (Request Changes); and a footer naming the Workspace and saying the message is automated. It SHALL use table layout with inline styles and no images or external resources.
  - AC-010.8: THE button SHALL link to the ticket's Ticket Detail page on the Workspace's own site, https://<Workspace slug>.<TSOLVE_DOMAIN>/tickets/<ticket id>, and the plain-text part SHALL carry the same link. IF the site address cannot be built (no domain configured or no Workspace slug), THEN THE backend SHALL compose the email without the button and the link.
  - AC-010.9: THE backend SHALL HTML-escape every stored value placed in the HTML part (names, ticket title, Project and Workspace names, reason, comment), keep the line breaks of the reason or comment, and SHALL replace any line break in the subject with a space.

### STORY-011: Read Review History in the production Workspace (KAN-159)

- As a Project Manager, I want Review History to show the decisions stored for my Projects so that I can check past reviews after the request ends.
- Acceptance:
  - AC-011.1: WHEN a Project Manager queries Review History, THE API SHALL use the signed-in Workspace and return only review decisions on tickets of Projects they manage, newest first by default with the decision UUID as tie-breaker, paged with the existing page and size conventions.
  - AC-011.2: WHEN a stored decision is returned, THE API SHALL report Approve, Reject and Request Changes as APPROVE, REJECT and REQUEST_CHANGES, the stored text as reason only for REJECT and as comment only for REQUEST_CHANGES, the reviewer's full name as actorName (the reviewer's email when no name is stored), the ticket writer as authorName, and the ticket's source key, title and Project.
  - AC-011.3: WHEN Department, decision or a paired dateFrom and dateTo are supplied, THE API SHALL filter by them, with both calendar dates inclusive in Vietnam time. IF only one date is supplied, a date is not a yyyy-MM-dd calendar date or dateFrom is after dateTo, THEN THE API SHALL refuse the request with 400 and MSG39. IF the Department has no Project the caller manages, THEN THE API SHALL return an empty page.
  - AC-011.4: IF the sort is anything other than timestamp ascending or descending, or page or size is outside the existing limits, THEN THE API SHALL refuse the request with 400 and MSG06.
  - AC-011.5: IF the caller is unauthenticated, lacks Project Manager or has no verified Workspace Account, THEN THE API SHALL refuse the request with 401 or 403 and expose no decision.
  - AC-011.6: WHEN the production web application loads Review History, THE frontend SHALL use the published getReviewHistory operation through the existing generated client.

### STORY-012: See the Review Queue change in real time (KAN-154)

- As a Project Manager, I want the Review Queue to update itself when a ticket is submitted or decided by someone else so that I see waiting tickets without reloading.
- Acceptance:
  - AC-012.1: WHEN a Project Manager with a verified Workspace Account opens GET `/api/v1/review-queue/events`, THE API SHALL keep a `text/event-stream` response open for the signed-in Workspace and SHALL send a heartbeat comment at least every 30 seconds while it stays open.
  - AC-012.2: WHEN a Workspace transaction commits that moves a Solution Form ticket into Pending review, or moves a ticket out of Pending review, THE tenant database SHALL send a NOTIFY carrying only that ticket's Project UUID, and THE API SHALL send one `queue-changed` event with that Project UUID to every open stream of that Workspace whose caller manages that Project, within the existing 5-second Review Queue NFR (FE-10).
  - AC-012.3: THE stream SHALL send no event for a Project the caller does not manage and SHALL carry no ticket content, Solution text, reason or comment. A transaction that changes several tickets of one Project SHALL produce at most one event for that Project per stream.
  - AC-012.4: IF the caller is unauthenticated, lacks Project Manager or has no verified Workspace Account, THEN THE API SHALL refuse the stream with 401 or 403 and open no stream.
  - AC-012.5: WHEN the Review Queue screen receives a `queue-changed` event, THE frontend SHALL refetch the queue page and eligible ticket ids it currently shows, keeping the existing selection and stale-data rules (STORY-005).
  - AC-012.6: WHILE the event stream is open, THE frontend SHALL not run the 5-second queue polling. WHILE the stream is closed or failing, THE frontend SHALL keep the existing visible-tab 5-second polling, and WHEN the stream opens again it SHALL refetch the queue once.
  - AC-012.7: WHEN the user leaves the Review Queue screen, THE frontend SHALL close its event stream.

### STORY-013: Send Expired tickets back to the Review Queue in the production Workspace (KAN-156)

- As a Project Manager, I want the Expired Tickets screen to show the stored Expired tickets of my Projects and to send selected ones back to the Review Queue so that overdue Solutions get reviewed instead of lost.
- Acceptance:
  - AC-013.1: WHEN a Project Manager queries GET `/api/v1/expired-tickets`, THE API SHALL use the signed-in Workspace and the Projects they manage through the Review Workspace access and return only EXPIRED Solution Form tickets of those Projects, each with ticket UUID, source key, title, Project, source, writer name, enteredQueueAt, reviewDueDate, expiredAt and previousStatus, paged with the existing page and size conventions, latest expiredAt first by default with the ticket UUID as tie-breaker.
  - AC-013.2: WHEN a Project Manager queries GET `/api/v1/expired-tickets/ticket-ids`, THE API SHALL return the UUIDs of every ticket the Expired list returns for the same Department filter across all pages. WHEN departmentId is supplied, both endpoints SHALL return only tickets of that Department's Projects. IF that Department has no Project the caller manages, THEN THE API SHALL return an empty page and an empty id list.
  - AC-013.3: WHEN the API returns an Expired ticket, THE API SHALL report expiredAt from the ticket's stored expired_at and previousStatus from its stored status_before_expiry. IF expired_at is null, THEN THE API SHALL report expiredAt as 00:00 Vietnam time (UTC+07:00) on the calendar day after the ticket's review due date, or as the ticket's last update time when the due date is not set; IF status_before_expiry is null, THEN THE API SHALL report previousStatus as PENDING_REVIEW. THE API SHALL store neither fallback value.
  - AC-013.4: WHEN the Expired list is sorted by expiredAt, sourceTicketId, title or project, ascending or descending, THE API SHALL apply that order, ordering expiredAt by the value AC-013.3 reports. IF the sort names any other field or direction, or page or size is outside the existing limits, THEN THE API SHALL refuse the request with 400 and MSG06.
  - AC-013.5: WHEN a Project Manager sends back 1 to 500 unique UUIDs through POST `/api/v1/reviews/send-back` and every ticket is an EXPIRED Solution Form ticket of a Project they manage, THE backend SHALL in one Workspace transaction change every ticket to PENDING_REVIEW, set its review_queue_entered_at to the send-back time, set its review due date to the send-back calendar date in Vietnam time plus its Project's review_range days (3 days when review_range is null or not positive) (GBR-10), keep its Solution and Evidence unchanged, and record one SEND_BACK review decision per ticket with the caller, the send-back time and no reason or comment; THE API SHALL return the number of tickets sent back as count and SHALL enqueue no review notification job.
  - AC-013.6: IF ticketIds is empty, holds more than 500 entries, holds a duplicate or holds a value that is not a UUID, THEN THE API SHALL refuse the batch with 400. IF any ticket does not exist in the signed-in Workspace, THEN THE API SHALL refuse the batch with 404 and expose no ticket data. IF any ticket belongs to a Project the caller does not manage, THEN THE API SHALL refuse the batch with 403. IF any ticket is no longer EXPIRED or is not a Solution Form ticket, THEN THE API SHALL refuse the batch with 409. In every refused case THE backend SHALL change no ticket and record no review decision.
  - AC-013.7: IF the caller is unauthenticated, lacks Project Manager or has no verified Workspace Account, THEN THE API SHALL refuse the Expired list, the Expired ticket ids and send-back with 401 or 403 and expose no ticket.
  - AC-013.8: WHEN Review History returns a stored SEND_BACK decision, THE API SHALL report it as SEND_BACK with null reason and null comment, with the same actor, author, ticket and Project fields as AC-011.2; WHEN the decision filter is SEND_BACK, THE API SHALL return those decisions.
  - AC-013.9: WHEN the production web application loads Expired Tickets or sends tickets back, THE frontend SHALL use the published getExpiredTickets, getExpiredTicketIds and sendBackTickets operations through the existing generated client, and the OpenAPI contract SHALL describe them as production operations.

### STORY-014: Expire overdue tickets with JOB-02 (KAN-157)

- As a Project Manager, I want tickets that waited past their review due date to leave the queue automatically so that no ticket waits for a decision forever (UC-33, GBR-10).
- Acceptance:
  - AC-014.1: WHEN a backend process starts with the worker integration enabled, THE backend SHALL register the JOB-02 recurring job under one fixed id with the schedule every day at 00:00 in the Asia/Ho_Chi_Minh time zone (UTC+7), so that several processes keep a single registration.
  - AC-014.2: WHEN JOB-02 runs, THE worker SHALL enqueue one expiry job, with attempt number 1, for each Workspace the worker tenant port lists as Active at that moment, and none for a Suspended or unknown Workspace (GBR-38).
  - AC-014.3: WHEN an expiry job runs for an Active Workspace, THE worker SHALL, inside runInTenant for that Workspace and in one transaction, set to EXPIRED every ticket in PENDING_REVIEW or CHANGES_REQUESTED whose review_due_date is before the current calendar date in Vietnam time and whose Project's Department is not deleted, and SHALL store for each ticket the expiry time in expired_at and its previous status in status_before_expiry.
  - AC-014.4: THE expiry job SHALL leave unchanged every ticket whose review_due_date is today or later or not set, every ticket in another status, every ticket of a deleted Department, and the Solution, Evidence, review_due_date and review decisions of the tickets it expires. WHEN the expiry job runs again on the same calendar date, THE worker SHALL change no ticket.
  - AC-014.5: WHEN the expiry transaction commits, THE expired tickets SHALL no longer appear in the Review Queue or its eligible ids, and THE worker SHALL log one line per Project with expired tickets, carrying only the Workspace UUID, the Project UUID and the count. IF no ticket is overdue, THEN THE worker SHALL end without changing any ticket.
  - AC-014.6: IF the expiry transaction fails, THEN THE worker SHALL roll back every ticket change of that attempt, log the failure without ticket content, and schedule another expiry job for that Workspace 15 minutes later with the next attempt number. IF attempt 3 fails, THEN THE worker SHALL schedule no further attempt and the overdue tickets SHALL wait for the next 00:00 run.
  - AC-014.7: IF the Workspace is Suspended or unknown when an expiry job runs, THEN THE worker SHALL change no ticket and schedule no further attempt (GBR-38).
  - AC-014.8: THE expiry job payload SHALL hold only the Workspace UUID and the attempt number, and JOB-02 SHALL record no review decision and no audit entry. THE tenant changelog SHALL add nullable ticket.expired_at as TIMESTAMPTZ and nullable ticket.status_before_expiry as VARCHAR(20) limited to PENDING_REVIEW and CHANGES_REQUESTED.

## Out of Scope

- General audit writer, notification channels other than STORY-010 email, and real-time updates of screens other than the Review Queue (STORY-012). STORY-009 stores Review decisions and exposes Request Changes feedback to the writer in Jira. The KAN-162 retry limit of three attempts in one hour, a logo image, a Vietnamese or per-user language email, and email for decisions other than Reject and Request Changes are not part of STORY-010.
- A WebSocket channel (STORY-012 uses SSE only). STORY-013 (KAN-156) serves and sends back tickets that are already EXPIRED; it does not move tickets to EXPIRED (STORY-014, KAN-157, does), rename `project.review_range`, or offer send-back on Ticket Detail. STORY-014 does not serve the Expired list, notify the writer or a Project Manager of an expiry, register JOB-02 per Workspace on provisioning (KAN-161), or retry beyond 3 attempts per night. KAN-158 delivers frontend, a proposed API and in-memory fixtures. KAN-159 displays historical Unpublish/Republish records without adding those mutation workflows; STORY-011 and STORY-013 serve the stored Approve, Reject, Request Changes and Send back decisions, so the UNPUBLISH and REPUBLISH filters return an empty page.
- Importing PR #26 or changing/closing it; creating or pushing the replacement PR still requires the user to request that action.

## Interface Contracts

### Exposed by this module

- Preserve existing `/api/v1/review-queue`, `/api/v1/review-queue/ticket-ids` and `/api/v1/reviews/{approve,reject,request-changes}` proposal routes. Queue/ids use optional UUID `departmentId`; page/sort conventions stay shared. Every internal ticket/Project id becomes a UUID string, while displayed `sourceTicketId` remains the Jira/source key.
- STORY-012 adds GET `/api/v1/review-queue/events` (`text/event-stream`, Project Manager): events named `queue-changed` with data `{"projectId":"<uuid>"}` and heartbeat comments; the same 401/403 Problem errors as the queue. The tenant changelog adds a trigger that NOTIFYs channel `review_queue` with the Project UUID on entry to or exit from Pending review.
- STORY-013 makes the KAN-158 GET `/api/v1/expired-tickets`, GET `/api/v1/expired-tickets/ticket-ids` and POST `/api/v1/reviews/send-back` proposals production operations with unchanged shapes (ExpiredTicketPage, TicketIdList, `{ticketIds}` → `{count}`). A new tenant changeset widens `ck_review_decision` to also allow `SEND_BACK`; no other column is added.
- STORY-014 (JOB-02) adds no REST route. The tenant changelog adds `ticket.expired_at` (TIMESTAMPTZ) and `ticket.status_before_expiry` (VARCHAR(20), PENDING_REVIEW or CHANGES_REQUESTED); they keep the real expiry time and previous status. The production Expired list of STORY-013 reads them (KAN-307) and derives a value only where a column is null. The status change to EXPIRED reaches open Review Queue streams through the existing STORY-012 trigger.
- Review Queue returns `enteredQueueAt` as a date-time when recorded and `null` for older rows with an unknown entry time. The tenant changelog adds nullable `ticket.review_queue_entered_at` as `TIMESTAMPTZ`.
- Decision requests: unique `ticketIds` (1–500 UUIDs); Reject adds `reason`, Request Changes adds `comment` (trim-required, at most 1,000 characters). Success returns existing `{ count }`. Use current catalog Problem errors: validation 400, unauthenticated 401, unauthorized 403, missing 404 and stale 409, preserving field errors.
- Use the generated Ticket Detail query at `/api/v1/tickets/{ticketId}`, reusing existing Ticket source/status/Project/Solution definitions where compatible. Details carry trusted author and manager identity/scope information. The frontend uses account identifiers to determine authorship.
- MSW fixtures hold ticket states and append-only decision records in memory, reset through the existing mock reset entry point. Invalid batches leave every ticket and history entry unchanged. No production table or second Ticket authority is created.

### Required from other modules

STORY-009 requires a trusted Workspace binding and the authenticated Account's Project membership for Review. Jira panel feedback requires a verified Jira issue and writer identity in that same Workspace. Workspace routing, sign-in and general Project membership administration belong to their respective modules. STORY-014 requires the worker tenant port to list the Active Workspaces (worker STORY-003) and uses worker's public `TenantExecutionService` and JobRunr scheduler.

KAN-159 proposes GET `/api/v1/review-history`, with optional UUID `departmentId`, optional `decision` enum (APPROVE, REJECT, REQUEST_CHANGES, UNPUBLISH, REPUBLISH, SEND_BACK), optional paired `dateFrom`/`dateTo` ISO calendar dates, and existing page/size/sort conventions. Default sort is timestamp descending with decision UUID as deterministic tie-breaker; supported explicit sorts are timestamp ascending/descending. Response is a generated paginated ReviewHistoryItem collection with decision UUID, ticket UUID/source key/title, Project UUID/name, decision, actor UUID/display name, nullable author name, timestamp, nullable reason and comment. Date boundaries use UTC+07:00, end-exclusive next midnight internally. Validation/authentication/authorization errors follow the existing review Problem contract. Records retain decision actor/time/action/content independently of subsequent ticket status changes; current ticket metadata supplies display/navigation and scope. An empty mock history remains valid until decisions occur; tests may seed all six actions. This contract is a proposal, not an existing production endpoint.

KAN-158 proposes GET `/api/v1/expired-tickets` (optional UUID departmentId, existing page/size/sort conventions), GET `/api/v1/expired-tickets/ticket-ids` (same filter, complete eligible UUID set), and POST `/api/v1/reviews/send-back` (`ticketIds`, 1–500 unique UUIDs, response `{count}`). Add generated ExpiredTicketItem/ExpiredTicketPage schemas carrying expiry and previous status; reuse existing Ticket and decision shapes. Error codes/statuses follow the existing review proposal. Mock expiry fixtures include the Project's current reviewRangeDays; calendar due dates follow the project's existing Vietnam-time convention. SEND_BACK records are session-only and may be consumed by the later KAN-159 slice. The Ticket Detail implementation is inherited from the explicitly approved KAN-24 branch base; this task does not duplicate that feature.

Use existing session roles and UUID Project memberships, catalog messages, shared form/table/error components, feature barrels and TanStack Query conventions from fe-foundation. Preserve all unrelated endpoints when regenerating the client.

## Definition of Done

- [ ] Current spec approved; every criterion has meaningful named tests under review feature paths.
- [ ] All frontend verification commands pass, generation is deterministic, no test timeout/error suppression or skipped regression hides failures.
- [ ] Automated checks cover queue, Ticket Detail, confirmation, validation, stale/failure/retry and permissions; reports disclose mock-only guarantees and backend dependencies. Manual browser testing is omitted per the user's instruction.
- [ ] STORY-009 passes backend verification, including PostgreSQL tests for scope, atomic decisions and persistence. Verify queue and detail through the real API and verify MSG64 for the writer in Jira.
- [ ] STORY-013 passes backend verification, including PostgreSQL tests for scope, the atomic send-back batch, the recalculated due date and the SEND_BACK decision.
- [ ] Review and test evidence recorded; user approves each feature commit; push/PR only when requested.
