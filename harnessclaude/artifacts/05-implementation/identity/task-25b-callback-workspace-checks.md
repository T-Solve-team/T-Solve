# Task 25b: the Workspace checks of the callback and the sign-in start — KAN-297

## Session

### Hand-off
- done: Workspace checks of the callback (AC-001.14 order inside `TenantScope.runIn`) and of the sign-in start (AC-001.48); AC-001.74 added in Change Mode; review passed on attempt 2 (APPROVE); full verify 0 failures (1236 unit + 407 IT); feature `callback-workspace-checks` set to passing; 4 commits (2926b55c tests, cd113846 code, d2194b51 build, 0da80d2f fix)
- open: Q5 spec wording fixes after merge (interface table "or not ready", AC-001.42 omits MSG120); Minor 5 repeated lookup by host accepted (Q2 A); non-blank `google_domain` not URL-encoded accepted; platform change requests not sent; `spotless:check` unconfirmed locally, CI decides
- next: Task 26a (KAN-232)
- branch: feat/KAN-297-identity-callback-workspace-checks
- PR: none yet
