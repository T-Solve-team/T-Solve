---
module: platform
feature: e2e-smoke
task: 24
verdict: pass
attempt: 1
date: 2026-11-14
reviewer: harnessclaude-reviewer
---

# Review: platform — Task 24: e2e-smoke (STORY-015, AC-015.8 – AC-015.12)

## Summary
Commit f6dd0fc4 adds the `e2e/` package (Playwright 1.64.1). All five ACs have tests that check the stated behavior, with the
gaps listed below. Manager evidence (not re-run): live E2E 17/17 on Edge. My own stack-free run
(`E2E_BROWSER_CHANNEL=msedge npx playwright test --config stack-check.config.ts`): 11/11 pass; `npx tsc --noEmit -p .` clean.
Without the channel, two tests fail only because Playwright's Chromium is not installed on this machine (environment, not code).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-015.8 – .12 each covered |
| Completeness | pass | 5/5 ACs traced; README gives the stack and suite commands |
| Correctness | pass | tsc clean, stack-free 11/11, live 17/17 per manager |
| Integration | pass | Real Keycloak + fake Google sign-in, real session cookie, no synthetic cookies |

### Stage 1 – spec compliance
| AC | Status | Evidence |
|----|--------|----------|
| 015.8 | PASS | `tests/suite-config.spec.ts:10` `scripts.e2e == "playwright test"`; README holds `npm run e2e` and `docker compose up -d` |
| 015.9 | PASS | `tests/smoke.spec.ts:11-48` home, Ticket List (waits for a ticket link), Search, Chatbot, User List: heading + content, no `alert` or error banner. Sign-in in `global-setup.ts:13` through Keycloak/fake Google as alice@acme.test |
| 015.10 | PASS | `tests/site-bound-session.spec.ts:4`, see below |
| 015.11 | PASS (weak) | `tests/stack-check.spec.ts:10` names each service; `stack-check.ts:35` throws before seeding and browser launch |
| 015.12 | PASS | `playwright.config.ts:9` html reporter to `playwright-report`, git-ignored; run recorded in `task-24-e2e-smoke-test-report.md` |

## AC-015.10 (asked check)
The test first proves Alice has the real `__Host-TSOLVE_SESSION` cookie and that the same helper (`siteGet`, loopback with the
original `Host`) gets 200 with her identity on ACME. It then sends that same cookie, explicitly forwarded, with
`Host: beta.localhost:8080` (`site-request.ts` takes `sourceOrigin`, so the ACME cookie is sent despite the jar rules).
`beta.localhost` is a registered dev site (`DevWorkspaceLookup.java:27`). The backend answers 401 with no `user`, `workspace` or
BETA identifier. This is the backend's host-binding refusal, not a cookie-jar effect. Weak point: no positive control with a
valid BETA session (minor 2).

## Dev-data fixture (asked check)
`fixtures/dev-data.ts` runs in one transaction. It requires an existing active `alice@acme.test` (creates no account) and adds
only missing rows: `user_role` with `ON CONFLICT DO NOTHING`, department and project `ON CONFLICT DO NOTHING`, one ticket only when
`NOT EXISTS` a Published ticket anywhere. No update or delete. It does add two roles to Alice (additive). Pass.

## Secrets
None committed. DB settings come from the root `.env` (git-ignored); the README names local compose defaults
(`DB_PASSWORD=tsolve`, README lines 18-19), dev values only. `.auth/` (saved session) is git-ignored and untracked.

## Issues
Critical: none.

Important:
1. `tests/stack-check.spec.ts:10-21`, `stack-check.ts:35` — AC-015.11 ("stop before any test and name the service") is proven only through the pure `firstStackFailure` function. The probe loop and the global-setup abort are never run with a service down. Fix: make `services` injectable and add a test that runs `checkLocalStack` against a closed port and asserts the thrown message, or record one manual run with the stack down in the test report.

Minor:
1. `tests/stack-check.spec.ts:23,50` — the two Node-transport tests carry the AC-015.10 label but test the helper; they use the `context` fixture, so the "stack-free" config fails without `npx playwright install` or `E2E_BROWSER_CHANNEL`. Fix: use `request.newContext()` or document the prerequisite in the README.
2. `tests/site-bound-session.spec.ts:19` — no positive control that the BETA site is live. Fix: optional; e.g. assert BETA `/api/v1/auth/sign-in` redirects.
3. `tests/smoke.spec.ts:28` — Search asserts only the heading and the Results region, not result content; AC-015.9 says "show its content". Fix: run a query and assert a result, or accept the region as the content.
4. `tests/suite-config.spec.ts:19` — AC-015.12 test checks configuration, not that a report file appears; the run record covers the second half. Acceptable.

## Traceability
```
  ✓ AC-015.8  e2e/tests/suite-config.spec.ts
  ✓ AC-015.9  e2e/tests/smoke.spec.ts
  ✓ AC-015.10 e2e/tests/site-bound-session.spec.ts, e2e/tests/stack-check.spec.ts
  ✓ AC-015.11 e2e/tests/stack-check.spec.ts
  ✓ AC-015.12 e2e/tests/suite-config.spec.ts
trace platform (STORY-015): 12/12 acceptance criteria have a test
```

## Test Report
Manager: live 17/17 (Edge). Me: stack-free 11/11 with `E2E_BROWSER_CHANNEL=msedge`, `tsc --noEmit` clean. Proposed status: `passing`.
