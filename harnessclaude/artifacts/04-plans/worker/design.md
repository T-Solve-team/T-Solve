---
module: worker
date: 2026-11-06
---

# Design: worker

Spec: [Worker SMTP delivery](../../03-modules/worker/spec.md)

## Architecture Overview

The review SMTP sender accepts the writer address, ticket key, decision kind and reason or comment. It validates a single mailbox, builds a UTF-8 plain-text message, and calls Spring Boot's `JavaMailSender` once. A mail failure becomes a delivery exception with a fixed, safe message. The sender runs outside a database transaction.

The sender is available when the SMTP host is configured. Startup does not connect to SMTP. Review and JobRunr will call this boundary in the later integration slice, after recipient lookup in the decision's Workspace.

## Data Model

The notification is an in-memory command. Its decision kind is either Reject or Request Changes. Its single recipient is already resolved by the caller. Ticket key and review feedback appear in the body, while the subject is fixed for each decision kind. There are no schema changes.

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| SMTP client | Spring Boot Mail and `JavaMailSender` | Jakarta Mail session configured directly | The project uses Spring Boot. The user selected its standard mail configuration. |
| Message format | Plain text with fixed subjects `Ticket rejected` and `Changes requested for your ticket` | HTML templates and dynamic headers | The two templates need only the ticket key and feedback. Fixed headers keep review text in the body. |
| Configuration | Spring's mail properties for SMTP host, port, credentials and TLS settings. `TSOLVE_SMTP_FROM` supplies the sender address. Use the documented Spring Boot timeout example as initial connect/read/write defaults of 5/3/5 seconds. | Dedicated SMTP settings wrapper | Standard properties preserve deployment control. Environment values supply addresses and credentials. |
| Failure boundary | Return an internal delivery exception with a fixed message. Keep the raw transport exception out of the propagated exception and logs. | Propagate the original mail exception | A transport diagnostic can contain recipient addresses and SMTP details. The caller can still distinguish a delivery failure from invalid input. |
| Local SMTP verification | Proposed Mailpit container through the existing Testcontainers setup, using synthetic addresses and the capture API | Embedded SMTP library or a custom SMTP server | Exercises Spring Mail over SMTP without adding a Java test library or sending external email. |

## File Structure

```text
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/vn/tsolve/worker/client/ReviewNotificationSender.java
    │   └── resources/application.yml
    └── test/java/vn/tsolve/worker/client/
        ├── ReviewNotificationSenderTest.java
        └── ReviewNotificationSenderIT.java
.env.example
```

## External Dependencies

- [Spring Boot Mail](https://docs.spring.io/spring-boot/reference/io/email.html) provides the SMTP client and configuration.
- [Mailpit](https://mailpit.axllent.org/docs/install/docker/) provides the proposed SMTP capture fixture for tests. Testcontainers manages its lifecycle.

## Edge Cases & Risks

Dimensions analyzed are input extremes, timing, environment, error cascades, authorization, data integrity, integration, compliance and business logic. User type, scale and persisted state transitions belong to the later job and review flows.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|---|---|---|---|---|---|
| 1 | STORY-001 | Input and integrity | Multiple addresses, an address group or a header line break | High | Reject before sending any mail | SMTP delivery |
| 2 | STORY-001 | Timing and integration | SMTP refusal or timeout | High | Make one attempt and report a safe delivery failure | SMTP delivery |
| 3 | STORY-001 | Environment | SMTP properties absent | High | Web application starts without SMTP | SMTP delivery |
| 4 | STORY-001 | Input and business logic | Unicode or multiline review feedback | Medium | Preserve feedback as UTF-8 body text under the correct fixed subject | SMTP delivery |
| 5 | STORY-001 | Compliance | Transport exception contains an address, content or credentials | High | Exclude those details from the returned exception and logs | SMTP delivery |
| 6 | STORY-001 | Authorization | Caller provides an address from a different Workspace | High | The future job resolves the recipient in its own Workspace before calling the sender | Later job integration |

## Risk Review — 2026-11-06 — Verdict: CAUTION

The sender is suitable for the local slice. Live delivery depends on the later job verifying the recipient's Workspace.

| Perspective | Finding | Mitigation |
|---|---|---|
| Architecture | A synchronous SMTP call can couple review latency to delivery | Keep the sender outside the transaction and connect it only through the future worker |
| Security | Addresses and review feedback can leak through headers, errors or logs | Validate one recipient, keep dynamic text in the body and sanitize transport errors |
| Performance | An SMTP server can leave a connection waiting indefinitely | Set finite connect, read and write timeouts |
| User | An SMTP acknowledgement confirms acceptance by the server | Describe the result as accepted for delivery. Show clear subjects for both decisions |
| Simplicity | A custom mail framework would add maintenance before the queue is ready | Use one Spring Mail sender and a local capture test |
