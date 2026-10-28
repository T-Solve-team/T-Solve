---
module: fe-foundation
feature: site-sign-in
task: 8
verdict: pass
attempt: 2
date: 2026-10-26
reviewer: harnessclaude-reviewer
---

# Review: fe-foundation Task 8 (KAN-206): Workspace site, central sign-in, MSG123, background header

## Summary
Pass with nits. Fix 1 closes the attempt-1 major (BUG-019) and the minors 2 to 4. MSG119 and MSG120 now go to
`onSiteUnavailable` before the `handlesOwnErrors` check, for queries and for every mutation. `showMessage` ignores
both codes. `GuestOnly` sends a foreign-session user to `/login?error=MSG123`. `dismissAcknowledgement` closes only the
MSG07 dialog. orval's generated names are `get…QueryOptions` again. No path that can still show MSG119 / MSG120 as a
toast or dialog was found. The QA test files have not changed since before attempt 1, so no existing `it` changed.
The targeted run passes: 19 files, 486 tests. lint, typecheck, format:check and build pass, and `generate:api` changes
no file. Trace is 25/25. What remains is informational and does not block.

## Criteria
| Criterion | Result | Detail |
|---|---|---|
| Functionality | pass | AC-011.5 is now met on every path (edges 23, 24): `query-client.ts:48-51` → `global-errors.ts:68` `endSessionOnce` (once per site-status object); `show.ts:33`. AC-012.5 also holds when Login is opened directly: `guards.tsx:41-43`. Edge 25 still holds: `global-errors.ts:37-44` uses `dismissAcknowledgement('MSG07')` |
| Completeness | pass | All 25 ACs have a test. The two attempt-1 gaps each have a test now: `site-state.own-errors.test.tsx` (BUG-019, 3 rows) and `guards.guest-only.test.tsx` (3 rows) |
| Correctness | pass | 19 files, 486 of 486 tests pass. The QA files have the same counts as the red list (67, 13, 10, 11, 12, 10, 6, 56; DevUserSwitcher 6 is outside this run, and its file is unchanged) |
| Integration | pass | `@/shared/messages` adds `dismissAcknowledgement`. `GlobalErrorHandlers` adds `onSiteUnavailable`. The generated exports are the same as at `HEAD` apart from the new sign-in operation; no `use…QueryOptions` rename is left (0 hits) |

### Fix 1: each point on the card
| Check | Result | Evidence |
|---|---|---|
| MSG119 / MSG120 are routed before `handlesOwnErrors`; the session, queries and mutations are dropped once | PASS | `query-client.ts:48-51`. The 401 branch comes first, which is correct because 401 never carries these codes. `resetSession(…, { signedOut: true })` removes the queries, clears the mutation cache and sets the session to `null`. `resetFor` (a WeakMap of client → status object) keeps the reset to one per status, so there is no listener leak |
| Any other path that shows MSG119 / MSG120 | none found | The only own-error callers are `RejectTicketModal.tsx:38` and `RequestChangesModal.tsx:38`, and both go through `showMessage`, which now returns at once. The chatbot hooks only set `meta`. `ErrorState` (`msg(error.code)`) is in the outlet, and `RootLayout.tsx:27` swaps that for the site page. No direct `app.message`, `notification` or `modal` call exists outside `show.ts`. `useSession` keeps both codes out of `isError` |
| The BUG-019 test fails without the fix | PASS (reasoned) | Without the `handle()` branch, a `handlesOwnErrors` mutation reaches no handler. The session stays, so `waitFor(… toBeNull())` fails, and `clear` is never called (edge-24 row). With the branch but without the `show.ts:33` guard, the test's own `onError` (mutation level, which fires even after unmount) toasts MSG120. Then `findByText` sees two elements, or `getAllByText` gets length 2. This matches the developer's "red 3 of 3" |
| Choice 1: no redirect loop | PASS | The redirect fires only when `error !== 'MSG123'`. On `/login?error=MSG123` it renders the `Outlet`. The "stays where it is" row waits 100 ms and asserts the URL. `returnTo` is dropped, the same as in `RequireAuth` |
| Choice 2: closes only MSG07 | PASS | `show.test.tsx` opens MSG07 and MSG08, dismisses MSG07, and checks that one dialog is left (MSG08) and that only MSG07's promise resolved. The eslint-disable and the raw `Modal` import are gone |
| Choice 3: `get…QueryOptions` names; no diff on a second run | PASS | `orval.config.ts` `useHooks: false`. A hash of every file under `generated/` (the untracked `signInParams.ts` included) is the same before and after `generate:api` |
| No QA `it` changed | PASS | The mtimes of all 10 QA files (02:41–02:51) are earlier than the attempt-1 review (03:24) and every Fix 1 file (03:30 and later). The per-file counts are the same as in the red list |
| `docs/bug-log.md` BUG-019 | PASS | 13 of 13 cells, Status `Fixed`, Jira KAN-206, Test named with the bug id; `Fixed in` is empty until the PR exists (allowed by the card) |

