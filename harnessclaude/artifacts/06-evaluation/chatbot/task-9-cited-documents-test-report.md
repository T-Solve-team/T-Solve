# Test Report: chatbot — Task 9: cited-documents

**Date:** 2026-11-08
**Commit under test:** `default` `d657006e..a19abbb2`. The working tree was at `25008093`; the commits after `a19abbb2` change only the bug log (BUG-186) and harness files.
**Commands** (Git Bash):
- From `backend/`: `export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.1.1/jbr"; export PATH="$JAVA_HOME/bin:$PATH"; mvn -B -Dapi.version=1.44 verify`
  (compile, Surefire unit tests, Failsafe ITs with Testcontainers, `spotless:check`)
- From the project root: `bun harnessclaude/hooks/sdd.ts trace chatbot STORY-006`

There is no separate typecheck or lint step. `javac` compiles in the same run, and Spotless is the format and lint gate (`backend/AGENTS.md` §9). The frontend is not touched by this range, so no frontend `verify` was run.

## Results
- Surefire (unit): `Tests run: 953, Failures: 0, Errors: 0, Skipped: 0`
- Failsafe (IT): `Tests run: 297, Failures: 0, Errors: 0, Skipped: 0`
- `spotless:2.43.0:check` ran, then `BUILD SUCCESS`, `Total time: 08:38 min`, exit code 0
- Compile: pass · Spotless: pass
- The known flaky `ReviewQueueEventsIT` (BUG-186) passed on the first run: `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`. No rerun was needed.

Feature test classes:
- `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.015 s -- in vn.tsolve.chatbot.source.controller.UnavailableDocumentsControllerTest` (1 + 1 + 6 parameterized + 1)
- `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0 s -- in vn.tsolve.chatbot.source.service.impl.NoWikiSourcesTest`
- Regression: `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in vn.tsolve.chatbot.answer.controller.ChatbotAnswerControllerTest`

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|
| Line / branch | not measured: the backend has no coverage plugin (no JaCoCo in `pom.xml`) | none in the spec |

I read the code instead. Every branch of `UnavailableDocumentsRequest.of` (null or empty list, a bad id, a short-form id, more than 50 distinct ids, the valid path) and of `UnavailableDocumentsResponse.of` (filtering, order) is exercised by `UnavailableDocumentsControllerTest`.

## Acceptance Criteria → Tests
```
  ✓ AC-006.1  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java, backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java, backend/src/test/java/vn/tsolve/chatbot/answer/controller/ChatbotAnswerControllerTest.java
  ✓ AC-006.2  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java, backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java
  ✓ AC-006.3  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java
  ✓ AC-006.4  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java
  ✓ AC-006.5  backend/src/test/java/vn/tsolve/chatbot/source/controller/UnavailableDocumentsControllerTest.java
  ✓ AC-006.6  backend/src/test/java/vn/tsolve/chatbot/source/controller/UnavailableDocumentsControllerTest.java
  ✓ AC-006.7  backend/src/test/java/vn/tsolve/chatbot/source/service/impl/NoWikiSourcesTest.java
trace chatbot (STORY-006): 7/7 acceptance criteria have a test
```

## Failures
None.

## Notes
- The stack traces and `fallback` log lines in the output come from tests that exercise error paths on purpose. No test failed or was skipped.
- I ran a probe against the compiled `UnavailableDocumentsRequest.of`, using a scratch class in the session scratchpad; no repo file was touched. Results: an uppercase canonical UUID is accepted and normalised to lowercase. The same id in two cases counts once. A leading space, `{…}` braces, a null element and a 37-character id each give `InvalidChatbotRequestException`. The nil UUID is accepted.
