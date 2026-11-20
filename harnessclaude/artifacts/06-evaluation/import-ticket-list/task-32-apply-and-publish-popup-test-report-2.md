---
module: import-ticket-list
feature: apply-and-publish-popup
task: 32
attempt: 2
date: 2026-11-19
reviewer: harnessclaude-reviewer
---

# Test report: import-ticket-list — Task 32: Apply & publish in the popup (attempt 2)

Code: `97824ea6..fc506bea` plus the fix `4e2b56cd..720ef9d2`. Run on branch `feat/KAN-373-import-publish-row` at
`f23b344d`, in `frontend/` with Node 24.22.2 (nvm).

## Traceability
`bun harnessclaude/hooks/sdd.ts trace import-ticket-list STORY-017`: 10/10 criteria have a test. AC-017.8 to
AC-017.10 are named in `ImportPreviewPage.test.tsx` (lines 812, 818, 839, 855, 885, 902, 915). AC-017.8 and
AC-017.9 are also named in `JiraImportPreviewPage.test.tsx:366`.

## Frontend

| Command | Result |
|---------|--------|
| `npm run format:check` | All matched files use Prettier code style |
| `npm run lint` | 0 errors, 1 warning: `TicketListFilters.tsx:23` `react-refresh/only-export-components` (known, excluded) |
| `npm run typecheck` | pass (`tsc -b --noEmit`) |
| `npx vitest run src/features/ticket-import` | 7 files, 142/142 passed (159 s, first run; no time-outs) |
| `npm run build` | pass (exit 0) |
| `npm run generate:api` | ran; `git status` shows no change in `src/shared/api/generated` |

New tests in the fix (all pass):
- `ImportPreviewPage.test.tsx:855` AC-017.9 drops a published row from the selection (BUG-349)
- `ImportPreviewPage.test.tsx:885` AC-017.8 one save and one publish on a double click
- `ImportPreviewPage.test.tsx:902` AC-017.10 MSG04 when the row is gone, popup kept
- `JiraImportPreviewPage.test.tsx:366` AC-017.8 AC-017.9 Jira issue published, others stay

The full `npm run test` was not run. Its known flaky file, `AddMemberModal.test.tsx`, is outside this feature.
