# Task 2: Ticket Detail review controls and read-only content

**Module:** ticket-review
**Assignee:** Codex
**Status:** passing
**Iteration:** 1

---

## Session 2026-11-01

### Working on: review-ticket-detail
- Continued the approved second feature after Task 1 and the users quick fix were committed/pushed. User is watching CI for merge head `7031260`; no additional CI polling is part of this work.
- Kept the existing route as a thin keyed adapter to the review feature. Added confirmed read-only Ticket/Solution/Evidence content, Vietnam date formatting, safe API references, and managing-PM Pending review controls. Reused generated detail query, decision hooks, forms, messages and UI kit; no contract, mock, dependency or shared runtime change.
- Dialog lifetime depends on the user's open dialog, not query success: failed refresh retains input and disables submission until a successful eligible refresh. Closing a dialog unmounts its form; navigating to a different id resets local state. Approve has a stable accessible name and duplicate-send guard.
- Six frontend files: review barrel, TicketDetailPage adapter, ReviewTicketDetail, ReviewTicketContent, scoped CSS and detail tests. No new bug was demonstrated; the initial red failures were the intentionally unimplemented placeholder.
- TDD: initial detail tests 0/14 against the placeholder; final focused run 23/23 (19 detail cases and four existing shared decision-form cases). Intermediate test adjustments address modal leave motion through explicit dialog lifetime; no timeouts, skips or assertions were weakened.
- Independent reviewer found no critical/informational issues or concrete acceptance gaps. Root independently inspected all source/tests and ran fresh project-wide checks; final results are in the test report.
- Graphify source graph refreshed to 6,948 nodes / 14,782 edges. Four SQL files remain omitted because `tree_sitter_sql` is unavailable; this frontend slice was verified directly.
- Unrelated research/scalability/graph files and the user's three existing registry rows remain uncommitted. Actual team branch remains `feat/KAN-24-review-decisions`; the Harness default branch record is a known audit warning.

### Blockers
- No technical blocker. Verification is complete; per-feature commit agreement is pending. Backend authorization, durable history, Jira/email delivery and actual Evidence downloads remain outside the approved frontend/MSW scope.

### Debug: BUG-084 queue activation race
- First full Task 2 run: 1,135/1,136; the existing Request Changes test failed to find its dialog. Unchanged focused queue rerun: 10/10. The failed case lasted 8.5 seconds while the real queue poll interval is five seconds.
- The old helper waited for enabled, then awaited userEvent's separate hover/pointer/click steps. A poll can disable eligibility between those steps, correctly suppressing the click. Enabled-check and activation now happen synchronously in one waitFor turn via fireEvent.click; no event is sent while disabled. All business assertions, polling, timeout limits and runtime behavior remain unchanged.
- Existing BUG-084 row updated with the incomplete first fix and current evidence; no duplicate bug id. Focused queue + detail after correction: 29/29. Final full rerun required before commit.
- Mutation check deliberately inverted the detail Pending review condition: both nonpending cases failed on exposed controls. Restored exact source bytes, verified SHA-256 and reran the same two cases green. Final graph refresh after the queue-test correction: 6,969 nodes / 14,800 edges.
- Final full suite after this correction: 1,136/1,136 passed, zero failed/skipped, 148/148 reported suites across 63 files, 581.4 seconds, exit 0. All users tests also pass. Final format/lint/typecheck passed after the test-only correction; the previously verified build and deterministic generated output are unchanged. Browser desktop/narrow/keyboard and decision/reader flows passed; four screenshots registered. No further test repetition is needed without a new source change.

