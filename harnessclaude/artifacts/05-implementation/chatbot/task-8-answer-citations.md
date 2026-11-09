# Task 8: Citations

**Module:** chatbot
**Assignee:** bluebearccc
**Status:** passing
**Iteration:** 2

---

## Session 2026-11-08

### Working on: answer-citations (KAN-180, branch `feat/KAN-180-chatbot-citations` from `main` f629dfde)
- Story STORY-006; AC-006.1 – AC-006.4 (edge cases B1, B5, B6).
- `Citations` (new record in `chatbot/answer/service/impl`): the kept sources whose `[n]` marker is in the text, in
  number order, each once; markers of an unknown number removed from the text. `ChatbotAnswerServiceImpl` stores and
  returns that text; no citation left → `NO_SOURCE` (AC-005.5 runs on the text after removal). `hasKeptMarker` and
  the Task 7 `ponytail:` comment are gone.
- AC-006.3 / AC-006.4 mapping was already in `ChatbotSource` (Task 7); Task 8 adds the tests that name them.
- `keepsTopSourcesNumbered_AC_005_2` now has the model cite `[1] [2] [3]`: with Task 8 an uncited source is no
  longer returned.
- Branch from `main`, not stacked on `feat/KAN-179-chatbot-answers` as tasks.md says: KAN-179 is merged (PR #136).

### Deviation from the design
- Design: "Removing an unknown marker also collapses the double space it leaves". The code also drops the space
  before a removed marker when punctuation or the end of the text follows (`"fix [1] [42]."` → `"fix [1]."`, not
  `"fix [1] ."`). A space then a word after the marker is the design's case (`"a [9] b"` → `"a b"`). A marker at the
  very start of the text can leave one leading space; not handled.

### Tests
- `CitationsTest` (10): `listsOnlyCitedSourcesInOrder_AC_006_1`, `listsAdjacentMarkersAndIgnoresOtherForms_AC_006_1`,
  `removesUnknownMarkers_AC_006_2` (5 cases), `citesNothingWhenOnlyUnknownMarkers_AC_006_2`,
  `mapsTicketSourceToFiveSections_AC_006_3`, `mapsWikiSourceToDocument_AC_006_4`.
- `ChatbotAnswerServiceImplTest`: `returnsOnlyCitedSources_AC_006_1`, `storesAndReturnsTextWithoutUnknownMarkers_AC_006_2`.
- `ChatbotAnswerControllerTest`: `writesCitationTypeDiscriminator_AC_006_1`.

```
trace chatbot (STORY-006): 4/7 acceptance criteria have a test
```
AC-006.5 – AC-006.7 are Task 9 (`cited-documents`).

### Verify
- `mvn -B -Dapi.version=1.44 verify` (backend, JAVA_HOME = IntelliJ jbr 21): BUILD SUCCESS — 939 unit tests,
  297 integration tests, Spotless check.

### Commits
- default@88a434b `feat(chatbot): cite only the sources the answer marks (KAN-180)`

### Next up
Review of Task 8, then Task 9 `cited-documents` on the same branch (KAN-180).

## Session 2026-11-08 (review and fix)

### Review attempt 1: pass
- `06-evaluation/chatbot/task-8-answer-citations-review.md` (harnessclaude-reviewer, on 88a434b): verdict pass, three
  informational findings, `mvn verify` 939 unit + 297 IT.
- Finding 1 fixed by the user's choice: BUG-185, array indexing such as `args[9]` was removed as an unknown marker and
  `args[1]` counted as citing source 1. A `[n]` right after a letter, digit or underscore is no longer a marker
  (lookbehind `(?<![\p{L}\p{N}_])`); `[1][2]` still counts as two. This narrows the design's marker form
  (`\[([1-9]\d?)\]`); design.md is not changed here.
- Findings 2 (a stray leading space when the first word is a removed marker) and 3 (no controller test for
  `"workaround": null`) are left open.
- Test: `CitationsTest` `leavesIndexingAfterAWordAsText_BUG_185` (4 cases; 3 failed before the fix).
- Verify: `mvn -B -Dapi.version=1.44 verify`: BUILD SUCCESS, 943 unit + 297 integration tests, Spotless check.
- Commit: default@cd5cc85 `fix(chatbot): leave code indexing like args[1] out of the citations (KAN-180)`.
- The fix was not reviewed again; the user moved the feature to passing on review attempt 1 plus this verify.

### Next up
Task 9 `cited-documents` on the same branch (KAN-180).
