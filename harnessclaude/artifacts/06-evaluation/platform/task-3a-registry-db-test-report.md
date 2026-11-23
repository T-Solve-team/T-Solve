# Test Report: platform — Task 3a: registry-db

**Date:** 2026-11-22
**Commands:** `mvn test -Dtest=PasswordCipherTest` · `mvn test -Dtest=RegistryChangelogIT` (from `backend/`, Docker running) · `bun harnessclaude/hooks/sdd.ts trace platform STORY-001` / `STORY-008`. Typecheck and lint are part of `mvn test` (compile); `spotless:check` was not re-run (developer's verify ran it). No coverage command is configured for the backend and the spec sets no threshold. The full suite is not run by the reviewer; it runs in CI on the PR.

## Results
- `PasswordCipherTest`: Total 15 · Passed 15 · Failed 0 · Skipped 0 · 0.74 s
- `RegistryChangelogIT`: Total 9 · Passed 9 · Failed 0 · Skipped 0 · 16.4 s (shared Testcontainers server, real init script)
- Compile: pass · Developer's `mvn spotless:check test`: 3208 tests, 0 failures (not re-run by the reviewer)

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line coverage | not measured | none in spec |

## Acceptance Criteria -> Tests
```
trace platform (STORY-001): 9/20 acceptance criteria have a test
  ✓ AC-001.15  RegistryChangelogIT
  ✓ AC-001.16  RegistryChangelogIT
  ✓ AC-001.17  RegistryChangelogIT
  ✓ AC-001.18  RegistryChangelogIT
  ✓ AC-001.19  RegistryChangelogIT
  ✓ AC-001.24  PasswordCipherTest
  ✓ AC-001.25  PasswordCipherTest, RegistryChangelogIT
  ✓ AC-001.27  RegistryChangelogIT
  ✓ AC-001.33  RegistryChangelogIT
trace platform (STORY-008): 1/11
  ✓ AC-008.4   RegistryChangelogIT
```
Every criterion of Task 3a is covered. The remaining uncovered criteria belong to later tasks.

## Failures
None.

## Notes
- Edge #13 (missing key refuses startup) and #72 (primary DataSource and tenant Liquibase stay) are tested. Edge #14 (no password in any log line): the cipher and config contain no logging and the tests assert no secret in messages and stack traces; the log-capture test is Task 6's.
- Maven logs hold neither the test password nor the test key.
- First attempt to run the two classes as a detached background job produced no output (the job was cut off); re-run in the foreground, logs in the session scratchpad.
- Weak spots (not failures): the AC-001.33 test can only show that the Registry run does not touch Jobs; the init script's own Registry database (`tsolve_test_registry`) is never asserted (review M2).
