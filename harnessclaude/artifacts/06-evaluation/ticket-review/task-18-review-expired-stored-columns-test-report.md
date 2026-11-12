# Test Report: ticket-review - Task 18: review-expired-stored-columns

**Date:** 2026-11-10
**Commands:** `mvn verify -Dspotless.check.skip=true -DargLine=-Dapi.version=1.44 -Dit.test=ExpiredTicketsApiIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`

## Results
- ExpiredTicketsApiIT: Total 26 · Passed 26 · Failed 0 · Skipped 0 · 61.5s · BUILD SUCCESS
- Full verify (developer's run, not repeated; no code change after it): 1036 unit, 380 IT, BUILD SUCCESS
- Spotless skipped (BUG-130 in worktrees); the 5 changed Java files were checked separately.

## Acceptance Criteria -> Tests
  ✓ AC-013.1  ExpiredTicketsApiIT.java
  ✓ AC-013.2  ExpiredTicketsApiIT.java
  ✓ AC-013.3  ExpiredTicketsApiIT.java
  ✓ AC-013.4  ExpiredTicketsApiIT.java
  ✓ AC-013.5  SendBackApiIT.java
  ✓ AC-013.6  SendBackApiIT.java
  ✓ AC-013.7  ExpiredTicketsApiIT.java, SendBackApiIT.java
  ✓ AC-013.8  ReviewHistoryApiIT.java
  ✓ AC-013.9  ExpiredTicketsPage.test.tsx
trace ticket-review (STORY-013): 9/9 acceptance criteria have a test

## Failures
None.
