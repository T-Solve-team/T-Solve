# Test Report: chatbot — Task 8: answer-citations

**Date:** 2026-11-08
**Commit under test:** `default` `f629dfde..88a434b0` (working tree at `9819728d`, which adds only harness files)
**Commands** (Git Bash, from `backend/`):
- `export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.1.1/jbr"; export PATH="$JAVA_HOME/bin:$PATH"; mvn -B -Dapi.version=1.44 verify`
  (compile, Surefire unit tests, Failsafe ITs with Testcontainers, `spotless:check`)
- `bun harnessclaude/hooks/sdd.ts trace chatbot STORY-006` (from the project root)

No separate typecheck or lint step: `javac` compiles in the same run and Spotless is the format and lint gate (`backend/AGENTS.md` §9).

## Results
- Surefire (unit): `Tests run: 939, Failures: 0, Errors: 0, Skipped: 0`
- Failsafe (IT): `Tests run: 297, Failures: 0, Errors: 0, Skipped: 0`
- `spotless:2.43.0:check` ran, then `BUILD SUCCESS`, `Total time: 08:17 min`, exit code 0
- Compile: pass · Spotless: pass

Feature test classes:
- `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.023 s -- in vn.tsolve.chatbot.answer.service.impl.CitationsTest`
- `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.586 s -- in vn.tsolve.chatbot.answer.service.impl.ChatbotAnswerServiceImplTest`
- `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.307 s -- in vn.tsolve.chatbot.answer.controller.ChatbotAnswerControllerTest`
- `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.191 s -- in vn.tsolve.chatbot.ChatbotAnswerTimeoutIT`

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured: the backend has no coverage plugin (no JaCoCo in `pom.xml`) | none in the spec |

## Acceptance Criteria → Tests
```
  ✓ AC-006.1  ChatbotAnswerServiceImplTest.java, CitationsTest.java, ChatbotAnswerControllerTest.java
  ✓ AC-006.2  ChatbotAnswerServiceImplTest.java, CitationsTest.java
  ✓ AC-006.3  CitationsTest.java
  ✓ AC-006.4  CitationsTest.java
  ✗ AC-006.5  no test under this module's paths
  ✗ AC-006.6  no test under this module's paths
  ✗ AC-006.7  no test under this module's paths
trace chatbot (STORY-006): 4/7 acceptance criteria have a test
```
AC-006.5 to AC-006.7 belong to Task 9 (`cited-documents`, still pending), so they are outside this task. All four criteria in Task 8's scope have tests.

## Failures
None.

## Notes
- The stack traces in the log come from tests that exercise error paths on purpose (the logged exceptions). No test failed or was skipped.
- Slow tests (over 5 s): `ChatbotAnswerServiceImplTest` takes 5.6 s for the class (this includes its timeout tests). The other slow classes are ITs elsewhere in the codebase.
- I also ran a probe outside the repo against the compiled classes (a scratch class in the session scratchpad, no repo file touched). It covered `Citations.of` edge cases that the tests skip; see the review's Informational issues.
