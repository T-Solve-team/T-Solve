# Test Report: platform — Task 6-1: background-work-bound

**Date:** 2026-11-26
**Range:** `0bbf98ed...5bd6e902` (PR #351, branch `fix/KAN-461-platform-bind-background-work`)
**Commands:** `mvn -B spotless:check test -Dtest=TicketsPublishedListenerTest,EmbeddingCatchUpJobTest,ConversationRetentionJobTest,PreviewCleanupJobTest,TicketEmbeddingServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false` (in `backend/`) · `bun harnessclaude/hooks/sdd.ts trace platform` · `gh pr checks 351`

The reviewer runs only the changed test classes; the full suites ran in GitHub CI on the PR (CLAUDE.local.md §3).

## Results

- Reviewer run: Total 25 · Passed 25 · Failed 0 · Skipped 0 · 14.8 s, `BUILD SUCCESS`
  - `ConversationRetentionJobTest` 5 · `EmbeddingCatchUpJobTest` 3 · `TicketEmbeddingServiceImplTest` 6 ·
    `TicketsPublishedListenerTest` 3 · `PreviewCleanupJobTest` 8
- Format: `spotless:check` pass (ratchet from `origin/main`)
- Developer verify (from the card, uncommitted tree over `0bbf98ed`, since committed unchanged as `4b0dc067` +
  `8cb6f829`; `5bd6e902` changes only `docs/bug-log.md`): `spotless:check verify -Dit.test=ConversationRetentionJobIT`
  BUILD SUCCESS, unit 3342 / 0 failures, IT 3 / 0.
- CI on `5bd6e902`: backend, backend-tests (unit), (it-other), (it-keycloak-a), (it-keycloak-b), changes, frontend,
  gitleaks, pr-title pass; frontend-checks and frontend-tests skipped (no frontend change). it-other first failed on
  Maven Central HTTP 429 and passed on re-run (the BUG-400 pattern, not this change).

## Coverage

| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no coverage run for a targeted review) | spec sets none |

## Acceptance Criteria → Tests

Task 6-1 covers no acceptance criterion; its tests carry `BUG_402`.
- `trace platform`: `61/188 acceptance criteria have a test`, exit 1 (expected).
- `trace platform STORY-002`: `6/25`, exit 1; AC-002.12, AC-002.13, AC-002.14 (the `runIn` contract used here) ✓.
- New tests (8): `TicketsPublishedListenerTest` ×2, `EmbeddingCatchUpJobTest` ×2, `ConversationRetentionJobTest` ×2,
  `PreviewCleanupJobTest` ×2, all `_BUG_402`; constructor-only updates in `TicketEmbeddingServiceImplTest`,
  `ConversationRetentionJobIT` and the existing cases of the four classes.

## Failures

None.

## Notes

- No flaky, skipped or slow (>5 s) test in the reviewer run.
- Not covered here, by plan: an unbound tenant query failing once routing is on (Task 6-2 `TenantRoutingIT`); the
  `@Scheduled` triggers against several real Workspaces; `findActive()` leaving out Suspended Workspaces (platform
  AC-004.29 tests); the staging check on bluefin.
