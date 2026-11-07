---
module: ticket-review
date: 2026-11-01
total_tasks: 10
owner: Dev
---

# Tasks: ticket-review — iteration 1

Spec: artifacts/03-modules/ticket-review/spec.md
Master: artifacts/04-plans/ticket-review/design.md

## Task 1: Complete UUID review queue and atomic mock decisions

**Story:** STORY-001, STORY-002, STORY-003, STORY-004, STORY-005
**Feature:** review-queue-decisions
**Description:** Follow the master's Task 1. Update OpenAPI first, generate UUID client/types, extend feature-owned mock records/history with atomic guarded decisions and proposed detail query, complete queue selection/popups and stale/error recovery. Preserve Department filtering, existing polling and unrelated contracts. Detail UI callers wait for Task 2.
**Files:** frontend/openapi/tsolve-api.yaml; generated API output; frontend/src/features/review/components/**, hooks/**, mocks/**, pages/ReviewQueuePage* and their module-owned tests; docs/bug-log.md for reproduced defects.
**Dependencies:** Spec approved, master design and iteration grouping approved; module planned through sdd.ts.
**Estimated size:** medium–large vertical slice.
**Verification:** Master risks R1–R12 for queue/contracts; named AC-001–005 tests; focused then full frontend tests, format/lint/typecheck/build, repeat generation with no additional generated diff. Browser-check queue, filters/history, cross-page/Select all, single/bulk Approve and both popup flows, cancellation/error/retry at 1366 × 768. Report partial detail scope honestly. Ask for this feature's commit agreement before Task 2.

## Task 2: Add Ticket Detail review controls and read-only content

**Story:** STORY-002, STORY-003, STORY-004, STORY-005, STORY-006
**Feature:** review-ticket-detail
**Description:** Follow the master's Task 2. Implement the scoped read-only detail/actions in review and expose them via its barrel to the existing Ticket Detail placeholder. Wire Task 1 hooks, source/status/Project/author restrictions, safe Evidence references and stale/error recovery.
**Files:** frontend/src/features/review/components/**, hooks/**, index.ts and module-owned tests; frontend/src/features/tickets/pages/TicketDetailPage.tsx and a scoped CSS module only if needed; API proposal/generated output if a contract adjustment is demonstrated; docs/bug-log.md for defects.
**Dependencies:** Task 1 verified and committed with user agreement.
**Estimated size:** medium.
**Verification:** Master risks R1 and R5–R12; AC-006.1–006.6 and detail callers of earlier stories; full frontend checks, 31/31 criterion trace, deterministic generation and browser/narrow-width/keyboard inspection. No backend or real Jira/audit claims. User commit checkpoint applies; push/PR only if requested.

## Task 3: KAN-158 Expired Tickets and mock send-back

**Story:** STORY-007
**Feature:** expired-tickets
**Description:** Implement the KAN-158 extension in the master and iteration design. Generate the proposed contract, add guarded expiry fixtures/list/ids/send-back handlers, complete the existing page and reuse Ticket Detail navigation. Deliver one independently reviewable feature on feat/KAN-158-review-expired-tickets based on the authorized KAN-24 dependency.
**Files:** frontend/openapi/tsolve-api.yaml; generated API output; frontend/src/features/review/pages/ExpiredTicketsPage*; review feature-local components/hooks/mocks and tests; docs/bug-log.md only for reproduced defects.
**Dependencies:** User approval of current spec (AC-007.1–007.8), existing KAN-24 base; production KAN-156/157 are future integration dependencies rather than implemented here.
**Verification:** Named AC-007.1–007.8 tests covering every risk in the extension, focused then full frontend checks, deterministic generation, trace and browser review. Record evidence and request agreement for feat(review): add expired ticket workflow (KAN-158). Do not begin KAN-159 before this feature's approved commit.

## Task 4: KAN-159 Review History

**Story:** STORY-008
**Feature:** review-history
**Description:** Implement the KAN-159 extension in the master/iteration design. Add the proposed read-only generated endpoint, scoped mock query and stable history records; replace ReviewHistoryPage placeholder with shared table, Department/decision/date URL filters, server pagination/sort, Ticket Detail links and query states. Invalidate history after existing successful decisions/send-back.
**Files:** frontend/openapi/tsolve-api.yaml; generated API output; frontend/src/features/review/pages/ReviewHistoryPage*; review feature-local hooks/components/mocks and tests; existing review decision/send-back invalidation callers; docs/bug-log.md only for reproduced defects.
**Dependencies:** Current STORY-008 spec approval; committed KAN-158 and inherited KAN-24 detail. No backend/SSE/Unpublish mutation delivery.
**Verification:** AC-008.1–008.7 tests and design edge cases; focused tests, trace, full required frontend checks and deterministic generation. No manual browser testing per user. Review before reporting completion and request agreement for feat(review): add review history (KAN-159).

## Task 5: Shared UUID storage for Review (KAN-24)

**Story:** STORY-009
**Feature:** review-storage
**Description:** Implement AC-009.9 and R13. Evolve the shared Ticket table, add Evidence and Review decision storage, and preserve import rows and event delivery.
**Files:** Tenant Liquibase changelog, Ticket entity and repository, import decision entity, import confirmation service and event, affected import tests and Review migration integration test.
**Dependencies:** Approved STORY-009, current Liquibase baseline and database/02_tenant.sql.
**Verification:** PostgreSQL upgrade test, import regressions, backend verification and Spotless.

## Task 6: Atomic Review decisions (KAN-24)

**Story:** STORY-009
**Feature:** review-decision-transaction
**Description:** Implement the transaction for AC-009.4–009.8, covering Approve, Reject and Request Changes. Keep it inside the Ticket service boundary so ticket state and decision history share the same transaction. Accept Account and managed Project scope from a trusted internal caller. Keep HTTP integration in Task 7.
**Files:** Ticket domain model, repository and decision service; decision history entity and repository; PostgreSQL integration tests in the Review test scope.
**Dependencies:** Task 5. A scoped PostgreSQL test supplies the authorized Account and Project set.
**Verification:** Real PostgreSQL tests for decisions, rejection of invalid batches, competing decisions and rollback; run backend verification and Spotless.

## Task 7: Review decision endpoints (KAN-24)

**Story:** STORY-009
**Feature:** review-decision-endpoints
**Description:** Connect the three existing OpenAPI mutations to Task 6. Use an internal access interface for the verified Workspace Account and managed Projects. Deny access when its production adapter or binding is absent. Cover AC-009.3 and the decision API portion of AC-009.4–009.8.
**Files:** Review controller, access interface and two-Workspace API integration tests.
**Dependencies:** Task 6. Tests implement the trusted Workspace and membership contract against two PostgreSQL databases.
**Verification:** Check session, role, scope, UUID batch and feedback validation, cross-Workspace denial and committed decisions through HTTP.

## Task 8: Record Review Queue entry time (KAN-24)

**Story:** STORY-009
**Feature:** review-queue-entry-time
**Description:** Implement AC-009.11. A tenant migration records when a Solution Form ticket enters Pending review. Keep unknown historical entry times null. Record a new time on a new entry without changing the due date.
**Files:** Tenant Liquibase changelog and Review storage and transaction integration tests.
**Dependencies:** Task 5 and approved queue entry time criteria.
**Verification:** PostgreSQL upgrade and lifecycle tests, backend verification and Spotless.

## Task 9: Production Review reads and web integration (KAN-24)

**Story:** STORY-009
**Feature:** review-production-api
**Description:** Implement AC-009.1–009.2, AC-009.10 and AC-009.12. Connect queue, eligible IDs and detail to persisted data, expose the nullable queue entry time and verify the existing web decision callers through the production contract.
**Files:** Review controller, services, tests and existing OpenAPI or web Review files where needed.
**Dependencies:** Tasks 7 and 8 and Workspace-scoped Project metadata including public Project and Department IDs.
**Verification:** Backend authorization, concurrency and rollback tests, plus real web API integration.

## Task 10: Request Changes feedback in Jira (KAN-24)

**Story:** STORY-009
**Feature:** review-jira-feedback
**Description:** Complete the Jira clause of AC-009.6 and cover R15.
**Files:** Jira issue panel and Review feedback boundary and tests.
**Dependencies:** Task 9 and verified Jira issue and writer context.
**Verification:** MSG64 for the writer and denial for an unrelated issue, Account or Workspace.

## Task 11: Email the writer after Reject or Request Changes (KAN-155)

**Story:** STORY-010
**Feature:** review-email-notification
**Description:** Implement AC-010.1–010.6. `TicketDecisionServiceImpl` publishes the saved Reject and Request Changes decision ids inside the decision transaction. An after-commit listener enqueues one `review-notification` tenant job per decision id when `TenantJobService` is available, and logs only a count on failure. The job handler loads the decision, ticket and writer account in the Workspace and calls the KAN-162 `ReviewNotificationSender`; delivery failure fails the job so JobRunr retries it.
**Files:** `ticket/event/ReviewFeedbackDecidedEvent.java`, `ticket/entity/ReviewDecisionRecord.java` (getters), `ticket/service/impl/TicketDecisionServiceImpl.java`, `review/service/impl/ReviewNotificationEnqueuer.java`, `review/service/impl/ReviewNotificationJobHandler.java`, tests in `review/TicketDecisionIT.java` and `review/service/impl/`.
**Dependencies:** KAN-24 decisions (PR #137), KAN-102 tenant jobs (PR #139), KAN-162 SMTP sender (PR #131).
**Verification:** Unit tests for enqueue and job outcomes, a PostgreSQL test that jobs are enqueued only after commit and never after rollback, `mvn verify` with Spotless.

## Task 12: Production Review History read API (KAN-159)

**Story:** STORY-011
**Feature:** review-history-api
**Description:** Implement AC-011.1–011.6. `GET /api/v1/review-history` in `ReviewReadController` resolves the caller's managed Projects through `ReviewWorkspaceAccess`, and `TicketReviewReadService.history` pages the stored `review_decision` rows joined to ticket and reviewer account in the database, with the Department, decision and Vietnam-time date filters. Invalid dates return MSG39; other invalid values MSG06. The existing generated web hook needs no change.
**Files:** `review/controller/ReviewReadController.java`, `ticket/service/TicketReviewReadService.java`, `ticket/service/impl/TicketReviewReadServiceImpl.java`, `ticket/repository/ReviewDecisionRecordRepository.java`, `ticket/dto/TicketReviewView.java`, `ticket/exception/InvalidReviewHistoryFilterException.java`, `frontend/openapi/tsolve-api.yaml` (description only), tests in `review/`.
**Dependencies:** KAN-24 storage and read APIs (PR #137).
**Verification:** PostgreSQL tests for scope, filters, mapping and paging; HTTP tests for 400/401/403; `mvn verify`.

## Task 9 addendum: Project membership adapter (KAN-24)

**Story:** STORY-009
**Feature:** review-production-api
**Description:** Activate AC-009.1, AC-009.2, AC-009.10 and AC-009.12 outside tests (design: "Project membership adapter"). Add `project_membership` from `database/02_tenant.sql` with UUID references and implement `ReviewWorkspaceAccess` over it, failing closed for a missing or inactive Account.
**Files:** `db/changelog/changes/017-project-membership.sql`, `db.changelog-master.xml`, `review/entity/ProjectMembership*.java`, `review/repository/ProjectMembershipRepository.java`, `review/service/impl/ProjectMembershipReviewAccess.java`, `review/ProjectMembershipReviewAccessIT.java`; the two API ITs mark their test adapter `@Primary`.
**Dependencies:** Task 9.
**Verification:** PostgreSQL test of managed and readable Projects, inactive Account, foreign Project; `mvn verify`.
