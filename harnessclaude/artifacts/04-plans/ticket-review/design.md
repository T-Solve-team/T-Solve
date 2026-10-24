---
module: ticket-review
date: 2026-10-20
---

# Design: ticket-review

Spec: `artifacts/03-modules/ticket-review/spec.md`

## Architecture Overview
Implement within `vn.tsolve.review`, following controller → service interface → implementation → repository. Review DTO records and MapStruct mappers prevent JPA entities from leaving services. Keep the old `/api/tickets` and Solution APIs unchanged; those scaffold APIs do not implement the SRS ticket lifecycle.

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
- `backend/src/main/java/vn/tsolve/review/controller/`: review endpoints; exceptions use the shared GlobalExceptionHandler.
- `backend/src/main/java/vn/tsolve/review/service/` and `service/impl/`: ReviewService, ReviewAccessService, AuditService, notification handoff interfaces/implementations.
- `backend/src/main/java/vn/tsolve/review/entity/`, `dto/`, `mapper/`, `repository/`: review persistence and records.
- `backend/src/main/java/vn/tsolve/review/config/`: enable method authorization for the module without supplying an identity adapter.
- `backend/src/test/java/vn/tsolve/review/`: entity, service, MVC/authorization and persistence transaction checks.
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

Chosen: D1 batch Request changes shares one comment across all selected tickets, including all-page selection. D2 excludes empty-Solution state and removes MSG20 and empty flags. Alternatives: the superseded single-ticket contract and approval-time empty check. Rationale: explicit FE Lead decisions take precedence until the Report3 documents are corrected. Submission integrations guarantee populated Solutions. Keep other catalog codes unchanged. Restore main bug identities, then use consecutive branch IDs from BUG-012; human attribution is Phạm Tùng Dương.

## FE/BE review amendments — 23 October 2026

- Keep backend review code and tests in `vn.tsolve.review`, retaining table names and applied Liquibase changesets. Leave legacy ticket snapshot classes in `vn.tsolve.ticket`.
- Ticket detail checks Project scope first, then allows unpublished content only to the Project PM or author. Other readers use the Published-only service path (404 MSG04 for unpublished), retaining that consumer interface.
- All three request schemas and the internal decide port enforce max 500 before locks (400 MSG06); UI disables every action over the limit and explains the limit without truncating Select all or splitting transactions.
- A null Evidence list means no files; defensive immutable copy remains for non-null lists. Maximum five Evidence items stays constructor-enforced, with no migration edit.
- Test TicketDecisionActions within the review feature. Other modules retain ownership of their tests; retain page rendering coverage separately without review AC ids. Review Queue navigation is role-gated.
- Keep `/api/v1/tickets/{id}` as the review read contract for this PR. Legacy snapshot IDs at `/api/tickets/{id}` are distinct; ticket-repository owns eventual mapping/consolidation.
- Production auth/Project adapter and notification consumer remain excluded by the original scope. They must be connected by be-foundation roles-security/me-session, identity and workspace/Project integration, then email delivery/retry. Upstream task Jira keys are not assigned in the current plan; do not invent KAN numbers.
- Preserve main context/map and base tracking; replay this module's status/approval commands through sdd.ts.

## Rejection review amendments — 24 October 2026

This section supersedes the separate review ticket/Solution store, legacy-code changes and independent audit writer described in the original design. User decisions: integrate KAN-31 canonical tickets/history and be-foundation audit before merge; use trusted writer user IDs, never free-text names for authorization. No subagents were used.

### Dependency contract and ownership

- Review depends on ticket-repository service interfaces/DTOs and be-foundation common services. It must not import another module's entity/repository, copy its implementation into review, or introduce a reverse call from ticket-repository to review.
- KAN-31 currently has canonical `ticket`, inline Solution fields and `ticket_review_decision`; its history differs from review's current table. The numeric `002` prefix is not itself a Liquibase identity clash. The duplicate table creation and incompatible FK/columns are the actual blocker.
- Required canonical operations: scoped queue/detail snapshots, one ordered batch lock operation, source/status validation, canonical status updates and append-only history writes, all participating in the caller's transaction. The existing KAN-31 branch does not yet supply the complete review service contract. Do not invent a working adapter or bypass the service boundary while this is missing.
- Solution Form integration must supply a trusted immutable writer user ID. Free-text `writer` is display data only. Missing trusted author identity denies author access; separately verified managing PM rights remain valid. Imports remain Published under the owning module's GBR-23 implementation.
- Review may retain review-specific metadata and notification handoffs keyed to canonical ticket IDs; it must not retain a competing authoritative ticket status or Solution. Exact metadata/history mapping must be agreed against the dependency implementation before code/migration execution.
- Use `vn.tsolve.common.audit.AuditService.record(AuditAction, String resourceType, String resourceId)` in the decision transaction. Foundation owns actor resolution, JDBC INSERT and audit schema. Remove the review JPA audit writer after the dependency is available; no mapping to `resource`/`result` survives. Reuse foundation role constants and its single security configuration.
- Restore legacy message-only exception compatibility under foundation Q3. Review must not assign MSG codes to Solution/Ticket callers owned by other modules. Use specific review exceptions extending foundation bases rather than one catch-all review error.

