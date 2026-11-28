---
module: ticket-review
feature: ai-review-assist
task: 23
attempt: 1
date: 2026-11-26
reviewer: reviewer
iteration: 1
---

# Test report: ticket-review — Task 23: ai-review-assist

Worktree `t-solve-kan-462`, branch `feat/KAN-462-ticket-review-ai-check`, HEAD `5946758d`.

## Backend (from `backend/`, JBR 21 from IntelliJ, `-Dapi.version=1.44`)

| Command | Result |
|---------|--------|
| `mvn -B -Dapi.version=1.44 test -Dtest='AiReview*Test,TicketReviewTextTest,TicketDetailVisibilityTest,TicketDecision*Test' -Dsurefire.failIfNoSpecifiedTests=false` | exit 0 |
| `mvn spotless:check` | exit 0 (pass) |

Per class (fresh surefire reports, 02:20):

| Class | Run | Fail | Error | Skip |
|-------|-----|------|-------|------|
| `vn.tsolve.review.controller.AiReviewControllerTest` | 5 | 0 | 0 | 0 |
| `vn.tsolve.review.service.impl.AiReviewServiceImplTest` | 18 | 0 | 0 | 0 |
| `vn.tsolve.ticket.service.impl.TicketReviewTextTest` | 11 | 0 | 0 | 0 |
| `vn.tsolve.ticket.service.impl.TicketDetailVisibilityTest` | 21 | 0 | 0 | 0 |
| **Total** | **55** | **0** | **0** | **0** |

`TicketDecision*Test` matched no surefire unit class; those tests are integration tests run by failsafe. I did not rerun the full `mvn verify` (Docker). The dispatcher reported that it passed at `5946758d` (unit 3291/0/0, IT 1016/0/0).

## Frontend (from `frontend/`, Node v24.22.2)

| Command | Result |
|---------|--------|
| `npm run typecheck` (`tsc -b --noEmit`) | exit 0 |
| `npm run lint` (`eslint .`) | 0 errors, 1 warning: `react-refresh/only-export-components` in `src/features/tickets/components/TicketListFilters.tsx`. This file is unchanged here; the warning dates from commit 2abedfd3 (KAN-227) |
| `npx vitest run src/features/review src/shared/messages` | 18 files, 362 tests passed, 0 failed (135 s) |

Not run: `npm run format:check`, full `npm run test`, `npm run build`, `npm run generate:api` diff check.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace ticket-review STORY-015` → 8/8 acceptance criteria have a test.

## Coverage
No coverage tool was run. Test-to-criterion mapping:
- AC-015.1: the panel is offered on a managed Pending ticket and hidden on Published, Rejected, an unmanaged Project and for the writer.
- AC-015.2: seven fields, SYSTEM/USER roles, the schema's required fields and enums, nulls sent as empty text; the read service returns the title, description and Solution.
- AC-015.3: every check phrase and the language rule are in the system prompt.
- AC-015.4: 10-finding cap, HIGH first, strip, 500-char cut, empty list; the controller serialises the findings.
- AC-015.5: grouped by severity, field label, HTML shown as text, empty-list message, loading and disabled state.
- AC-015.6: AI exception and 10 malformed answers → 503 MSG_NEW_AI_REVIEW_1; retry in the UI; Approve, Reject and Request Changes still work.
- AC-015.7: 401, 403 (role, unverified Workspace, unmanaged Project), 404 (missing or foreign ticket), 409 (each non-pending status, each non-Solution-Form source); the AI is not called on any refusal.
- AC-015.8: captured output holds no ticket text or AI answer; the constructor has no store or audit dependency; the mock store is unchanged; the writer sees no panel.
