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
No numeric coverage threshold or coverage script is configured. Acceptance-criterion tracing covers 7/7 criteria of STORY-004.

## Acceptance Criteria → Tests

| Criterion | Evidence |
|-----------|----------|
| AC-004.1 | SRS columns, 20 visible rows, foreign Workspace exclusion, null-name regression and named Add user action |
| AC-004.2 | Case-insensitive name/email search, Back restoration, combined filters, Department/status controls and 100-character mock validation |
| AC-004.3 | URL-backed paging/sort, 20+4 unique records, totals, descending sign-in sort and invalid-sort regression |
| AC-004.4 | Deferred list request displays Loading state |
| AC-004.5 | Request error/retry and Department lookup error |
| AC-004.6 | No-match search displays MSG04 |
| AC-004.7 | Employee denied at all three users routes; mock APIs reject non-Admin/signed-out access |
| AC-004.8 | Newly explicit 100-character search submission limit; fresh verification pending review repair |

`sdd.ts trace identity STORY-004`: 7/7 acceptance criteria have a test.

## Failures resolved
- BUG-010: decorative plus icon polluted the button accessible name; fixed with aria-hidden.
- BUG-011: `sort=constructor,asc` selected an inherited property and threw `TypeError: value(...).localeCompare is not a function`; fixed with own-property lookup. Regression reproduced the 500 before the fix.
- BUG-012: null Full name displayed Email in the name cell; fixed by retaining an empty name and linking Email. Regression failed before the fix.
- Test-only corrections: use the actual searchbox role, wait for the Department lookup before interaction, and wait for filtered results rather than retained previous query rows. No production requirement was weakened.

## Notes
- jsdom emits its existing `getComputedStyle` pseudo-element warning during antd rendering; the final suite exits 0.
- Actual browser flow and desktop layout verified. Exact 1366 × 768 visual check remains for manual review; the temporary iframe harness did not support MSW correctly and was removed.
- This delivery demonstrates frontend/mock behavior. Production Workspace isolation and backend authorization remain unverified, as scoped in the approved spec.

## Review repair — 2026-10-26
The UC-04 list story is now STORY-004 (AC-004.1–8), following main's preserved STORY-001–003. The verification results above are historical evidence from the original seven-criterion run, with references mapped to the merged spec; they do not establish a fresh pass for the newly explicit AC-004.8 or the UUID contract repair. Rerun the checks and tracing after the review repairs.

## Review repair verification — 2026-10-26

- Scope: STORY-004, AC-004.1–8. Main's auth stories remain unchanged; the new search-limit criterion is explicitly approved.
- Red evidence: new BUG-015 test failed with `aria-invalid=true` after Back. Role-option queries also exposed Ant Design virtual option limitations; all filter selects now expose real options.
- Green focused run: 21/21 tests passed (`vitest run src/features/users --reporter=json`). Includes overlong search retaining input/results/URL and no new request, Back clearing validation, UUID detail href and sign-in sorting.
- Format, lint, typecheck and build: exit 0 on the final source.
- API regeneration: identical SHA-256 file-content set before/after `npm run generate:api`.
- Trace: `sdd.ts trace identity STORY-004` passes 8/8.
- Browser: Admin at viewport override 1366×768; list and filters/actions visible; UUID links; USER24 returns one user; 101-character search shows MSG02; Back restores list with no stale error. Console error list empty. Viewport override reset afterwards.
- Screenshot: [kan-107-user-list-1366x768.jpg](../../assets/identity/kan-107-user-list-1366x768.jpg).
- Independent source review: no blockers for AC-004.1–8, UUID contract, isolation, accessible controls, URL validation or pending state.
- Environment retry: initial full-suite attempt ran only 15 assertions because worker imports lost temporary transformed files (`ENOENT` under the execution environment's temp). Full suite rerun uses a stable ignored cache directory and original test settings; no source/config/test timeout weakened.
- Diagnostic retry with verbose output exposed an existing chatbot test timeout (AC-002.4 conversation opening,15067ms vs15000ms; preceding test14883ms). Logged BUG-016, left Open. The diagnostic run was interrupted and the complete suite is rerun with `--maxWorkers=1`, keeping all assertions and timeouts unchanged. The earlier silent run was interrupted without inferring a source failure from absent progress.

### Local full-suite limitation and baseline comparison
- Full default parallel and one-worker runs were attempted; observed existing chatbot timeouts include AC-002.9, AC-002.3 and AC-002.4. Runs were interrupted after failures; no complete aggregate pass is claimed.
- Fresh isolated comparison: the same two selected tests (AC-002.9 Wiki availability and AC-002.4 conversation opening) fail with15000ms timeouts on both this branch and an unmodified origin/main f22515d frontend snapshot. Each comparison:2 failed,54 unselected. This diagnostic subset is not reported as a full run.
- BUG-016 remains Open; chatbot code/tests and timeout settings are unchanged. CI must supply the full-suite outcome before the review-repair task is marked passing.
- Code repair commit: bcae127. Main integration commit:756adde. Original hooks completed successfully for both. New screenshot is under artifacts/assets/identity/.
