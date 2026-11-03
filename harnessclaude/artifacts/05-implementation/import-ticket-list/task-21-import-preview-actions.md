# Task 21: Import Preview screen — Remove, Confirm, Cancel and leaving

**Module:** import-ticket-list
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-03

### Working on: import-preview-actions
- Remove (AC-009.3): `useRemovePreviewRows` refetches the Summary and every page of rows; the button is disabled with no selection and shows loading while pending; success clears the selection.
- Confirm (AC-009.5 – AC-009.8): disabled while `validCount` is 0, loading while pending. A ref stops the second call of a double click before `isPending` renders. Success shows MSG32 with `created` / `skipped` and opens Import Tickets; an error shows the returned code and stays.
- Cancel and leaving (AC-009.9, AC-009.10): `useBlocker` + `ConfirmDialog` MSG37 (danger) for every move to another path. Cancel navigates through the blocker. A second ref skips the blocker only for the move a successful Confirm makes. A preview that could not be read blocks nothing.
- Mocks: `handlers.ts` keeps whether the preview is confirmed, so later reads answer 404 (AC-004.12), and answers remove-rows and confirm.

### Deviation
- `F/mocks/data.ts` is in the task's file list but needed no change.

### Verify
- `sdd.ts trace import-ticket-list STORY-009`: 12/13 — AC-009.4 belongs to Task 22.
- `format:check`, `lint` (exit 0, 1 warning in `tickets/components/TicketListFilters.tsx`, not from this task), `typecheck`, `test` (57 files, 1071 passed), `build` (exit 0) pass; `generate:api` leaves no diff.
- Backend not touched, so `mvn verify` was not run.

### Commit
- default@55a1ee6

### Next up
Task 22 import-ticket-preview-popup

## Session 2026-11-04

### Working on: import-preview-actions — Low findings of review attempt 1
- Review issue 1 → BUG-108: Confirm is disabled while a Remove runs and Remove while a Confirm runs.
- Review issue 2 → BUG-109: after a successful Confirm the preview and rows queries are removed once the page has moved to Import Tickets, so browser Back shows MSG04, not the confirmed preview.
- Review issue 3 (Info, 401 toast on Confirm) not changed: a kit-wide question.
- Verify: `mvn verify` BUILD SUCCESS (235 unit tests, 1 skipped as before; 10 ITs; spotless) with JDK 24 and `-Dapi.version=1.44` for Docker Desktop 29; frontend `format:check`, `lint` (0 errors, the old `TicketListFilters.tsx` warning), `typecheck`, `test` (58 files, 1083 passed), `build` pass; `generate:api` leaves no diff.
- Commit: default@11c35cc

### Next up
Module completion
