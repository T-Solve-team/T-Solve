---
module: worker
date: 2026-11-06
owner: BA
---

# Spec: worker

## Changelog

| Date | Story | Change | Reason |
|---|---|---|---|
| 2026-11-06 | STORY-001 | Define the SMTP delivery slice of KAN-162 | Verify review email delivery before the job queue is available |

## Requirements

### Functional

1. Send one plain-text review notification to the ticket writer for a Reject or Request Changes decision.
2. Report delivery failure to the caller so the future worker job can handle it.

### Non-Functional

1. Attempt one SMTP send per call. Bound connection, read and write waits with finite SMTP timeouts.
2. Keep recipient addresses, message contents and SMTP credentials out of logs.
3. Verify delivery against a local SMTP test server using synthetic recipients.

## User Stories

### STORY-001: Send a review decision notification through SMTP (KAN-162)

- As a ticket writer, I want the review decision and its reason or comment by email so that I know how to respond.
- Acceptance:
  - AC-001.1: WHEN the caller submits a Reject notification, THE SMTP sender SHALL send a plain-text email to the supplied writer address with a rejection subject, the ticket key and the rejection reason.
  - AC-001.2: WHEN the caller submits a Request Changes notification, THE SMTP sender SHALL send a plain-text email to the supplied writer address with a request-changes subject, the ticket key and the review comment.
  - AC-001.3: IF the supplied recipient does not represent exactly one mailbox, THEN THE SMTP sender SHALL reject the request before attempting delivery.
  - AC-001.4: IF the SMTP server refuses delivery or the send times out, THEN THE SMTP sender SHALL report a delivery failure to the caller after one attempt, with no recipient address, message content or credentials in the reported error.
  - AC-001.5: THE SMTP sender SHALL configure finite SMTP connection, read and write timeouts.
  - AC-001.6: THE SMTP sender SHALL omit recipient addresses, message contents and SMTP credentials from its logs.
  - AC-001.7: WHILE SMTP settings are absent, THE web application SHALL start without an SMTP connection.

## Out of Scope

- KAN-161 and KAN-102 own queue execution and Workspace context. A later KAN-162 change covers retry policy and job delivery, including the limit of three retries in one hour.
- KAN-155 owns the trigger after a committed review decision. Its integration verifies that email failure cannot roll back the decision.
- The job integration owns recipient lookup in the current Workspace. This slice uses only synthetic recipients in local tests.
- First Admin invitation, production SMTP provider setup and HTML email are outside this slice.

## Interface Contracts

### Exposed by this module

An internal Java delivery operation accepts a decision kind, one writer address, a ticket key, and the associated reason or comment. It reports success when the SMTP server accepts the message and reports delivery failure to the caller when the send fails.

### Required from other modules

The future caller supplies the committed review decision and resolves the writer address within the decision's Workspace. Local tests provide synthetic decision data and a local SMTP endpoint.

## Definition of Done

- [ ] Every acceptance criterion has a test whose name cites its AC id.
- [ ] Both notification kinds are verified with the local SMTP server.
- [ ] Backend verification and formatting checks pass.
