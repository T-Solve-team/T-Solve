---
module: ticket-review
date: 2026-10-20
---

# Design: ticket-review

Spec: `artifacts/03-modules/ticket-review/spec.md`

## Architecture Overview
Implement within `vn.tsolve.ticket`, following controller → service interface → implementation → repository. Review DTO records and MapStruct mappers prevent JPA entities from leaving services. Keep the old `/api/tickets` and Solution APIs unchanged; those scaffold APIs do not implement the SRS ticket lifecycle.

`ReviewAccessService` is an integration port. For every service request it supplies the authenticated actor, current managed Project references and readable Project scope from trusted server-side identity/Project data. Its absence denies access. No request field or header can supply the actor or a list of managed Projects. The eventual adapter must revalidate active account and Project rights on every request. Configure method security for review controllers; do not disable CSRF or replace the application's authentication configuration with demo credentials.

Queue reads are repository queries constrained by managed Project IDs, status Pending review and source Solution Form. Project filtering further narrows the server scope. Default sort is reviewDueDate ascending, then ticket ID for stable pagination. Allow sorting by sourceTicketId, title, project name, authorName, enteredQueueAt and reviewDueDate; reject arbitrary properties. Default size 20; supported sizes 1–100.

Decision writes acquire database row locks in ascending ticket ID order. Validate the complete selection, authorization, state and source eligibility before changing anything. Then apply the action, append per-ticket history, call AuditService.record and append notification handoffs inside one transaction. A second concurrent request observes the changed state after acquiring the lock and returns conflict. No email or Jira network call occurs in a transaction.

`ReviewService` exposes DTO-based registration of submitted review tickets and Published ticket lookup to trusted integrations. No HTTP endpoint creates arbitrary review data. Evidence references are metadata only; this module neither creates download URLs nor implements file delivery. Ticket Detail renders those references without inventing working downloads.

## Data Model
Add `002-add-ticket-review.sql`; include it in the existing Liquibase master. Existing changesets and data remain intact. These are new review-owned records populated by the future submission integration; no existing scaffold record is silently converted.

| Table | Owned data and constraints |
|-------|---------------------------|
| ticket_review | Generated ticket ID; unique source/sourceTicketId; external Project ID/name; writer ID/display name; source; SRS status; entered queue at, review due at and updated at; current change comment; optimistic version as an additional integrity guard |
| ticket_review_solution | Exactly one row per ticket via unique ticket FK; Symptoms, Root cause, Resolution steps, Workaround, Scope of applicability; a future resubmission replaces the row instead of overwriting it |
| ticket_review_evidence | Ticket FK and immutable integration-provided Evidence ID/name/type/size; metadata only; no upload/download implementation |
| ticket_review_decision | Ticket FK; actor identity; writer identity; action; reason/comment; timestamp. Append only |
| ticket_review_notification | Unique decision FK; recipient identity; action; reason/comment; created at; pending handoff data for the future email sender |
| audit_event (existing) | Append-only actor/action/resource/result/timestamp through the module's AuditService interface and implementation |

Foreign keys prevent orphan review content/history. Add the queue index on Project/status/source/reviewDueAt/id and decision index on ticket/time. Statuses use the SRS spellings as Java enums and strings in the database. Require positive external IDs and nonnull dates for registered queue tickets. The module does not calculate deadlines or schedule expiry.

## Key Decisions
| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Runtime stack | Existing Spring Boot/JPA/PostgreSQL/MapStruct and React/TanStack/orval | New framework or dependencies | Existing architecture and Ponytail preference |
| Auth and email scope | Integration ports; deny review without verified identity; durable pending email records | Implement Google OIDC/SMTP now | User explicitly selected review-only integration scope |
| Existing schema | New forward-only review tables, leaving legacy scaffolds untouched | Reuse legacy Solution statuses; rewrite applied SQL | Existing lifecycle differs from SRS and applied migrations are immutable |
| Atomic bulk decisions | One transaction with ordered row locks | Partial success; per-ticket transactions | UC-30 saving error leaves all tickets unchanged |
| Missing mail sender | Persist pending handoff, never claim delivery | No-op sender; synchronous SMTP | Decision survives mail outages without silent notification loss |
| Request changes contract | `ticketIds` and one shared comment from queue or detail | Single-ticket restriction | FE Lead D1 / FE-34 overrides pending GBR-09 / UC-30 corrections |
| Error rendering | Shared ApiException and one common/exception/GlobalExceptionHandler | Module-specific handlers | Follow the current backend rule and preserve review Problem Details |
| Shared frontend edits | Scoped MSG13–MSG19 remapping and MSG20 removal under D2 and generated API updates; preserve unrelated codes | Remap entire MSG catalog | Minimum required SRS correction; normal FE Lead review still applies |

Decided in planning: internal class names, deterministic sort tie-breaker, row-lock acquisition order, table/index names and scoped error advice are implementation choices; they do not add business behavior.

## File Structure
- `backend/src/main/java/vn/tsolve/ticket/controller/`: review endpoints and scoped exception advice.
- `backend/src/main/java/vn/tsolve/ticket/service/` and `service/impl/`: ReviewService, ReviewAccessService, AuditService, notification handoff interfaces/implementations.
- `backend/src/main/java/vn/tsolve/ticket/entity/`, `dto/`, `mapper/`, `repository/`: review persistence and records.
- `backend/src/main/java/vn/tsolve/ticket/config/`: enable method authorization for the module without supplying an identity adapter.
- `backend/src/test/java/vn/tsolve/ticket/`: entity, service, MVC/authorization and persistence transaction checks.
- `frontend/openapi/tsolve-api.yaml`: update first; generated client comes from the existing generator.
- `frontend/src/features/review/`: Project filter, queue actions and batch decision components/hooks.
- `frontend/src/features/tickets/pages/TicketDetailPage.tsx`: read-only review detail and actions imported through the review feature's index.
- `frontend/src/shared/messages/`: current SRS review codes and affected catalog tests.
- `harnessclaude/artifacts/05-implementation/ticket-review/`: implementation sessions and integration instructions.
- `docs/bug-log.md`: permanent discovered-defect ledger.

