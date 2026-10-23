# Test Report: fe-foundation — Task 4: catalog-tags

**Date:** 2026-10-23
**Code:** `default: a5ee1a7..f67ead7` (branch `feature/KAN-62-fe-foundation-catalog`, mostly `frontend/`). I ran
the checks at HEAD `2e835d1`. It differs from `f67ead7` only in harness files, so the code is the same.
**Commands (run by the reviewer one after another in `frontend/`, from `package.json` scripts):**
`npm run format:check` · `npm run lint` · `npm run typecheck` · `npm run test` · `npm run build` ·
`npm run generate:api` followed by `git status --short frontend/` · from the repo root
`bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-006` and `STORY-007` (and `STORY-003` for BUG-005) ·
coverage: no script in `package.json`

## Results
- Total: 352 · Passed: 352 · Failed: 0 · Skipped: 0 · Duration: 36.2 s (21 test files, `vitest run`, exit 0).
  This was the first run, with no `findBy` timeout, so no re-run was needed.
- Format check: pass (`All matched files use Prettier code style!`, exit 0)
- Lint: pass (`eslint .`, no output, exit 0)
- Typecheck: pass (`tsc -b --noEmit`, exit 0; `tsconfig.app.json` includes `src`, so the `expectTypeOf` checks of
  AC-006.7 and AC-007.3 are type-checked)
- Build: pass (`tsc -b && vite build`, `✓ built in 28.68s`, exit 0). `report3-messages.json` is not in `dist/`.
- `generate:api`: ran, and `git status --short frontend/` was empty afterwards, so the generated client matches
  `openapi/tsolve-api.yaml` and nothing had to be restored
- Reference data: I parsed `$TSOLVE_DOCS/Report3/System_Messages.md` with my own script (scratchpad, not in the
  repo) and compared it to `src/shared/messages/report3-messages.json`: 103 = 103 codes, 0 differences in type,
  context or text. `_context/Report3/System_Messages.md.md` has the same text (`diff --strip-trailing-cr` finds
  only the source comment on line 1).

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch coverage | not measured, because the project has no coverage script | none in the spec's non-functional requirements |

## Acceptance Criteria → Tests
```
  ✓ AC-006.1  frontend/src/shared/messages/catalog.test.ts
  ✓ AC-006.2  frontend/src/shared/messages/catalog.test.ts
  ✓ AC-006.3  frontend/src/shared/messages/catalog.test.ts
  ✓ AC-006.4  frontend/src/shared/messages/msg.test.ts
  ✓ AC-006.5  frontend/src/shared/messages/msg.test.ts
  ✓ AC-006.6  frontend/src/shared/messages/msg.test.ts
  ✓ AC-006.7  frontend/src/shared/messages/catalog.test.ts
trace fe-foundation (STORY-006): 7/7 acceptance criteria have a test
  ✓ AC-007.1  frontend/src/shared/ui/StatusTag/StatusTag.test.tsx
  ✓ AC-007.2  frontend/src/shared/ui/StatusTag/StatusTag.test.tsx
  ✓ AC-007.3  frontend/src/shared/ui/StatusTag/StatusTag.test.tsx
trace fe-foundation (STORY-007): 3/3 acceptance criteria have a test

  ✓ AC-003.2  frontend/src/app/router/guards.test.tsx  (incl. "AC-003.2 treats a path in other letter case as the same screen (BUG-005)")
trace fe-foundation (STORY-003): 4/4 acceptance criteria have a test
```

## Failed Tests
None.

## Notes
- jsdom prints `Not implemented: Window's getComputedStyle() method: with pseudo-elements` (antd). This is noise
  only and causes no failure.
- The known first-`findBy` timeout under full-suite load (BUG-002/003/004) did not occur in this run.