### Ordered implementation and migration gate

1. Finish and commit the current main-conflict/FE recovery fix with user agreement before starting the backend integration feature.
2. Obtain updated spec approval and dependency-owner service/migration contracts. Foundation is planned; KAN-31 remains an unmerged branch. No dependency code has been merged by this amendment.
3. Integrate the reviewed dependency commits without force-push. Reconcile schema ownership before attempting a fresh combined boot. Never edit an applied changeset, mark it executed, reset a shared DB or delete historical rows to hide the collision.
4. Define a forward migration with an explicit old-review-to-canonical identity/history mapping. Test both fresh installation and upgrade with existing review history. Preserve rows while mapping is unverified; reject ambiguous mappings rather than matching by writer names or assuming equal numeric IDs. Deployment/data conversion is not authorized here.
5. Implement canonical service orchestration, shared audit and legacy exception compatibility. Lock an ascending batch once rather than per-ticket repeated reads; verify bounded statement counts instead of asserting the reviewer's unmeasured 2,500-query estimate.
6. Add foundation-aligned Testcontainers (`pgvector/pgvector:pg16`, Spring `@ServiceConnection`) and non-secret test-only SERVER_PORT/CORS values. Database lifecycle belongs to containers, never a configured host DB. Use dependency-compatible Testcontainers version; KAN-31's Docker API compatibility must be retained.
7. Run `mvn verify` with no host SERVER_PORT/database credentials and add backend CI with Docker. Verify combined migrations, rollback, audit/history/notification atomicity, concurrent PM decisions and cleanup. Shared POM/test config/CI changes require the project's scoped gate/owner review.

### Error ordering and frontend recovery

- Check trusted actor/Project access before exposing source. For an accessible imported Published ticket, Request changes returns MSG18 before checking Pending review, making AC-004.4 reachable through a legitimate fixture. Tests must not mutate source/status via reflection into an impossible import state.
- Refresh queue, complete eligible-ID query and affected details on every settled mutation. Keep the refresh promise pending through refetch. Reconcile user selection with the complete eligible-ID set, preserving off-page choices. Key the queue selection/dialog state by Project URL scope so Back/Forward clears it too.
- The batch constant mirrors OpenAPI `maxItems: 500`. Keep all IDs selected above the limit and disable every decision; never truncate or auto-split atomic requests.
- Tests cover 409 retry payloads, all three detail decision errors, 404 detail removal, Project history and the existing all-page flow. Keep read-only page rendering/error tests in Ticket Detail and decision tests in review; remove duplicate and self-mock assertions.
- A sender/consumer/retry worker remains excluded by the user's original scope. AC-006.4 states the external sender obligation (three attempts within an hour), not a currently running retry service. AC-006.6 is evaluated by reviewing the integration documentation and live prerequisites; it must not be claimed as an executable delivery test.

### Inline risk review and builder check

Verdict: **CAUTION / merge blocked** until dependencies, canonical author identity, migration mapping and clean-machine backend verification are complete. Security: fail closed on missing trusted identity, and do not expose foreign Project content/source. Data: preserve applied migrations and history; no production conversion now. Product: FE error recovery can be verified independently using mocks. Performance: one ordered batch lock with measured query behavior. Operations: isolated Docker tests and backend CI are required, not a prior successful run against host PostgreSQL.

Unresolved implementation prerequisites are dependency-owned service signatures and exact existing-data mapping. They are integration blockers, not permission to construct a parallel store or silently lower acceptance criteria. Re-read the actual dependency implementations before drafting the migration; record any changed behavior in the spec first.
