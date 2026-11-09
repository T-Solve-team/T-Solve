# Test report: chatbot Task 9 (cited-documents)

Command (in `backend/`): `mvn -q test -Dtest='CitationsTest,ChatbotAnswerServiceImplTest,ChatbotAnswerControllerTest,AnswerPromptTest,UnavailableDocumentsControllerTest,NoWikiSourcesTest'`
Date: 2026-11-09, reviewer run, worktree `kan-180-chatbot-citations-dbe540`, untracked Task 9 files on `f629dfd`.

| Class | Tests | Failures | Errors |
|-------|-------|----------|--------|
| UnavailableDocumentsControllerTest | 17 | 0 | 0 |
| NoWikiSourcesTest | 1 | 0 | 0 |

The `UnavailableDocumentsControllerTest` count is made of three tests:
- `listsWhatThePortReturns_AC_006_5`: 5 runs, one per `Role`.
- `rejectsMissingTooManyOrBadIds_AC_006_6`: 11 runs, 9 refused inputs and the 1 and 50 limits accepted.
- `answers401WithoutSession_AC_006_6`: 1 run.

The same run passed 73 tests in total (Task 8 classes included).

Trace: `trace chatbot (STORY-006): 7/7 acceptance criteria have a test`.

Not run here: `mvn spotless:check` (BUG-130, linked worktree) and the full `mvn verify`. The controller reported both
from a separate clone. I did not re-run them.
