# Test report: chatbot Task 8 (answer-citations)

Command (in `backend/`): `mvn -q test -Dtest='CitationsTest,ChatbotAnswerServiceImplTest,ChatbotAnswerControllerTest,AnswerPromptTest,UnavailableDocumentsControllerTest,NoWikiSourcesTest'`
Date: 2026-11-09, reviewer run, worktree `kan-180-chatbot-citations-dbe540`, uncommitted staged changes on `f629dfd`.

| Class | Tests | Failures | Errors |
|-------|-------|----------|--------|
| CitationsTest | 21 | 0 | 0 |
| ChatbotAnswerServiceImplTest | 20 | 0 | 0 |
| ChatbotAnswerControllerTest | 13 | 0 | 0 |
| AnswerPromptTest | 1 | 0 | 0 |

The same run also passed the Task 9 classes (`UnavailableDocumentsControllerTest` 17, `NoWikiSourcesTest` 1), for a
total of 73 tests.

Trace: `trace chatbot (STORY-006): 7/7 acceptance criteria have a test`.

Not run here: `mvn spotless:check` cannot run in a linked worktree (BUG-130), and the full `mvn verify` was out of
scope for this review. The controller reported both from a separate clone: 949 unit tests passed, and two identity
Keycloak ITs failed on a container startup timeout. Those ITs are unrelated to this change. I did not re-run them.
