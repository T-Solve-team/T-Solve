# Test Report: identity — Task 1: user-list

**Date:** 2026-10-25
**Commands:** package-script-equivalent Prettier, ESLint, TypeScript, Vitest, Vite and orval commands recorded in the session log.

## Results
- Full frontend test suite: exit 0. RTK filtered aggregate counts; total/passed/duration are not retained and are not inferred.
- Format: pass. Lint: pass. Typecheck: pass. Production build: pass.
- Regeneration: no generated-content drift, verified with hashes before and after orval.
- Whitespace check: `git diff --check` exits 0.
- Read-only review: no remaining critical source finding.

## Coverage
No numeric coverage threshold or coverage script is configured. Acceptance-criterion tracing covers 7/7 criteria of STORY-001.

## Acceptance Criteria → Tests

| Criterion | Evidence |
|-----------|----------|
| AC-001.1 | SRS columns, 20 visible rows, foreign Workspace exclusion, null-name regression and named Add user action |
| AC-001.2 | Case-insensitive name/email search, Back restoration, combined filters, Department/status controls and 100-character mock validation |
| AC-001.3 | URL-backed paging/sort, 20+4 unique records, totals, descending sign-in sort and invalid-sort regression |
| AC-001.4 | Deferred list request displays Loading state |
| AC-001.5 | Request error/retry and Department lookup error |
| AC-001.6 | No-match search displays MSG04 |
| AC-001.7 | Employee denied at all three users routes; mock APIs reject non-Admin/signed-out access |

`sdd.ts trace identity STORY-001`: 7/7 acceptance criteria have a test.

## Failures resolved
- BUG-010: decorative plus icon polluted the button accessible name; fixed with aria-hidden.
- BUG-011: `sort=constructor,asc` selected an inherited property and threw `TypeError: value(...).localeCompare is not a function`; fixed with own-property lookup. Regression reproduced the 500 before the fix.
- BUG-012: null Full name displayed Email in the name cell; fixed by retaining an empty name and linking Email. Regression failed before the fix.
- Test-only corrections: use the actual searchbox role, wait for the Department lookup before interaction, and wait for filtered results rather than retained previous query rows. No production requirement was weakened.

## Notes
- jsdom emits its existing `getComputedStyle` pseudo-element warning during antd rendering; the final suite exits 0.
- Actual browser flow and desktop layout verified. Exact 1366 × 768 visual check remains for manual review; the temporary iframe harness did not support MSW correctly and was removed.
- This delivery demonstrates frontend/mock behavior. Production Workspace isolation and backend authorization remain unverified, as scoped in the approved spec.
