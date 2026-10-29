# Task 4 test report — User roles (KAN-110)

**Module:** identity
**Iteration:** 1
**Status:** evaluating

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
