---
module: import-ticket-list
feature: import-ticket-preview-popup
task: 22
verdict: pass
attempt: 1
date: 2026-11-03
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: import-ticket-list — Task 22: Import Ticket Preview popup

## Summary
Commit `1b6bef5` (repo `default`, range `1b6bef5^..1b6bef5`). The code at HEAD `15d346c` is the same.

- **`DetailModal`** is new in `shared/ui` (D15), at `frontend/src/shared/ui/DetailModal/DetailModal.tsx:19-33`. It has a Modal Header of the Form kind, the content, and one **Close** button. Esc, the X icon and Close all call `onClose`, and it uses `destroyOnHidden`. It is exported from `shared/ui/index.ts:7`.
- **`ImportTicketPreviewModal`** reads the row with `useGetImportPreviewRow`. It shows the row read-only in antd `Descriptions`: Ticket ID, Title, Description, Author, Resolved date, the five Solution sections in full with line breaks kept (`ImportTicketPreviewModal.module.css`), Result as `StatusTag`, and Error details from the catalog. Loading and `ErrorState` with Try again stay inside the popup.
- The **Preview** button of each row opens the popup (`ImportPreviewTable.tsx:59-68`, `ImportPreviewPage.tsx:111,126-130`).

All four ACs have named tests, and the shared run is green. I found no CRITICAL issue. **Verdict pass.**

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-009.4: the Preview button calls `setPreviewRowNo(rowNo)`. AC-010.1: `ImportTicketPreviewModal.tsx:39-72` shows every field, with no input. AC-010.2: closing only resets `previewRowNo`, so the page's selection, page number and queries do not change, and no MSG37 is asked because it is not a navigation. AC-010.3: `:37` shows the error inside the popup while the table stays behind it. |
| Completeness | pass | All four ACs are traced to tests. The kit part matches design D15 (props `isOpen`, `title`, `width?`, `onClose`, `children`). |
| Correctness | pass | `ImportPreviewPage.test.tsx` passes in the shared run (1074/1074). Lint passes, so the ban on raw antd `Modal` outside `shared/ui` holds. |
| Integration | pass | It uses the generated `useGetImportPreviewRow` (`GET /imports/previews/{id}/rows/{rowNo}`, AC-003.3). It reuses `ModalTitle`, `StatusTag`, `ErrorState` and `LoadingState`, and shares `SECTIONS` / `errorText` with the table (`lib/import-row.ts`). `FormModal` is unchanged. |

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-009 STORY-010` → `16/16 acceptance criteria have a test`. The ACs of this task (`frontend/src/features/ticket-import/pages/ImportPreviewPage.test.tsx`):

| AC | Test | Status |
|---|---|---|
| AC-009.4 | `:333` (Preview on row 2 opens the "Import Ticket Preview" dialog of that row) | PASS |
| AC-010.1 | `:333`: every label and value, the full Solution texts, Result "Error", MSG01 detail, no textbox | PASS |
| AC-010.2 | `:369`: the footer Close closes the popup; no MSG37; still on `/import/preview`; the row 1 selection is kept | PASS |
| AC-010.3 | `:390`: 500 on the row → alert with MSG06 inside the dialog; the table is still there | PASS |

## Issues
CRITICAL (blocks pass): none.

Informational (does not block):
1. [Low] `frontend/src/shared/ui/DetailModal/`: the new kit component has no test of its own. Guideline 10 §2 asks for kit component tests ("each variant renders the right text/role"), and the sibling `CsvUpload` has `CsvUpload.test.tsx`. Today the only coverage is through the page test. Fix: add `DetailModal.test.tsx` that renders the title and children, checks there is one Close button and no OK, and checks that Close and Esc call `onClose`.
2. [Low] `frontend/src/features/ticket-import/components/ImportTicketPreviewModal.tsx:28`: the content unmounts as soon as `rowNo` becomes `null`, so the popup body goes blank and shrinks during antd's close animation. Fix: keep the last row number for the content, for example by clearing it in `afterClose` (that would need an `afterClose` prop on `DetailModal`), or accept this as cosmetic.
3. [Info] Spec Definition of Done: the pull request must ask the FE Lead to review `src/shared/ui/DetailModal` (D15) together with `CsvUpload`. This is still open; it is a process step, not a code fix.

The mock 404 without a code (task 20, issue 1) also makes this popup show MSG06 instead of MSG04 in mock mode for a row that no longer exists.

## Assumptions made
- AC-010.1 "read-only": I count antd `Descriptions` with plain text (no inputs) as read-only. The test checks that there is no textbox.
- AC-010.3 names no message. The popup shows the code returned in `ErrorState` (MSG04 for a missing row from the real backend, MSG06 for 5xx) and offers Try again. I accept this.
- I judged only by the second shared verify run at HEAD `15d346c`, and did not run the suites myself.

## Test Report
`task-22-import-ticket-preview-popup-test-report.md`: frontend verify exit 0. format:check, lint (0 errors), typecheck, vitest 1074/1074 and build all pass, and `generate:api` left no diff.
