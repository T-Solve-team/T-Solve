---
module: chatbot
feature: answer-citations
task: 8
verdict: pass
attempt: 1
date: 2026-11-09
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: chatbot — Task 8: Citations (KAN-180, STORY-006)

## Summary
I reviewed the staged change on `feat/KAN-180-chatbot-citations` against `f629dfd`. The change has not been committed.
The change adds `Citations` (`backend/src/main/java/vn/tsolve/chatbot/answer/service/impl/Citations.java`).
`ChatbotAnswerServiceImpl` now uses it in place of the Task 7 placeholder, which returned every kept source and only
checked for a kept marker. I checked AC-006.1 to AC-006.4 against the spec and the code. I checked the JSON shape
against `frontend/openapi/tsolve-api.yaml` and edge cases B1, B5 and B6. Each criterion has a test named with its id,
and all the tests pass.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-006.1: `Citations.parse` keys the kept sources by number in a `TreeMap`. It returns only the sources whose `[n]` is still in the cleaned text, in number order, and each keeps its number (`Citations.java:15-53`). AC-006.2: markers whose number is not a kept source are removed. The service saves `result.text()` and returns the same text (`ChatbotAnswerServiceImpl.java:135-142`). The test `storesCleanedText_AC_006_2` shows the stored text equals the returned text. AC-006.3: `TicketSolutionCitation` carries the five sections, with a blank or null workaround as `null`. The JSON test shows 3 keys on the citation and 5 on `solution`, with no ticket id or title. AC-006.4: `WikiDocumentCitation(number, wikiDocumentId, title)`. AC-005.5 still holds: no kept marker left after removal gives `NO_SOURCE` with an empty text and no citations. |
| Completeness | pass | AC-006.1 to AC-006.4 are covered. The test names asked for by the task are present: `listsOnlyCitedSourcesInOrder_AC_006_1`, `removesUnknownMarkers_AC_006_2`, `mapsTicketSourceToFiveSections_AC_006_3`, `mapsWikiSourceToDocument_AC_006_4` and `writesCitationTypeDiscriminator_AC_006_1`. There is one extra test, `storesCleanedText_AC_006_2`. Edge cases: B1 (`[9]` removed; nothing left gives `NO_SOURCE`), B5 (`[1][2]` is two markers; repeated `[3]` is cited once; `[1, 2]`, `[ 1 ]`, `[0]`, `[01]` and `[100]` stay as plain text) and B6 (blank or whitespace workaround gives `null`). |
| Correctness | pass | 73 focused tests pass (see the test report). The design's marker form `\[([1-9]\d?)\]` is used as written. Removing a marker also removes one of the two spaces around it, and nothing else. The removal loop always ends: each pass removes at least one marker or leaves the text unchanged. Nothing new is logged. There is no mock data. |
| Integration | pass | The JSON discriminator `type` is `TICKET_SOLUTION` / `WIKI_DOCUMENT`, as the contract's `ChatbotCitation` oneOf says. `CitedSolution` writes `workaround: null` (the key is present). `Citations` is package-private in `service/impl`, so nothing outside the service sees it. No other module is touched. |

## Traceability
```
  ✓ AC-006.1  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java, backend/src/test/java/vn/tsolve/chatbot/answer/controller/ChatbotAnswerControllerTest.java
  ✓ AC-006.2  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java, backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java
  ✓ AC-006.3  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java
  ✓ AC-006.4  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java
trace chatbot (STORY-006): 7/7 acceptance criteria have a test
```

## Issues
None blocking.

Non-blocking notes:
1. `Citations.java:32-38`: the whitespace fix-up applies only to a space on both sides of the marker. `"foo [9]."`
   becomes `"foo ."`, and `"[9] foo [1]"` keeps a leading space. The design asks only for the double-space collapse,
   so this matches the design. Collapse a space before punctuation only if users notice it.
2. `Citations.java:20-42`: the removal runs again until the text stops changing. Text like `"[[9]1]"` becomes `"[1]"`
   and then counts as a citation of source 1. This matches the design: the AC-005.5 check runs on the text after
   removal. It is noted so the behaviour is known. The test case `"x [[9]9] [1]"` covers the same loop.
3. `Citations.java:10`: the marker regex covers only 1 to 99. `ChatbotAnswerServiceImpl` does not cap
   `CHATBOT_SOURCE_LIMIT` at 99, so a limit of 100 or more would make sources 100 and up impossible to cite. The
   default is 5, so this is not a real risk today.
4. `ChatbotAnswerServiceImplTest.keepsTopSourcesNumbered_AC_005_2`: the stubbed model text changed from
   `"Answer [1]"` to `"Answer [1][2][3]"`. This is needed because only cited sources are returned now. It is not a
   weakened assertion.

## Assumptions made
- "Exactly the kept sources whose marker appears in the text" (AC-006.1) is read as: the text after unknown markers
  are removed (AC-006.2). The design says the same.
- Leading and trailing whitespace of the model text is kept as written. AC-006.2 asks only for marker removal, not
  trimming.

## Test Report
See `task-8-answer-citations-test-report.md`. 73 tests passed with 0 failures: `CitationsTest` 21,
`ChatbotAnswerServiceImplTest` 20, `ChatbotAnswerControllerTest` 13 and `AnswerPromptTest` 1, plus the Task 9
classes. I could not run `mvn spotless:check` and the full `mvn verify` in this worktree (BUG-130). They were run in a
separate clone and I did not re-run them.
