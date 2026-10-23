---
module: fe-foundation
feature: guidelines
task: 5
attempt: 2
date: 2026-10-23
---

# Test Report: fe-foundation — Task 5: guidelines (attempt 2)

**Date:** 2026-10-23
**Code:** `default: 4940f0d..03f9137` (branch `chore/KAN-63-fe-foundation-docs`, `frontend/AGENTS.md`,
`frontend/guidelines/*.md`, `frontend/src/test/guidelines.test.ts`). The fix for attempt 1 is `03f9137`. I ran the
checks at HEAD `e2ea212`. It differs from `03f9137` only in harness files, so the code is the same. The working
tree was clean.
**Commands (run by the reviewer in `frontend/`, from `package.json` scripts):**
`npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run test` · `npm run build` ·
`npm run generate:api` followed by `git status --short frontend/src/shared/api/generated` · from the repo root
`bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-010` · coverage: no script in `package.json`.
format:check and lint ran at the same time. All the other commands ran one after another.

## Results
- Total: 397 · Passed: 397 · Failed: 0 · Skipped: 0 · Duration: 35.9 s (22 test files, `vitest run`, exit 0).
  This was the first run, with no `findBy` timeout, so no re-run was needed.
- Format check: pass (`All matched files use Prettier code style!`, exit 0)
- Lint: pass (`eslint .`, no output, exit 0)
- Typecheck: pass (`tsc -b --noEmit`, exit 0)
- Build: pass (`tsc -b && vite build`, `✓ built in 703ms`, exit 0)
- `generate:api`: ran (orval 8.41.0, exit 0), and `git status --short frontend/src/shared/api/generated` was empty
  afterwards, so nothing had to be restored
- MSG meanings: I printed the type, context and text of all 45 MSG codes the docs name from
  `report3-messages.json`, and compared each one with its sentence. All fit. `MSG_NEW_2` is only in `catalog.ts`
  (D3), as intended.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch coverage | not measured, because the project has no coverage script | none in the spec's non-functional requirements |

## Acceptance Criteria → Tests
```
  ✓ AC-010.1  frontend/src/test/guidelines.test.ts
  ✓ AC-010.2  frontend/src/test/guidelines.test.ts
  ✓ AC-010.3  frontend/src/test/guidelines.test.ts
  ✓ AC-010.4  frontend/src/test/guidelines.test.ts
trace fe-foundation (STORY-010): 4/4 acceptance criteria have a test
```

## Failed Tests
None.

## Attempt 1
All checks passed at `02f8b86` (397/397, 36.2 s). The doc test's regexes failed on the old docs of `4940f0d` for
each criterion. `guidelines.test.ts` has not changed since then, so I did not repeat that check.

## Notes
- jsdom prints `Not implemented: Window's getComputedStyle() method: with pseudo-elements` (antd). This is noise
  only and causes no failure.
- The known first-`findBy` timeout under full-suite load (BUG-002/003/004) did not occur in this run.