### Choice 2: test seam (the card's question)
Keeping it is acceptable. The no-animation close runs only on the programmatic path, `dismissAcknowledgement`, and
that path runs only when MSG123 takes over and the app is about to leave the screen. An instant close is correct
there in production too, and the OK-click path still animates. Still, the reason for the change is jsdom, and the
comment at `show.ts:65` gives only the production reason. antd 6.6.5 offers a test-only seam, `theme.token.motion:
false` (its `MotionWrapper` passes it to `@rc-component/motion`). It could be set in the test render wrapper in a
later clean-up. Not blocking (Issue 1).

## Traceability
`bun harnessclaude/hooks/sdd.ts trace fe-foundation STORY-011 STORY-012 STORY-001 STORY-010` → "25/25 acceptance
criteria have a test". AC-011.5 is now traced to `show.test.tsx`, `site-state.own-errors.test.tsx` and
`site-state.test.tsx`, and AC-012.5 to `guards.guest-only.test.tsx` and `global-errors.test.tsx`. The full output is in
the test report.

## Issues
Critical: none. Major: none. Minor: none.

Informational:
1. `frontend/src/shared/messages/show.ts:65-66`: production code is shaped by jsdom, because antd's leave animation
   never ends in jsdom. The behaviour is fine as is. Fix (optional): add "(jsdom never ends the leave animation)" to
   the comment, or move the seam to the test wrapper (`theme={{ token: { motion: false } }}`) and drop the `update`.
2. `frontend/src/app/router/site-state.own-errors.test.tsx:29-41`: the BUG-019 test builds a stand-in mutation
   (`approveTickets` with `handlesOwnErrors` and its own `showMessage`) instead of the real
   `useRejectSelectedTickets` / Reject popup or `useAskQuestion`. It tests the same mechanism, and the
   mutation-level `onError` is the stricter case. Fix (optional): add one row through the Reject popup.
3. Attempt-1 items 5 to 7 are still open: the generated `signIn` / `useSignIn` are exported, `returnTo` has no
   2000-character limit, and `design.md` still says `global-errors.ts` navigates for MSG123. None blocks the pass.
   Item 7 belongs in the next plan change.

## Attempt 1 (fail), for the record
- Major 1 (BUG-019): the first MSG119 / MSG120 on a `handlesOwnErrors` mutation left the session and the cache in
  memory, and a screen's own `showMessage` could toast. **Fixed** (see above).
- Minor 2: `GuestOnly` had no MSG123 redirect. **Fixed** (`guards.tsx:41-43`, with a test).
- Minor 3: raw `Modal.destroyAll()` behind an eslint-disable. **Fixed** (`dismissAcknowledgement`).
- Minor 4: orval renamed `get…QueryOptions` to `use…QueryOptions`. **Fixed** (`useHooks: false`; guideline 06 §3).
- Informational 5–7: open (Issue 3 above).

## Security (the Fix 1 changes only)
No new finding. The attempt-1 Low (the previous user's data kept in memory behind the site page) is closed: every
MSG119 / MSG120 now drops the cache. `GuestOnly`'s redirect target is a constant (`/login?error=MSG123`) and is not
built from user input. The full STRIDE pass of attempt 1 still applies: none of the sign-in, `returnTo` or
`google_domain` code changed in Fix 1.

## Assumptions made
- "No existing `it` changed" is judged from file mtimes plus per-file counts and names. The QA files are
  uncommitted, so no pre-fix snapshot exists to diff against.
- Per the card, the full suite was not re-run. The developer's run is accepted (35 files, 669 tests, which is
  658 + 11 new). The 11 new tests match the three new files (3 + 3 + 5).
- The card listed no extra skills, so the security-review skill was not loaded again. The security section above
  covers only the Fix 1 changes.
- Proposed feature status: `passing` (the main session sets it; the card did not ask the reviewer to).

## Test Report
See `task-8-site-sign-in-test-report.md`. Targeted run: `Test Files 19 passed (19)`, `Tests 486 passed (486)`,
36.3 s. format:check, lint, typecheck and build exit 0. `generate:api` changes no file. Trace is 25/25.
