# Test Report: fe-foundation — Task 5: guidelines

**Date:** 2026-10-23
**Code:** `default: 4940f0d..02f8b86` (branch `chore/KAN-63-fe-foundation-docs`, `frontend/AGENTS.md`,
`frontend/guidelines/*.md`, `frontend/src/test/guidelines.test.ts`). I ran the checks at HEAD `5b3ad1d`. It differs
from `02f8b86` only in harness files, so the code is the same. The working tree was clean.
**Commands (run by the reviewer one after another in `frontend/`, from `package.json` scripts):**
`npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run test` · `npm run build` ·
`npm run generate:api` followed by `git status --short frontend/src/shared/api/generated` · from the repo root
`bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-010` · coverage: no script in `package.json`

## Results
- Total: 397 · Passed: 397 · Failed: 0 · Skipped: 0 · Duration: 36.2 s (22 test files, `vitest run`, exit 0).
  This was the first run, with no `findBy` timeout, so no re-run was needed.
- Format check: pass (`All matched files use Prettier code style!`, exit 0)
- Lint: pass (`eslint .`, no output, exit 0)
- Typecheck: pass (`tsc -b --noEmit`, exit 0)
- Build: pass (`tsc -b && vite build`, `✓ built in 706ms`, exit 0)
- `generate:api`: ran (exit 0), and `git status --short frontend/src/shared/api/generated` was empty afterwards,
  so nothing had to be restored
- Old docs: I copied the docs of `4940f0d` to the scratchpad and ran the same regexes as `guidelines.test.ts` on
  them with my own script. Every criterion fails there: AC-010.1 (no "35 screens"; Staff and Department Manager
  in AGENTS.md, 01, 03, 07, 08), AC-010.2 (no Space row), AC-010.3 (FE-34 not "kept by D1"), AC-010.4 (MSG20,
  MSG63, MSG80, MSG82, MSG83, MSG91, MSG92, MSG94 not in the catalog; `STAFF`, `DEPARTMENT_MANAGER` in 02 and 12).
  On the new docs the same script finds nothing.

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

## Notes
- jsdom prints `Not implemented: Window's getComputedStyle() method: with pseudo-elements` (antd). This is noise
  only and causes no failure.
- The known first-`findBy` timeout under full-suite load (BUG-002/003/004) did not occur in this run.
