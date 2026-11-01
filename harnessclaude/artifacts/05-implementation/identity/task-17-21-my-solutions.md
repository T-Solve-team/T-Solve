# Tasks 17-21: My Solutions on My Profile — KAN-238

**Module:** identity
**Story:** STORY-003
**Branch:** feat/KAN-238-identity-profile-my-solutions
**Status:** passing
**Iteration:** 1

## Session 2026-11-01

- Implemented "My Solutions" on /profile against MSW mocks: contract (`getMySolutions`, `getMySolutionSummary`), the table with Latest decision and role gating (Ticket Resolver or Project Manager), six counters, and three filters kept in the URL.
- Spec AC-003.17 to AC-003.23 and design rows 85 to 89.
- Decision FE-45: the Project filter is an exception to the filter design.
- BUG-088 (test race) fixed.
- BUG-087 (ReviewQueuePage, KAN-246) found while working on this; outside this PR, not fixed here.
- Scope note: AC-003.16 (Projects empty state, MSG143) is a follow-up Quick change. Backend Task 19 is deferred until its dependencies exist.
- Not browser-checked: typing dates in the range picker, and the `pm` and `km` users.

## Hand-off

1. Done: identity Tasks 17, 18, 20, 21 (KAN-238) — My Solutions on /profile on MSW mocks (contract, table with Latest decision and role gating, six counters, three URL filters); AC-003.17 to .23; design rows 85 to 89; FE-45 (Project filter exception); BUG-088 fixed; BUG-087 found (KAN-246, outside this PR).
2. Open: AC-003.16 (Projects empty state, MSG143) as a Quick follow-up; backend Task 19 (deferred); BUG-087; typing dates in the range picker and the `pm` and `km` users not browser-checked.
3. Next: the AC-003.16 Quick change, then Task 19 when its dependencies exist.
4. Branch: feat/KAN-238-identity-profile-my-solutions.
5. PR: opened on 1 Nov 2026 right after this commit; squash-merge.
