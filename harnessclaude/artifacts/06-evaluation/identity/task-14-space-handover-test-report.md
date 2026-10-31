# Test Report: identity — Task 14: space-handover

**Date:** 2026-10-31
**Scope:** STORY-009 / frontend, MSW, OpenAPI proposal

## Results

| Check | Result |
|-------|--------|
| Full frontend suite | 963 passed, 0 failed, 0 skipped; 50 files; ~431s |
| Focused users suite | 154 passed |
| Final status tests after deferred-request assertion | 15 passed |
| Final Space contract tests after foreign Active KM fixture strengthening | 28 passed |
| Format / lint / typecheck / build | Passed; existing TicketListFilters fast-refresh lint warning |
| API generation | Regenerated from YAML; second generation identical SHA-256 paths/hashes |
| Spec check / STORY-009 trace | OK / 9 of 9 |
| Whitespace check | git diff --check passed |

Commands in `frontend/`: `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm run build`, `npm run generate:api`, `npm run test -- --no-cache --maxWorkers=2 --reporter=json --outputFile=node_modules/.cache/kan112-full.json`; focused runs pass the relevant users path with the same options. TEMP/TMP point to ignored `node_modules/.cache/kan112-temp` to avoid Windows temporary-transform path failures. RTK wraps supported commands. Full-suite output was saved before the final test-only strengthening; final affected-file reruns are recorded separately above, not presented as a second full run.

## Acceptance Criteria → Tests

| Criterion | Evidence |
|-----------|----------|
| AC-009.1 | UserStatusPage: Space caller and required multi-Space selection before deactivation |
| AC-009.2 | UserRolesPage: role removal; contract: retain selected Spaces, hand over only removed Spaces |
| AC-009.3 | Contract: other Active same-Workspace KMs, other Department allowed, self/foreign/inactive/wrong role excluded |
| AC-009.4 | Both mutations: missing/duplicate/unrelated/self/foreign/unknown/wrong-role choices; recipient status/role changes after lookup; unchanged snapshots |
| AC-009.5 | UI MSG118 disables completion; both mock callers reject with no writes |
| AC-009.6 | Both UI callers: cancellation, catalog failure, retained choices and successful retry |
| AC-009.7 | Multiple Space transfer once, history, invalid accompanying Project/Space batch changes nothing; UI invalidates cached recipient |
| AC-009.8 | Space cached-refetch/failure guard; status double-click pending request count and disabled choices/completion; shared pending caller guards inspected |
| AC-009.9 | Both callers: confirmed transfers persist through reactivation or valid KM restoration |

Trace command: `bun harnessclaude/hooks/sdd.ts trace identity STORY-009` → `9/9 acceptance criteria have a test`. Complete trace appears in the task session log.

## Browser evidence and defects

At 1366 × 768, UC-07 removed KM after handing over IT Knowledge; the recipient's detail then displayed IT Knowledge and HR Knowledge. UC-06 required recipients for both Spaces, allowed keyboard selection, and ended with the outgoing account Deactivated. Screenshots: `artifacts/assets/identity/kan-112-space-handover.jpg`, `kan-112-deactivated.jpg`.

BUG-071 remains open: later mock User List/Department requests reached Vite's backend proxy and failed ECONNREFUSED. Reload restored the mock list; it resets in-memory fixtures. Root cause/reproducibility not established. Screenshot `kan-112-msw-network-error.jpg`. Production backend integration is not tested or implemented.

One new draft-test assertion failed because it asserted a disabled Cancel button, which the approved criterion does not require. Replaced it with the required disabled recipient control and request-count assertion; final 15 status tests passed. Existing tests, product checks and timeouts were not weakened. JSDOM reports its existing pseudo-element getComputedStyle warning. Three chatbot tests exceed 5 seconds (longest ~13.3s); all passed. No coverage script/threshold is configured for this feature; no coverage percentage is claimed.

## PR #93 CI follow-up — BUG-072

CI run 41197684615 passed 963 assertions but exited 1 with two uncaught `window is not defined` errors. Stack traces pointed to Ant Design's delayed form state after `forms.test.tsx` JSDOM teardown; this is not equivalent to a green workflow. Format/lint/types had passed; build and generation were skipped in that failed CI run.

User approved an exact-file quick change for the shared forms test and bug log. Added controlled timers and an after-cleanup zero-timer assertion: deterministic red was 3 passed / 1 failed, with the no-field-errors regression (BUG-072) reporting `expected 3 to be +0`. Draining only pending timers inside React act before restoring real timers makes all 4 form tests pass while retaining that assertion. No runtime implementation or unhandled-error suppression was added.

Fresh full rerun: `npm run test -- --no-cache --maxWorkers=2 --reporter=json --outputFile=node_modules/.cache/kan112-ci-full.json` → 963 passed / 0 failed / 0 skipped, 50 files, success true and process exit 0; no uncaught timer exceptions. Format, lint, typecheck, build and API generation passed; `git diff --exit-code -- src/shared/api/generated` exited 0. Remote CI confirmation follows the repair push.
