---
module: wiki
feature: wiki-preview-file
task: 20
attempt: 1
date: 2026-11-23
---

# Test Report: wiki — Task 20: First page of the original file in the 11.1 preview pane

Run by the reviewer at `49a1dd45` (code `b855ffe5..aba410ae`), in `frontend/`.

| Check | Result |
|-------|--------|
| `npm run format:check` | pass — all files use Prettier style |
| `npm run lint` | pass — 0 errors, 1 warning (`react-refresh/only-export-components` in `src/features/tickets/components/TicketListFilters.tsx`, not in this change) |
| `npm run typecheck` | pass |
| `npm run test` | pass — 122 files, 2011 tests (321.7 s) |
| `npm run build` | pass — built in 14.7 s |
| `npm run generate:api` | pass — no diff in `src/shared/api/generated` |
| `bun harnessclaude/hooks/sdd.ts trace wiki STORY-002` | 27/27 criteria have a test |

## Tests for this task

- `WikiHomePage.test.tsx:480` AC-002.24 previews the first row, then the selected row: the PDF iframe `src` is `blob:file#page=1&view=FitH&toolbar=0` and the content text is not shown.
- `WikiHomePage.test.tsx:522` AC-002.24 previews only the first page of a DOCX.
- `WikiHomePage.test.tsx:535` AC-002.27 shows the first 420 characters (and not 421) when the file answers 404.
- Regression (11.2, same component, default `variant="full"`): `WikiDocumentPage.test.tsx:147, 159, 171, 183, 207, 224, 256, 293` (AC-002.19, AC-002.26, BUG-356, BUG-357, BUG-255), and AC-002.25 at `WikiHomePage.test.tsx:545`. All pass.

## Manual check

jsdom has no layout, so the DOCX width scaling is not covered by the suite. The reviewer ran the `fit()` logic in headless Chrome: a 210 mm page in a 360 px box gets `zoom` 0.381 (rendered width 359.9 px). `offsetWidth` stays unzoomed, so when the `ResizeObserver` fires again (the box's height changes) it gets the same zoom and does not loop.

Coverage: not measured (the frontend has no coverage threshold configured).
