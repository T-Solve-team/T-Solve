# Task 32: Apply & publish in the popup

**Module:** import-ticket-list
**Assignee:** Đặng Nhật Minh (with Claude)
**Status:** evaluating

## Session 2026-11-19

### Working on: apply-and-publish-popup
- `usePublishPreviewRow` (MSG32, refreshes the Summary and every page of rows; the popup handles its own errors).
- `SolutionFormModal`: Apply & publish for the popup of one row — validates the form, runs fill-solution for that row, then publish; a rejection of either keeps the popup and what was written (AC-017.10).
- Deviation: the button sits at the end of the popup body, right-aligned, not in the footer — `shared/ui/FormModal` has no slot for a second action, and `shared/` changes need the FE Lead's review.
- Mock handler for publish (404, MSG06, MSG40, MSG32).
- Commit: default@fc506bea

### Verification
- `ImportPreviewPage.test.tsx`: 57 passed (4 new for AC-017.8 to AC-017.10).
- `format:check`, `typecheck`, `build`: OK. `lint`: 0 errors, 1 existing warning in `TicketListFilters.tsx`.
- `npm run test`: 1985/1986; the one failure, `AddMemberModal.test.tsx` (300 ms debounce), passes alone (9/9) — timing under load, unrelated.
- `generate:api`: no diff.

### Next up
evaluate import-ticket-list STORY-017
