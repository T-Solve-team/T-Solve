# Task 28: Unpublish / Re-publish buttons on Ticket Detail

**Module:** ticket-repository
**Assignee:** dang minh
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-15

### Working on: ticket-publication-buttons
- New `frontend/src/features/tickets/components/TicketPublicationActions.tsx` (+ test, 9 cases), rendered by `TicketDetailPage` above `ReviewTicketDetail` (not changed).
- Shares the detail query of `ReviewTicketDetail` by its key; buttons only for `hasRole('PROJECT_MANAGER') && isProjectManagerOf(projectId)`; `ConfirmDialog` MSG21 (danger) / MSG23; MSG22 / MSG24; `onSettled` invalidates the detail, Ticket List and Review History queries.
- Errors go to the global mutation handler: a 409 without a code shows MSG06, so `handlesOwnErrors` is not used (AC-009.10).
- The cancel tests assert that nothing is sent, not that the dialog left the DOM: antd keeps a closing dialog under jsdom, as `ReviewQueuePage.test.tsx` already works around.
- Verify: `format:check`, `typecheck`, `build` pass; `npm run test` 1803/1803; `lint` has one warning in `TicketListFilters.tsx` (not touched); `generate:api` leaves no diff.
- Commit: default@1315800
- No deviation from the plan.

### Blockers
none

### Next up
evaluate STORY-009