## External Dependencies
- Existing /me and authenticated frontend session are prerequisites, not implemented here.
- Auth/Project adapter is required for successful backend review access.
- Submission integration registers review tickets and saved Evidence metadata.
- Email sender consumes pending notification records and implements up to three retries within one hour. This module does not run that worker.
- Published-state access is exposed to downstream consumers; search/triage/chatbot ingestion is excluded.
- Java 21 and Maven are not currently on the shell PATH; locate installed distributions before verification. Do not report tests as passed without running them.

## Edge Cases & Risks
Dimensions analyzed: all 12. Browser coverage targets desktop, matching SRS scope; no mobile feature is added.

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-006 | User types | Missing identity adapter or unauthenticated requester | Critical | Deny without fake actor | 1 |
| 2 | STORY-003, STORY-004 | Input extremes | Null, blank, whitespace-only, exactly 1000 or 1001 characters | High | Required/length validation at both boundaries | 1, 2 |
| 3 | STORY-005 | Timing | Two decisions or repeated submission race for one ticket | Critical | At most one commit per Pending review state | 1 |
| 4 | STORY-001 | Scale | Empty queue, page boundary, all-page selection, unsupported sort | High | Scoped stable paging; empty MSG13; reject bad sort | 1, 2 |
| 5 | STORY-005 | State transitions | Mixed Pending review and already-decided selection | High | Reject whole batch | 1 |
| 6 | STORY-001 | Environment | Offline list load or slow mutation | Medium | Error/retry and pending controls; no premature success | 2 |
| 7 | STORY-005, STORY-006 | Error cascades | Audit/notification persistence fails; external mail is unavailable | Critical | Persistence failure rolls back; mail outage retains decision plus handoff | 1 |
| 8 | STORY-005 | Authorization | Foreign Project ID or ticket ID inserted in a selection | Critical | No foreign data or mutation | 1 |
| 9 | STORY-005 | Data integrity | Duplicate IDs, orphan content or duplicate notification | High | Reject invalid selection; FK/unique constraints | 1 |
| 10 | STORY-006 | Integration | Real APIs without /me/auth/submission/mail | High | Document prerequisites; tests never described as complete live success | 1, 2 |
| 11 | STORY-005 | Compliance | Missing audit or ticket content leaked through logs | High | Per-ticket append-only audit; no Solution/PII payload logging | 1 |
| 12 | STORY-002, STORY-004 | Business logic | Own-ticket approval, non-Solution Form changes, batch comment propagation | High | D2 excludes empty-Solution state; allow own-ticket approval; refuse invalid change source for entire batch | 1, 2 |

Open spec questions: none. Auth/email implementation exclusions are confirmed by the user. Technical limitations remain explicit integration prerequisites.

## Risk Review — 2026-10-20 — Verdict: CAUTION
Reviewed inline without subagents, as requested.

| Topic | Architect | Security | Performance | User | Devil's advocate | Resolution |
|-------|-----------|----------|-------------|------|------------------|------------|
| External auth | Existing seam fits | Missing adapter must deny | Scope once per request | Cannot sign in end to end yet | Do not build a second auth system | Require trusted adapter; document limit |
| Separate review records | Avoid legacy lifecycle coupling | IDs alone grant no access | Indexed queue | Existing records do not appear automatically | Keep schema limited to requested behavior | Submission owns population; no silent conversion |
| Bulk writes | Single transaction | Revalidate all IDs | Lock ascending IDs | Clear all-or-nothing outcome | Avoid custom job framework | JPA locks and transaction only |
| Notifications | Durable handoff | Recipient from persisted identity | No SMTP in transaction | Delivery is pending | No pretend sender | External sender obligation is explicit |

Agreements: preserve existing scaffold APIs, enforce Project rights on the server, and document unavailable live integrations. Correctness and authorization take priority over a superficially working demo.

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Auth bypass through fallback | Critical | No adapter means denied access; negative MVC/service tests | 1 |
| Partial or duplicate decisions | Critical | Ordered row locks, transaction, rollback/concurrency verification | 1 |
| UI/API contract drift | High | OpenAPI first, regenerate client, frontend regression checks | 1, 2 |

## Builder Check
Re-read spec/module/architecture as implementation inputs. All requested actions, validation limits, Project boundaries, notification exclusions and failure outcomes are defined. Internal decisions above are implementation details. A real PostgreSQL execution remains required to substantiate lock/rollback claims; mocked unit tests alone cannot prove them.

## FE Lead amendments (2026-10-22)

Chosen: D1 batch Request changes shares one comment across all selected tickets, including all-page selection. D2 excludes empty-Solution state and removes MSG20 and empty flags. Alternatives: the superseded single-ticket contract and approval-time empty check. Rationale: explicit FE Lead decisions take precedence until the Report3 documents are corrected. Submission integrations guarantee populated Solutions. Keep other catalog codes unchanged. Restore main bug identities, then use consecutive branch IDs from BUG-008; human attribution is Phạm Tùng Dương.
