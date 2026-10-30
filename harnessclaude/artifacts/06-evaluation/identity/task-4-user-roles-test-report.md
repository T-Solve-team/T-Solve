# Task 4 test report — User roles (KAN-110)

**Module:** identity
**Iteration:** 1
**Status:** passing

## Evidence
- API/status/session focused:74/74 tests passed.
- Independent UserRolesPage + UserStatusPage:19/19 tests passed.
- BUG-028 red: Admin checkbox remained enabled during deferred update; green after option busy-state fix. Double click sends one request, server failure keeps edits and enables retry.
- BUG-032 red: TR session incorrectly exposed PM-only Department; green role-based projection covers TR/PM/Employee/KM.
- AC trace STORY-007:7/7 criteria have tests.
- Format, lint, typecheck, production build pass. Regeneration hashes identical.
- Browser: Department change requires both Project recipients, validates missing choice and saves; old Project list retained. Direct Admin-role save succeeds; no browser errors.
- Full local suite and final Linux CI results follow. Keep original assertions/timeouts/config; BUG-025 tracks workstation load.

## Integration dependency
PR78 now includes main7296269 via5895f6e, mergeable, all original Linux CI gates pass:42files813/813 tests,187.63s. https://github.com/T-Solve-team/T-Solve/actions/runs/40867797289/job/123426934660

## Full local verification
Full unchanged Windows suite:844/851 pass,7 failures (timeout or late initial/detail renders in chatbot rating401, Review Queue and users). No assertion/time limit/config changed. Source will be pushed while feature remains evaluating for original Linux CI; only full CI success permits passing. Earlier isolated PR78 recheck was stopped after branch switching and is not acceptance evidence. Graphify refreshed:4720nodes9025edges336communities;2 SQL files skipped because tree_sitter_sql is missing, frontend TypeScript indexed.

Source f1a5b412d5df51d504834cd8e007c53e0e0bf416 pushed. Full Linux CI not yet triggered: PR creation is unconfirmed after connector403 and browser policy stop. Feature stays evaluating; no full-pass claim. Final local UI19/19 and API/session74/74 are supporting evidence only.

## Final PR verification — 2026-10-29
PR86 targets main. Original CI40870288080 on ec472d7:44/44 files,851/851 tests,128.01s, all frontend gates passed (format/lint/types/tests/generated-client drift/build). Evidence: https://github.com/T-Solve-team/T-Solve/actions/runs/40870288080/job/123435358184 . PR-title and gitleaks checks40870639932 also passed after the title trailing whitespace was removed. Earlier PR creation limitation is resolved by existing PR86. Feature may pass; module remains implementing for unrelated identity sign-in/backend work. BUG-025 stays open as a workstation load issue.

## PR86 regression repair — BUG033
Commit2e6b483 fixes the session-refresh test lifecycle:2 refetches survived the success toast and overlapped teardown, making following BUG026 load fail with ApiError status0. Reproduced with only AC007.6+BUG026; isolated BUG026 passed. New settled-query assertion initially failed(expected2to0); waitFor settlement yields2/2 pair and9/9 full file. No timeout/assertion weakened, no production/shared/dependency changes. Full static/build/generation checks pass; original PR CI is the final full-suite gate.

## FE Lead follow-up — 2026-10-30
- Source commits: c46c690 (retry/options), 67af2b4 (accessible guards and regression coverage).
- Red: both no-hover guard assertions failed without accessible descriptions. The failed-options test reproduced enabled handover confirmation. The first two status tests failed together while the second passed alone; explicit file-local QueryClient disposal before shared fixture reset makes the pair pass.
- Final users run: `npm --prefix frontend run test -- src/features/users --reporter=json` — 115/115 passed. Includes12 roles and12 status tests; no assertion, timeout or test configuration weakened.
- New coverage: successful Space handover, MSG78 followed by Space handover, unchanged roles during Department-only change, and Cancel after background assignment refetch. Cancel passes with existing keyed form remount; no production Cancel change (BUG037).
- `format:check`, `lint`, `typecheck`, `build`, `generate:api` pass; `git diff --exit-code -- frontend/src/shared/api/generated` passes.
- Browser: own-account MSG112/MSG113 visible without hover, associated with disabled controls; no error logs. Screenshot registered as kan-110-accessible-guards.jpg.
- Graphify refreshed:4725nodes9033edges337communities. Two unrelated SQL files lack tree_sitter_sql; TypeScript indexed.
- Original full PR CI follows the push; this local users result is not a full frontend-suite claim. BUG018/022 remain Open/flaky, BUG025 remains open.
