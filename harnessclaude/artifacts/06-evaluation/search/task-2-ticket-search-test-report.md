# Test Report: search — Task 2: Search Published tickets (ticket-search, KAN-145)

**Date:** 2026-10-30
**Change under test:** the staged diff `git diff --cached 1ad08be` on branch `feature/KAN-145-search-page-v2`
(not committed yet; base `1ad08be` = tip of the Task 1 branch). Frontend only, 20 files: the contract, the regenerated
client, `features/search/**`. The harness tracking files (`progress.json`, `events.jsonl`) were left out.
**Commands (from `frontend/`):** `npx vitest run src/features/search` · `npm run format:check` · `npm run lint` ·
`npm run typecheck` · `npm run generate:api` + `git diff --exit-code -- src/shared/api/generated` · `npm run build`.
Traceability: `bun harnessclaude/hooks/sdd.ts trace search STORY-002 STORY-004` (project root).

## Results

| Check | Result | Evidence |
|-------|--------|----------|
| Feature tests (`src/features/search`) | pass | `Test Files 2 passed (2)` · `Tests 29 passed (29)` · 50.2 s, exit 0 |
| Format | pass | `All matched files use Prettier code style!`, exit 0 |
| Lint | pass | `eslint .`, no output, exit 0 |
| Typecheck | pass | `tsc -b --noEmit`, exit 0 |
| `generate:api` drift | pass | orval 8.41.0 regenerated; `git diff --exit-code -- src/shared/api/generated` (working tree vs the staged files) exit 0; `git status` shows no unstaged or untracked file under `frontend/` |
| Build | pass | `tsc -b && vite build`, `✓ built in 1.17s`, exit 0 |
| Full suite (`npm run test`) | not run by the reviewer | The dispatcher runs it elsewhere and asked the reviewer not to (slow machine). See the review's Assumptions made. |

- Feature run: 29 tests (24 page tests in `SearchPage.test.tsx`, 5 mock tests in `mocks/handlers.test.ts`), 0 failed,
  0 skipped.
- The dispatcher's earlier evidence (29/29, format, lint, typecheck, build, no `generate:api` diff) matches this run.

## Coverage

| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch coverage | not measured — `package.json` has no coverage script | none; NFR 1 asks for one named test per AC, not a percentage |
| Acceptance criteria of this task with a named test | 15/15 (AC-002.1–AC-002.14, AC-004.3) | all (NFR 1) |

## Acceptance Criteria → Tests

```
  ✓ AC-002.1  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.2  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.3  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.4  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.5  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.6  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.7  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.8  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.9  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.10  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.11  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.12  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.13  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-002.14  frontend/src/features/search/pages/SearchPage.test.tsx
  ✓ AC-004.1  frontend/src/features/tickets/mocks/handlers.test.ts
  ✓ AC-004.2  frontend/src/features/tickets/mocks/handlers.test.ts
  ✓ AC-004.3  frontend/src/features/search/mocks/handlers.test.ts
  ✗ AC-004.4  no test under this module's paths (...)
trace search (STORY-002, STORY-004): 17/18 acceptance criteria have a test
```

AC-004.4 (the feedback mock) belongs to Task 3 (`iteration-1/tasks.md`) and is outside this feature; its ✗ is
expected now and is not a finding against Task 2. The trace exits 1 for that reason only. AC-004.1 and AC-004.2 are
Task 1's.

## Failures

None in the runs above.

## Notes
- Six page tests wait a fixed 300 ms (`await delay(300)`) to show that no request was made; this adds about 1.8 s
  and goes against guideline 10 T5. It does not make them fail under load, but a late request after 300 ms would
  go unnoticed (review, Informational 5).
- jsdom prints `Not implemented: Window's getComputedStyle() method: with pseudo-elements` many times. This is
  harmless (antd) and shows in every run.
