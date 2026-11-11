# Test Report: ticket-repository — Task 6: Finish — Confirm of a Jira preview

**Date:** 2026-11-07
**Commands** (from `backend/`, Git Bash, IntelliJ jbr Java 21 because the machine's JAVA_HOME is broken):
- `export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.1.1/jbr"; mvn -B -Dapi.version=1.44 verify`:
  compile, surefire, failsafe/Testcontainers, and `spotless:check`, which the pom binds to `verify`.
- Formatting, equivalent check: `mvn -q spotless:check -DspotlessFiles=<the 50 Java files changed in
  1586bddd^..423b8f17>`, run on a `git archive 423b8f17` copy with `ratchetFrom` removed.
- Coverage: none configured (no JaCoCo in `backend/pom.xml`).

## Results
- Unit (surefire): `Tests run: 632, Failures: 0, Errors: 0, Skipped: 0`
- Integration (failsafe, PostgreSQL Testcontainers): `Tests run: 48, Failures: 0, Errors: 0, Skipped: 0`
- Compile: pass (part of verify). Backend has no separate lint step; Spotless is the format/lint gate.
- `spotless:check` inside `verify`: **did not run.** `BUILD FAILURE`, exit 1, verbatim:
  `Failed to execute goal com.diffplug.spotless:spotless-maven-plugin:2.43.0:check (default) on project
  t-solve-backend: Execution default of goal com.diffplug.spotless:spotless-maven-plugin:2.43.0:check failed:
  Cannot find git repository in any parent directory`
  Cause: environment. `ratchetFrom origin/main` needs a `.git` directory, and this git worktree has only a `.git`
  file (`gitdir: .../.git/worktrees/KAN-45-jira-import`). Not a code defect.
- Equivalent Spotless check on the 50 changed Java files: exit 0, no violations. The file filter is real: adding
  `ai/client/ChatMessage.java` to it makes the same command fail. With no ratchet, the whole tree has 2 violations,
  both outside this range: `ai/client/ChatMessage.java` and `ai/client/EmbeddingClient.java`, from main, KAN-139
  #121. A ratcheted run would not report them.
- Duration: 2:00 min for the full verify. Slow tests (>5 s) are all outside this module: `SecurityConfigIT` 17 s,
  `AccountRepositoryIT` 8.7 s, `SecurityConfigTest` 6.4 s, `JiraAppSecurityConfigTest` 6.0 s.
- ERROR lines in the log come from tests that inject failures on purpose, and all those tests passed:
  `GlobalExceptionHandler -- Unhandled exception java.lang.IllegalStateException`, `Could not record the Failed
  Import Batch of preview 42`, `TransactionSynchronization.afterCompletion threw exception`.

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured (no coverage tool configured) | none in spec |

## Task tests (all passed)
- `JiraFinishTest`: 17 tests, 0 failures. Covers Published Jira tickets; approve decisions; a Completed batch with
  MSG32; title cut to 200; MSG38 with MSG32; all taken → Completed with 0; MSG40 first, then 403; MSG_NEW_4, also
  under the lock; MSG33 with a Failed batch; a unique violation retried once; a second violation → MSG33; a second
  Finish → 404; the event inside the transaction; the Draft deleted.
- `JiraFinishIT` (PostgreSQL): 4 tests, 0 failures, 4.7 s. A concurrent Ticket is skipped after one retry (Edge 6);
  a double click imports once (AC-003.8); a Draft of an inactive Project is kept with no batch (Edge 11); finishing
  a Draft deletes it with its rows.
- `ImportConfirmServiceImplTest` (CSV): 16 tests, 0 failures. `ImportConfirmIT` (CSV): 6 tests, 0 failures.

## Acceptance Criteria → Tests
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-003` → `10/10`:
- ✓ AC-003.1 … AC-003.10 in `JiraFinishTest`; AC-003.2, AC-003.4, AC-003.8 and AC-003.10 also in `JiraFinishIT`

## Failures
None.

## Notes
- The retry runs on any `DataIntegrityViolationException`, not only on the unique Ticket ID. No test covers a
  violation that is not about uniqueness, such as NOT NULL or a value too long (review, informational).
