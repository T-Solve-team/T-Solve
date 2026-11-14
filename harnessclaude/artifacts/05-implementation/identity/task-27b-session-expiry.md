# Task 27b: the expiry sweep and the `EXPIRED` / `DEACTIVATED` events — KAN-312

## Session

### Hand-off
- done: sweep (`SessionExpiryPublisher`, every minute) over the index `tsolve:session:expiry`; one `SessionEnded` per session (`EXPIRED` from the filter's limit path and the sweep, `DEACTIVATED` on the deactivated-account path); the review's findings (b), (c), (d), (e) and the Javadoc of `end` fixed in the same PR; spec review approve at 3e1f67cf and delta review approve at e1ea3a66 (0 critical); CI green at e1ea3a66; feature `session-expiry-events` set to passing; STORY-002 trace 7/8 (AC-002.5 is Task 30b)
- open: finding (a), a re-sign-in in the same browser ends the old Workspace session with no `SessionEnded` (`OidcCallbackHandler.java:166`), recorded in `docs/bug-log.md`, decided as option A (publish `LOGOUT`), follow-up Task 27c = KAN-314; the delta review's four informational findings and a listener-that-throws test are deferred to KAN-314; a Redis Cluster limit of the two-key Lua scripts is now in design.md
- next: PR #211 ready to merge; then Task 27c (KAN-314, Subtask of KAN-106) before Task 29b
- branch: feat/KAN-312-identity-session-expiry
- PR: #211 (head e1ea3a66)

### Commits
- 6a814bae docs (plan and card)
- cb966c00 tests (sweep, events and claim rule)
- 3e1f67cf code (sweep, `EXPIRED`, `DEACTIVATED`)
- 789003c5 tests (review fixes: bad member, `|null` member, cron default, logout with an expired session)
- e1ea3a66 fix (review fixes: per-member handling, Javadoc of `end`)

## Hand-off (Task 27c, KAN-314, 2026-11-13)

- done: Task 27c, the LOGOUT event for a replaced session (re-sign-in publishes SessionEnded LOGOUT; sweep hardening items done); feature `session-replaced-event` passing (11 tests: SessionReplacedEventTest, SessionExpiryPublisherTest, SessionExpiryPublisherIT); BUG-203 Fixed in PR #214
- open: KAN-311 (FE useSignOut), Task 28, Task 29b rebase on 27c, Task 30b
- next: review, merge PR #214
- branch: feat/KAN-314-identity-session-replaced-event
- PR: #214 (commit 2140795a)
