# Task 29: Fill solution and the Confirm lock on screen

**Module:** import-ticket-list
**Assignee:** Codex
**Status:** evaluating
**Iteration:** 1

---

## Session 2026-11-07

### Working on: fill-solution-screen
- `ImportPreviewTable` reports the selected rows, including preserved selections on other pages. The page retains only their row numbers and statuses. Fill solution appears only for a file without Solutions and requires a nonempty selection of Needs Solution / Valid rows (AC-015.4).
- `SolutionFormModal` reuses the existing form fields and `useFillPreviewSolution` for many rows. Fill solution starts with empty sections and no row details. Apply asks MSG36 with the selected Valid count before replacing existing Solutions; refusal saves nothing and preserves the form. Success refreshes the Summary and rows, closes the popup and clears selection (AC-015.5; edge case 50).
- Saving a selected row through Preview updates its selection status to Valid, so a subsequent Fill solution includes it in MSG36. Switching from Preview to Fill solution starts a fresh form.
- `MSG_NEW_4` is an inline catalog entry with the exact spec text and a catalog test. On a file without Solutions, the page shows its Alert and disables Confirm while `needsSolutionCount + errorCount > 0`; the existing no-Valid-row guard remains (AC-015.6).
- Files modified: `frontend/src/features/ticket-import/components/{ImportPreviewTable,SolutionFormModal}.tsx`, `frontend/src/features/ticket-import/pages/{ImportPreviewPage.tsx,ImportPreviewPage.test.tsx}`, `frontend/src/shared/messages/{catalog.ts,catalog.test.ts}`.
- Commit: default@d3df940
- Branch: `feat/KAN-31-ticket-import-csv-screens` (unchanged).

### Deviations from the plan
- No hook or mock changes were needed: Task 28's hook and fill-solution handler already support many rows. Its `onPreview(row)` and the existing Needs Solution tag were retained.
- The page tests locate MSG36's dialog by its message because antd gives both modal titles the same `test-id` in test mode. The existing Solution-writing helper now changes field values directly with Testing Library `fireEvent.change`, as other page tests do, instead of simulating every keystroke. Assertions and real API / MSW coverage remain intact.
- Follow-up (Claude, 2026-11-07): the full suite then timed out on 2 AC-015 cases (15 s limit; ~9.5 s alone). The page tests now find the row checkboxes with `getByLabelText` instead of `getByRole('checkbox', { name })`, which computes accessible names over the whole antd table; the slowest case fell to ~4.1 s with no longer timeout. Full suite after that: 1442/1442 passed.

### Verification
- All npm commands ran in `frontend/` with `PATH="/c/Users/admin/AppData/Local/nvm/v24.22.2:$PATH"`; `node --version` reported `v24.22.2`.
- Test-first: before production changes, the focused AC-015.4–.6 / catalog run exited 1 with 10 failures, 3 passes and 242 skipped cases: missing Fill solution, missing MSG_NEW_4, and Confirm enabled for unready rows. The first broader red run also encountered a catalog setup timeout; the focused run reproduced the catalog assertion failure.
- `npm run format:check`: exit 0, all matched files use Prettier style. The initial check identified two changed files; `npm run format` fixed them (exit 0), without changing unrelated files.
- `npm run lint`: exit 0, 0 errors and 1 existing `react-refresh/only-export-components` warning in `features/tickets/components/TicketListFilters.tsx:23`.
- `npm run typecheck`: exit 0, no TypeScript errors.
- `npm run test`: exit 0, **1442/1442 tests passed across 80/80 files**, duration **305.16 s**. The suite prints jsdom pseudo-element `getComputedStyle` notices.
- `npm run build`: exit 0, **3586 modules transformed**, Vite build **1.09 s**. The antd chunk remains above the configured 1000 kB warning threshold.
- `npm run generate:api`: exit 0, orval 8.41.0 generated successfully. `git diff --exit-code -- frontend/src/shared/api/generated`: exit 0; scoped git status also showed no generated changes.
- `bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-015`: exit 0, **7/7 acceptance criteria have a test**. `git diff --check`: exit 0.
- Session-start workspace audit reported 1 unrelated failure (fe-foundation's spec changed since approval) and 12 warnings; no import-ticket-list approval failure was reported. No independent review has been run for Task 29.

New tests: **12 page cases and 1 catalog case** (1429 existing + 13 = 1442):
- AC-015.4: `hides Fill solution for a file with Solutions`.
- AC-015.4: `enables Fill solution only for a nonempty selection without Error or Removed rows`.
- AC-015.4: `keeps Fill solution disabled when an Error row is selected on another page`.
- AC-015.5: `opens empty sections, fills selected Needs Solution rows without MSG36 and clears the selection`.
- AC-015.5: `counts selected Valid rows across pages in MSG36, saves nothing on refusal and replaces on OK (edge case 50)`.
- AC-015.5: `counts a selected row as Valid after its Solution is saved through Preview`.
- AC-015.5: `keeps the bulk Solution and selection when Apply is rejected`.
- AC-015.6: `blocks Confirm and shows MSG_NEW_4 for %i Needs Solution and %i Error rows` — (2, 0), (0, 3), (2, 3).
- AC-015.6: `removes the readiness alert when only Valid and Removed rows remain (Valid count %i)` — 1 and 0.
- AC-015.6 (catalog): `holds MSG_NEW_4 with the spec text as an inline message`.

### Blockers
none

### Open questions
none

### Next up
none — Task 29 is the last task.
