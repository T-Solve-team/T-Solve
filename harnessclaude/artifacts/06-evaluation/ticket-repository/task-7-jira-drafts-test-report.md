# Test Report: ticket-repository — Task 7: Drafts — save, list, reopen, delete

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
- `ImportDraftServiceImplTest`: 8 tests, 0 failures. Save without validation with MSG41; 403 when the Project is no
  longer managed; own Drafts newest first with a row count; reopen as saved without Jira; delete; delete of a
  non-Draft → 404; another user's, missing or CSV Draft → 404; saving again updates the same Draft and its time.
- `ImportDraftControllerTest`: 5 tests, 0 failures. MSG41, list, 204, 404 MSG04, PM role.
- `ImportDraftRepositoryIT` (Task 2): AC-004.6.

## Acceptance Criteria → Tests
`bun harnessclaude/hooks/sdd.ts trace ticket-repository STORY-004` → `7/7`:
- ✓ AC-004.1, AC-004.2, AC-004.3, AC-004.4, AC-004.5, AC-004.7 (`ImportDraftServiceImplTest`,
  `ImportDraftControllerTest`)
- ✓ AC-004.6 (`ImportDraftRepositoryIT`)

## Failures
None.

## Notes
- There is no session log `05-implementation/ticket-repository/task-7-jira-drafts.md`.
- Not tested: reopening a Draft after editing it without saving again, and reopening a Draft of a Project that became
  inactive (see the review's judgment calls).
