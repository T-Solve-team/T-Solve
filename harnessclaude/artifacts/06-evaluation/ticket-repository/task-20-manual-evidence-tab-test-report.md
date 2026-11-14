# Test report: ticket-repository — Task 20 (manual-evidence-tab), commit ad87c786

Run by the reviewer on 2026-11-14, in `frontend/`:

| Command | Result |
|---------|--------|
| `npx vitest run src/features/ticket-import src/shared/ui --maxWorkers=2` | 12 files, **172/172 passed** (177.9 s) |
| `npm run typecheck` (`tsc -b --noEmit`) | pass |
| Prettier check on `src/shared/ui/EvidenceUpload`, `src/features/ticket-import` | all formatted |
| ESLint on the same paths | no issues |

The jsdom lines "Not implemented: getComputedStyle with pseudo-elements" are noise from antd, not failures.

New tests: `EvidenceUpload.test.tsx`, with 6 AC-006.22 cases (list/add/remove with uppercase and exactly 10 MB; MSG72
with kept files counted; MSG34 for `.exe` and for 10 MB + 1; no auto upload; disabled + supplied error).
`ManualEntryForm.test.tsx` has 3 new cases: multipart send and clear after Save (AC-006.22); reopen, remove a kept file,
and no new file sent twice (AC-006.18, AC-006.19); a server MSG34 shown under Evidence with the file kept (AC-006.16).

Not re-run here: the full frontend suite (low memory), `npm run build` and `generate:api`. The session log reports all
of them passing.

Traceability: `sdd.ts trace ticket-repository STORY-006` → 22/22.