### Next up
- User approved commit and requested a new PR because #100 was merged. Code committed through normal hooks as `default@47c23557c3c71f269a7232c6a0ff7477eedd8ca9` — `feat(review): add ticket detail decisions (KAN-24)`.
- Fresh issue follow-up branch `feat/KAN-24-ticket-review-detail` starts from main `81d64dd` (merged PR #100). Its only incoming changes relative to the tested previous base are the team's BUG-086 renumbering in two test labels, bug log and Task 1 log; retained them all. Scoped review tests on the new base pass 71/71 with zero skipped. No implementation conflict or runtime change was introduced while moving the slice.
- Commit the harness evidence separately, push this new current branch and create its filled PR. User explicitly watches CI; do not wait for CI. Keep unrelated registry/research/scalability/graph changes out of both commits. Preservation stash `1e8fcd7538915a612afc4df8d548bbf49a818e4a` remains as a backup after successful apply.

References: `artifacts/03-modules/ticket-review/spec.md`, `artifacts/04-plans/ticket-review/iteration-1/tasks.md`, `artifacts/06-evaluation/ticket-review/task-2-review-ticket-detail-test-report.md`.

## PR #102 FE Lead follow-up — 2026-11-01
- Review https://github.com/T-Solve-team/T-Solve/pull/102#pullrequestreview-5727559560 requests stable refetch controls, an unmanaged-PM permission test, and resolution of reported users failures.
- Isolated checkout `C:/Users/anime/.codex/worktrees/kan24-pr102-review-fixes/T-Solve` uses the existing PR branch. The original D:/T-Solve checkout has concurrent KAN-158 work and was not edited. Harness initially rejected the new checkout as outside the project; user opened the exact three-file quick change at 10:23:01Z. No spec change or gate bypass.
- BUG-089: separated permission/status render eligibility from temporary refetch submission availability. All three buttons retain their DOM nodes while disabled during refetch. Submission and both popup OK controls remain gated; stale error/status/permission restrictions remain intact.
- Failing-first regression: one toolbar test fails before the correction; two added permission/empty-Evidence tests pass. After correction all 22 detail cases pass. Unmanaged PM is the permitted author of a Pending review Ticket in another Project, so the real mock returns content and the test specifically exercises the manager guard. Removing that guard makes its test fail; exact source bytes restored and the same test passes.
- Simplified conditional popup `isOpen` to true shorthand. Kept Approve's explicit accessible name because AntD's loading icon otherwise changes it; this is already guarded by a pending-approval regression and related shared users regressions.
- Users: 154/154 pass with default workers under both Node22.16.0 and Node24.22.1. Users/FormModal/setup files are identical to main81d64dd; the existing accessible-name fix is merged via #100. No new users fix is claimed. Added recheck evidence and pending tester/environment confirmation to BUG-022/073/075–079 notes; none is marked Closed. Asked for the failing commit/Node/log; user supplied no additional failure evidence.
- Replaced a temporary shared node_modules junction with a real `npm ci --offline` installation (424 packages, unchanged lockfile): the junction made ESLint resolve packages outside its boundary roots and produced275 path errors. Stopped that verification attempt; no lint/config/timeout relaxation. Fresh local-dependency format/lint/typecheck pass, zero errors and the existing one warning. Remaining project verification is recorded in the test report.
- Independent reviewer inspected the exact diff, spec, Graphify and users/main comparison: no critical/informational findings. Browser remains disabled in Work settings; no new real-browser/visual claim is made. Source graph refreshed to6,920 nodes/14,746 edges, with the existing four-SQL-file extractor limitation.
- Final verification on Node24.22.1 and local dependencies: 1,139/1,139 tests, zero failed/skipped, 148/148 reported suites, exit0; reported wall duration1,766 seconds includes an extended tool wait. Format/lint/typecheck/build pass and API generation leaves no generated diff. Users additionally pass154/154 with default workers on clean local dependencies. User's earlier commit/push instruction for this feature/PR remains in force; the exact-file quick approval authorizes this review correction. Commit/push only the PR102 branch, leave CI monitoring to the user.
- Code commit `ce1e400` — `fix(review): preserve detail actions during refetch (KAN-24)` — passed all normal secret/lint/format/commit-message hooks. Closed the three-file quick change after committing. This follow-up does not change module status or approval; its evidence is committed separately before pushing the existing PR branch.
