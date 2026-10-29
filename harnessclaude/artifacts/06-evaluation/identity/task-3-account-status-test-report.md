# Test Report: identity — Task 3: account-status

**Date:** 2026-10-27
**Commands:** npm run format:check; npm run lint; npm run typecheck; npm run test -- --reporter=json; npm run build; npm run generate:api; bun harnessclaude/hooks/sdd.ts trace identity STORY-006.

## Results
- Focused API/session45/45 and UI10/10 pass on final source. Local full run:716/720 pass;4 existing UI tests exceed15000ms. All24 new status tests pass. The affected existing files then pass25/25 with one worker and unchanged assertions/timeouts. See BUG-025.
- Format, lint, typecheck and production build pass; generator output hashes are identical before/after regeneration.
- No numeric coverage script/threshold is configured. Acceptance tracing is8/8; no line-coverage claim is inferred.

## Acceptance Criteria → Tests

| Criterion | Evidence |
|-----------|----------|
| AC-006.1 | Reactivation retains UUID, refreshes detail and invalidates cached list |
| AC-006.2 | MSG57 confirmation before ordinary deactivation |
| AC-006.3 | Real last-Admin mock guard and UI catalog error without status change |
| AC-006.4 | Disabled self action and tooltip; forged request rejects |
| AC-006.5 | Eligible same-Department Project Managers / active Knowledge Managers; every recipient required; successful Project and Space UI handovers |
| AC-006.6 | No eligible PM/KM gives MSG59/MSG118 and no request/state mutation |
| AC-006.7 | Account and Project history retained; successful-only Space transfer history BUG-030 |
| AC-006.8 | Invalid/foreign/duplicate/extra assignments reject atomically; session revoked on next request; UI retry/cancel preserve state |

## Regressions and Review
- BUG-029: status-only Space handover incorrectly removed an unrelated recipient role. Delta overlay preserves unrelated central session fields.
- BUG-030: Space handover erased former manager association. Private validated-success history retains old user, Space and recipient; rejected handover adds none.
- BUG-031: Project display name replaced jiraProjectKey in /me. Private immutable fixture tracker key is now preserved and stripped from public UserProject output.
- Full run started before review fixes was deliberately stopped; it is not a passing result. Fresh final run is the acceptance evidence.
- Source27c2c4f was committed/pushed for verification while the feature remains evaluating. Original Linux workflow_dispatch run40511820299 is checking the same source with the unmodified workflow. No Task4 implementation starts before full verification succeeds.
- Final Linux result on27c2c4f:38/38 files,720/720 tests,0 failures/skips,164.49s. Format, lint, types, generated-client drift and build steps all complete successfully under original settings. Evidence: https://github.com/T-Solve-team/T-Solve/actions/runs/40511820299/job/122215501465 . Tracking may pass; BUG-025 remains a local workload-sensitive verification issue.
- Browser1366×768: D3 keyboard recipient selection, successful PM deactivation, original Project/history list still visible; no console errors. Screenshots are registered in the artifact index.
- Graphify refreshed to3916 nodes; SQL parser is unavailable for one unrelated SQL file. Frontend TypeScript relationships are indexed and verified in source.

## PR #78 main integration — 2026-10-29
Merged origin/main7296269, including merged KAN-108, host routing, ticket CSV import and sign-in. Retained both identity plans and event streams. Regenerated client; a second generation has identical file hashes. Format, lint, typecheck and build pass. Full Windows run:806/813 pass;7 failures are timeouts or late initial data in users status, Review Queue and chatbot. Original assertions/timeouts/config unchanged; isolated recheck and remote CI follow. BUG-025 tracks local load. Main owns BUG-022; earlier109 regressions are now BUG-029/030/031 with identical tests and fixes. Independent integration review found no blocker.
