---
module: ticket-repository
feature: ticket-detail-page
task: 25
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude reviewer (independent subagent)
iteration: 1
---

# Review: ticket-repository — Task 25: Ticket Detail screen (KAN-135, STORY-008)

## Summary
This review covers the code at HEAD `7554caca`. The feature was merged in PR #236 (merge commit `09f3bccf`). Nothing has changed `ReviewTicketContent.tsx` or `ReviewTicketDetail.tsx` since then.

`ReviewTicketContent` (`frontend/src/features/review/components/ReviewTicketContent.tsx`):
- **Department, Resolved date and Source link (lines 50-66).** A missing value shows `—`.
- **Source link check (`sourceReference`, lines 25-32).** `new URL(value)` is called with no base. As a result, a relative value (`/ticket`), a protocol-relative value (`//host`) and any value that is not a URL fail the check. Only `http:` and `https:` become a link, with `target="_blank" rel="noopener noreferrer"`. Anything else is plain React text, which React escapes, so a `javascript:` link cannot run (design edge case 25).
- **Review due date (lines 70-78).** It is shown only while the status is `PENDING_REVIEW`.

`ReviewTicketDetail` (`frontend/src/features/review/components/ReviewTicketDetail.tsx`):
- **403 (line 55).** It shows `EmptyState code="MSG08"`, the inline catalog text, with no ticket content and no decision buttons. This branch comes before the 404 → MSG04 branch.
- **Other errors (lines 59-60).** They keep `ErrorState` with a retry button.
- **MSG06 toast (lines 33-37).** A `useEffect` shows it for every error except 403 and 404. `app/providers/global-errors.ts` shows no toast for a failed query, so the toast appears once.
- **Decision buttons.** They are unchanged.
- **Route.** `/tickets/:ticketId` admits `TICKET_RESOLVER` and `PROJECT_MANAGER` (`app/router/route-table.test.tsx:25`).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-008.8: the fields are shown, http/https become a link with new tab and `noopener noreferrer`, and other values are plain text. AC-008.9: the due date is shown only for PENDING_REVIEW. AC-008.10: 403 shows MSG08 inline with no content, and 500 shows the MSG06 toast. |
| Completeness | pass | AC-008.8, AC-008.9 and AC-008.10 each have tests that name them in `TicketDetailPage.test.tsx`. The tests cover `javascript:`, `data:`, `//`, relative and invalid links, null fields, and five non-pending statuses. |
| Correctness | pass | I did not run the tests locally, because the worktree has no `frontend/node_modules`. `frontend-checks` and `frontend-tests` 1–4 passed in CI on PR #236. |
| Integration | pass | The component uses the generated `ReviewTicketDetail` type. `department`, `resolvedDate` and `sourceLink` are optional and nullable, so the `ticket-review` mocks still type-check. |

## Traceability
```
  ✓ AC-008.8  frontend/src/features/tickets/pages/TicketDetailPage.test.tsx
  ✓ AC-008.9  frontend/src/features/tickets/pages/TicketDetailPage.test.tsx
  ✓ AC-008.10  frontend/src/features/tickets/pages/TicketDetailPage.test.tsx
trace ticket-repository (STORY-008): 10/10 acceptance criteria have a test
```
(The full STORY-008 trace is in `task-24-ticket-detail-scope-review.md`.)

## Issues
No critical or blocking issues.

1. **Low. A 401 also gets an MSG06 toast.**
   - Where: `ReviewTicketDetail.tsx:34`. The condition `status !== 403 && status !== 404` also lets 401 through, and network errors (status 0) too.
   - Effect: when the session expires on this screen, the user sees an MSG06 toast in addition to the global MSG07 dialog.
   - Fix: also skip `401`. Skipping the MSG119/MSG120 codes would cover site-unavailable errors too.
2. **Low. One 500 shows MSG06 twice: as a toast and in the inline `ErrorState` alert.**
   - The test checks both (`TicketDetailPage.test.tsx:116-120`).
   - This is what AC-008.10 and the design ask for ("keep `ErrorState` … plus … MSG06 toast"). It goes against the app rule in `app/providers/global-errors.ts:19` ("never two messages for one error"). Informational.
3. **Low. The MSG06 toast can repeat.**
   - Where: `ReviewTicketDetail.tsx:33-37`. The effect depends on `detail.error`, so every failed refetch, for example `refetchOnWindowFocus`, shows the toast again.
   - This is acceptable for a persistent failure, but noisy.
4. **Info. A long source link is not wrapped.** A plain-text `sourceLink` of up to 500 characters is rendered with no wrapping class (`ReviewTicketContent.tsx:64`), so it may overflow on narrow screens. This is cosmetic only.

## Assumptions made
- I did not run the frontend tests locally, because the worktree has no `node_modules` and the dispatcher allowed vitest only if it did. This PASS relies on the green CI run of PR #236 (`frontend-tests` 1–4, `frontend-checks`). The reviewed frontend files are unchanged since that PR.
- I read AC-008.10 ("show MSG06 as a toast") as allowing the inline `ErrorState` alongside the toast, because the design says so. I flag it as Issue 2 rather than a fail.

## Test Report
- Local: not run (no `frontend/node_modules` in the worktree).
- CI on PR #236: `frontend-checks` passed, and `frontend-tests` 1/4, 2/4, 3/4 and 4/4 passed.
