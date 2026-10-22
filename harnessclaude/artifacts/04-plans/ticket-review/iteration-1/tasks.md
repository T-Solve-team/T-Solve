---
module: ticket-review
date: 2026-10-20
total_tasks: 2
owner: Dev
---

# Tasks: ticket-review iteration 1

Spec: `artifacts/03-modules/ticket-review/spec.md`

Both features belong to the current iteration's requested review scope. Code starts only after the hook has recorded spec approval. Each feature requires the user's commit agreement before proceeding to the next.

## Task 1: Implement the backend review API

**Story:** STORY-001, STORY-002, STORY-003, STORY-004, STORY-005, STORY-006

**Feature:** review-api

**Description:** Update OpenAPI first. Implement queue and detail reads, three decisions, service integration ports, authorization, new review persistence, per-ticket audit and pending notifications. Use test-first slices internally: (1) ticket state transitions and validation, (2) persistence plus queue/service authorization, (3) controllers and Problem Details, (4) transaction/concurrency verification and integration instructions. These slices are one backend review API feature; do not start frontend behavior changes before its verified commit is agreed.

**Files:**
- Modify: `frontend/openapi/tsolve-api.yaml` (contract only; generated client belongs to Task 2).
- Create: `backend/src/main/java/vn/tsolve/ticket/{controller,service,service/impl,entity,dto,mapper,repository,config}/` review classes described in master design.
- Create: `backend/src/main/resources/db/changelog/changes/002-add-ticket-review.sql`.
- Modify: `backend/src/main/resources/db/changelog/db.changelog-master.xml`.
- Test: `backend/src/test/java/vn/tsolve/ticket/` entity/service/MVC and real PostgreSQL transaction tests.
- Modify: `docs/bug-log.md` for every newly found defect; do not close frontend BUG-001–BUG-003 during backend-only work.
- Create: `harnessclaude/artifacts/05-implementation/ticket-review/task-1-review-api.md` (session and integration notes).

**Dependencies:** recorded spec approval, installed Java 21/Maven, PostgreSQL for actual concurrency/rollback verification.

**Estimated size:** large feature, implemented in the four bounded internal slices above.

**Verification:** entity/service/MVC tests; DB migration and concurrent transaction verification; Maven verify and spotless:check. Test names cite backend AC behavior; frontend-only AC assertions remain for Task 2 and must not be represented as covered. Backend cannot be declared complete if a required test is blocked.

**Risk tests:** master design rows 1–5 and 7–12, including missing adapter, foreign Project, duplicate IDs, empty Solution, self-approval, source restriction, atomic bulk rollback and concurrent decisions.

## Task 2: Connect the frontend review flow

**Story:** STORY-001, STORY-002, STORY-003, STORY-004, STORY-005, STORY-006

**Feature:** review-ui

**Description:** Regenerate the API client, map current review MSG codes, change Department filtering to Project, retain all-page selection for Approve/Reject, remove bulk Request changes, implement read-only review Ticket Detail with three actions, and verify real-client behavior with controlled API test responses. Keep auth /me and live email prerequisites explicit.

**Files:**
- Generated: `frontend/src/shared/api/generated/` via `npm run generate:api` only.
- Modify: `frontend/src/shared/messages/catalog.ts` and directly affected message tests.
- Modify: `frontend/src/features/review/` queue, actions, mocks and exports.
- Modify: `frontend/src/features/tickets/pages/TicketDetailPage.tsx`; add its focused tests/styles as needed.
- Test: `frontend/src/features/review/pages/ReviewQueuePage.test.tsx` plus Ticket Detail regression tests naming BUG-001, BUG-002 and BUG-003.
- Modify: `docs/bug-log.md` statuses and regression-test references.
- Create: `harnessclaude/artifacts/05-implementation/ticket-review/task-2-review-ui.md`.

**Dependencies:** Task 1 verified and committed with user agreement.

**Estimated size:** large feature, split internally into generated-client/MSG, queue and Ticket Detail slices.

**Verification:** format:check, lint, typecheck, full tests and build; browser queue/detail/confirmation/validation flows; final sdd.ts trace for all six stories. Do not call mock-backed checks a complete live test.

**Risk tests:** all frontend cases in master rows 2, 4, 6, 10 and 12; BUG-001–BUG-003; failed mutation leaves selection and popup intact; mutation pending prevents duplicate submission.
## Task 3: Align review exceptions with the main branch rules

User decision on 2026-10-22: refactor review to shared exceptions and one global handler after syncing main. Acceptance criteria and API contract are unchanged.

- Move common exceptions/handler to common/exception and make ReviewException in ticket/exception extend the shared ApiException.
- Preserve review statuses, MSG codes, params and fieldErrors; no module-owned exception handlers and no common-to-module dependency.
- Migrate the three legacy callers to the shared exception package and existing MSG06 fallback rather than inventing message text; close main's BUG-023 with a failing-first regression.
- Verify shared MVC errors, review entity/service/controller regressions, PostgreSQL transactions/concurrency, Spotless and the full frontend suite.
