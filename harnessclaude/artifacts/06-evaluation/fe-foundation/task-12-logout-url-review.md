---
module: fe-foundation
feature: logout-url
task: 12
verdict: pass
attempt: 1
date: 2026-11-13
reviewer: harnessclaude-reviewer
---

# Review: fe-foundation — Task 12: Log out leaves for the identity provider's logoutUrl

## Summary
Approve. Each of AC-008.1 to AC-008.4 is implemented in `useSession.ts` and has a test that fails without the behavior. No `javascript:`, `data:`, relative or protocol-relative value reaches `navigateTo`. 0 critical, 3 informational.

## Criteria
| Criterion | Result | Detail |
|---|---|---|
| Functionality | pass | order logout, `resetSession`, `navigateTo` (`useSession.ts:139-150`); `UserMenu.tsx:16-17` goes to Login only on false |
| Completeness | pass | AC-008.1-.4 and the Interface line (200 `{ logoutUrl }`) covered; mock handler updated (`mocks/session.ts:172`) |
| Correctness | pass | developer's verify and CI at c5ed438a green (not re-run, per card) |
| Integration | pass | generated `Logout200` has `logoutUrl: string`; the guard treats the answer as `unknown`, safe against a 204 or odd body |

## Stage 1 - spec compliance
| AC | Status | Evidence |
|---|---|---|
| AC-008.1 | PASS | `sign-out.test.tsx:99` (query, mutation cache, both storages, `tsolve.ui.*` kept), `:118` (order: data still present while the call is in flight) |
| AC-008.2 | PASS | `:174` asserts one `assign(url)` call and that data was already dropped at that moment |
| AC-008.3 | PASS | `:198` 500: signed out, Login, no `/me` refetch, no assign |
| AC-008.4 | PASS | `:230` (204), `:237` (`{}`), `:244` (non-string), `:255` (relative), `:265` (other schemes); no-error asserted in `expectLoginInApp` |

## Security (logoutUrl from the backend)
`toLogoutUrl` (`useSession.ts:121-130`): non-string rejected; `new URL(url)` throws on relative and `//host` values (no base); protocol must be `https:` or `http:`, so `javascript:`, `data:`, `ftp:`, `file:` are rejected. Tests cover all of these and check nothing starting with `javascript:` reaches assign. The host is not checked against a trusted origin; the spec does not ask for it (the URL comes from our own backend).

## Traceability
```
✓ AC-008.1  sign-out.test.tsx, global-errors.test.tsx
✓ AC-008.2  sign-out.test.tsx
✓ AC-008.3  sign-out.test.tsx
✓ AC-008.4  sign-out.test.tsx
```

## Issues
CRITICAL: none.

Informational:
1. `frontend/src/shared/auth/useSession.ts:123-126` - `http:foo` (no slashes) parses as absolute with `new URL`, but on an http page (local dev) `location.assign('http:foo')` resolves against the current host. Not reachable from our backend; to be exact, also require `/^https?:\/\//i.test(url)`.
2. `frontend/src/shared/auth/sign-out.test.tsx:198-228` - the AC-008.3 test does not call `expectNoErrorShown`; the spec does not require "no error" for a failed call, so the question whether MSG07 may show is unspecified.
3. `frontend/src/app/layout/UserMenu.tsx:17` - the true path (no router navigation after a page load) is not asserted; deliberate per the card (stubbed assign does not unload the page).

## Test Report
Not re-run: developer verify and CI green at c5ed438a (card). Read-only checks: `sdd.ts trace fe-foundation` (AC-008.x above) and the full diff of the listed files. The `mock-mode.test.tsx` change (unmount roots in `afterEach`, `vi.doUnmock`) is sound and scoped to the test.
