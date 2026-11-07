# Test Report: ticket-repository — Task 4: Pull from Jira — service and endpoint

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
- `JiraImportServiceImplTest`: 13 tests, 0 failures. Covers order; snapshot; the 100 cap with MSG35; exactly 100 is
  not MSG35; the 20-page scan cap; MSG01; MSG39, with today as a valid end date; MSG04 with no preview; MSG50 with a
  Failed batch and no preview; no Jira key → MSG50 with no Jira call; 403 with no Jira call; the old preview replaced
  only after Jira answers.
- `JiraImportControllerTest`: 3 tests, 0 failures. 200 with the preview, 502 MSG50, 403 without the PM role.
- `ImportControllerTest` (CSV, touched by the `ImportPreviewResponse.of` change): 13 tests, 0 failures.

## Acceptance Criteria → Tests
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-001` → `12/12`:
- ✓ AC-001.1 … AC-001.10 in `JiraImportServiceImplTest` and/or `JiraImportControllerTest` (AC-001.9 only in the
  controller test)

## Failures
None.

## Notes
- NFR 1 (100 issues with comments within 60 s) has no test. There is no overall deadline across up to 20 pages and
  their retries (review, informational).
- There is no session log `05-implementation/ticket-repository/task-4-jira-pull.md`.
