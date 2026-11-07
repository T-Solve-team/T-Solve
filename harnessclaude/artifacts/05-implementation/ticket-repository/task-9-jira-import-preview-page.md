# Task 9: Web — Import Preview and popup for a Jira preview

**Module:** ticket-repository
**Assignee:** Đặng Nhật Minh (code by Claude, harnessclaude-developer subagent)
**Status:** passing (commit default@69f0576; evaluation attempt 1 pass)
**Iteration:** 1

---

## Session 2026-11-07

### Working on: jira-import-preview-page
- Tests first: `frontend/src/features/ticket-import/pages/JiraImportPreviewPage.test.tsx` (22 tests). First run: 15
  failed on missing UI (no "Jira import" / date range, no Finish, no Save as draft, no Edge 10 return, no comments or
  link in the popup). 7 passed at once, because the shared KAN-31 code already gives a Jira preview that behaviour:
  rows (AC-002.1), the MSG35 alert (AC-001.3), MSG36 (AC-002.6), MSG37 on Cancel / leave (AC-002.11 ×3), and
  "no Save as draft on CSV" (AC-004.1). They stay as pins.
- Coverage: AC-002.1 Summary (Jira import, Resolved from / to, Valid / Removed / Needs Solution, no Error card) and
  rows · AC-001.3 MSG35 · AC-002.10 Finish label, disabled while a row Needs Solution or no Valid row, enabled after
  the last Apply · AC-003.1 Finish → MSG32 → Import Tickets without MSG37 · AC-003.2 MSG38 dialog next to MSG32 ·
  AC-002.6 MSG36 count, nothing sent before OK · AC-004.1 Save as draft → MSG41, stays; only on a Jira preview ·
  AC-002.11 MSG37 on Cancel and on leaving, a reopened Draft unchanged (no POST, still listed with its date) ·
  AC-002.8 / Edge 10 404 on a re-read, Finish, Save as draft, Remove and Apply → Import Tickets without MSG37 ·
  AC-002.2 comments (author, date, body) and the Jira link in the writable popup and the read-only one (Removed).
- Code:
  - `pages/ImportPreviewPage.tsx`: `isJira = source === 'JIRA_IMPORT'` (a Jira preview is `WITHOUT_SOLUTION`, so the
    existing Needs Solution gate gives AC-002.10). Jira Summary, no Error card, Finish label, Save as draft (disabled
    during Remove / Finish; Finish disabled while it saves). `leave()` (shared with Confirm success) and
    `leaveIfGone` for every mutation's 404; an effect after `useBlocker` leaves on a 404 re-read.
  - `hooks/useImportPreview.ts`: `useSaveDraft` (MSG41, refetch Summary and Drafts list); `useConfirmPreview` shows
    the `notice` (MSG38) as an acknowledgement dialog.
  - `components/jira-snapshot.tsx` (new): Comments and Source link items, used by `ImportTicketPreviewModal` and
    the KAN-31 `SolutionFormModal` (Jira rows open there because they are writable). `SolutionFormModal` gains an
    optional `onError` prop; nothing else of it changed.
  - `mocks/handlers.ts`: `seedJiraPreview()`; Save as draft endpoint; remove-rows, fill-solution and confirm work on a
    reopened Draft too (they used to answer 404); a new upload / pull keeps a Draft; the Drafts list includes the open
    preview once saved.
- Decisions taken while coding (for the user):
  - MSG38 is an acknowledgement dialog (OK), not a toast: the page leaves for Import Tickets at once, and the skipped
    keys must stay readable. The catalog type is `inline`.
  - Summary labels reuse existing words only: "Jira import" (`TICKET_SOURCE_LABELS`), "Resolved from: / Resolved to:"
    (the pull form's labels). Popup labels "Comments" and "Source link" are the SRS III.5.3 field names.
  - Edge 10 return is silent on a re-read; on a mutation the existing MSG04 toast still shows.
  - A 404 on the first read of any preview keeps the CSV behaviour (MSG04 on the page, AC-009.12): the source is
    unknown then.
- Not done (no criterion asks for it): SRS III.5.3 "when opened for several issues: the list of Jira keys" in the
  Fill solution popup.
- No product bug found; `docs/bug-log.md` unchanged.

### Spec gaps
- MSG35: the catalog text still has `{count}` ("… {count} more match this date range …"), but D8 removed the count and
  the backend sends `params: {}`, so the page shows a literal `{count}`. The fix is the Report3 change of D8, then
  `report3-messages.json` and `src/shared/messages/catalog.ts` (FE Lead). Not changed here.
- AC-002.11 "without changing a saved Draft": Cancel sends nothing, but Fill solution / Remove on a reopened Draft are
  saved at once by the backend (task 7 review, judgment call 1). Still open with the user.

### Verification (Node 24, from `frontend/`)
- `npm run format:check` — all files use Prettier code style.
- `npm run lint` — 0 errors, 1 warning (`features/tickets/components/TicketListFilters.tsx`, existing, not touched).
- `npm run typecheck` — exit 0.
- `npx vitest run src/features/ticket-import` — 5 files, 106 tests passed (the CSV `ImportPreviewPage.test.tsx`
  unchanged and green).
- `npm run test` — first run 1 failed / 1449 passed, 2 of 83 files failed. `TicketListPage.test.tsx` could not import
  `generated/tickets/tickets.msw` because I ran `generate:api` (which cleans the generated folder) during the run.
  `ImportPreviewPage.test.tsx` AC-015.5 (edge case 50) timed out at 15 s under load. Both files alone: 83/83 passed.
  Clean rerun with nothing else running: 83 files, 1480 tests passed, exit 0.
- `npm run build` — exit 0. `npm run generate:api` — no diff in `src/shared/api/generated`.
- `bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-002 STORY-003 STORY-004` — 28/28, exit 0.

### Blockers
None.

### Next up
User review and commit of this feature; then Task 10 (`jira-import-verify`).

- Commit: default@69f0576.
