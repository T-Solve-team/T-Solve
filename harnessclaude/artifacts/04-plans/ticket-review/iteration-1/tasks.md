---
module: ticket-review
date: 2026-11-01
total_tasks: 2
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
