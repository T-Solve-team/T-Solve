# Task 12: Log out leaves for the identity provider's logoutUrl

**Module:** fe-foundation
**Assignee:** Claude (with ngaidang53)
**Status:** evaluating
**Iteration:** 1
**Jira:** KAN-311 (PR #213)

---

## Session 2026-11-13

### Working on: sign-out-logout-url
- `useSignOut` calls logout, then `resetSession`, then `navigateTo(logoutUrl)` only for an absolute http(s) URL; it returns true or false and never throws. `UserMenu` goes to Login through the router only on false.
- `toLogoutUrl` (`useSession.ts`) rejects non-strings, relative and `//host` values, and any protocol other than `https:` or `http:`.
- Default logout mock answers 200 with an https `logoutUrl` (`mocks/session.ts`).
- Tests: `sign-out.test.tsx` covers AC-008.1 to AC-008.4 (order, one `assign` call, 500 path, rejected URL values).
- Flake fix included: `mock-mode.test.tsx` unmounts the app roots that `main.tsx` creates in `afterEach` (inside `act()`), so no React work runs after jsdom teardown. Every existing assertion is unchanged. Root cause and evidence are in the debug hand-off; a local run cannot prove the CI-only race is gone, so CI shard 1/4 is the proof.

### Review
- Verdict: approve (pass), 0 critical, 3 informational. File: `artifacts/06-evaluation/fe-foundation/task-12-logout-url-review.md`.
- Informational: (1) `http:foo` is accepted by `toLogoutUrl` on an http page; optional tightening with `/^https?:\/\//i`. (2) AC-008.3 test does not assert "no error shown". (3) The true path of `UserMenu` (no router navigation after a page load) is not asserted, by design.

### Verify
- Developer verify and CI at c5ed438a green (the review did not re-run them).

### Commit
- PR #213, branch `feat/KAN-311-fe-foundation-sign-out-logout-url`

### Next up
Merge PR #213; then identity Task 27 / KAN-106 can land. KAN-314 is separate.

### Hand-off
1. Done: Task 12 (`useSignOut` navigates to `logoutUrl`; AC-008.1 to AC-008.4); flake fix of `mock-mode.test.tsx` included.
2. Open: 3 informational review findings (`http:foo` accepted by `toLogoutUrl`, `useSession.ts:123-126`; AC-008.3 test lacks a no-error assertion; UserMenu true path not asserted). The mock-mode flake fix is unproven locally; CI is the proof.
3. Next: merge PR #213, then identity Task 27 / KAN-106. KAN-314 separate.
4. Branch: `feat/KAN-311-fe-foundation-sign-out-logout-url`.
5. PR: #213, ready to merge.
