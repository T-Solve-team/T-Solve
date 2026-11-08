---
module: chatbot
feature: answer-citations
task: 8
verdict: pass
attempt: 1
date: 2026-11-08
reviewer: harnessclaude-reviewer
iteration: 2
---

# Review: chatbot — Task 8: Citations (KAN-180, STORY-006, AC-006.1 – AC-006.4)

## Summary
Commit `88a434b0` adds the package-private record `Citations`. It parses `[n]` markers, where n is a single integer from 1 to 99. It returns the cited kept sources in number order, with no duplicates, and removes the markers of unknown numbers. `ChatbotAnswerServiceImpl` now stores and returns the cleaned text. It answers `NO_SOURCE` when no kept marker is left after removal (AC-005.5 works on the cleaned text). The mapping from source to citation (`TicketSolutionCitation` with five sections and a blank workaround becoming null; `WikiDocumentCitation` with id and title) already existed from Task 7 in `ChatbotSource`. This task now tests it. The full `mvn verify` passes (939 unit, 297 IT, Spotless). Each AC in scope is implemented and checked by a test that names it. I found no CRITICAL issues, only cosmetic edge cases in the text cleanup.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-006.1 – AC-006.4 each checked against the code (Stage 1 table below) |
| Completeness | pass | No MISSING or EXTRA behavior. The diff touches only the files Task 8 lists, plus the service test. AC-006.5 – AC-006.7 are Task 9 |
| Correctness | pass | `mvn -B -Dapi.version=1.44 verify` → `BUILD SUCCESS`; no CRITICAL findings |
| Integration | pass | The contract DTOs are unchanged. The `type` discriminator (`WIKI_DOCUMENT` / `TICKET_SOLUTION`) and the absence of ticket id and title are checked in JSON by `writesCitationTypeDiscriminator_AC_006_1`. The backend has no global Jackson inclusion setting, so a null `workaround` is still written as a key, which the contract requires. `Citations` has one caller (`ChatbotAnswerServiceImpl.java:138`) |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-006.1 cited kept sources only, number order, each with its number | PASS | `Citations.java:15-23,35-37` (a `TreeSet` keeps number order and drops duplicates); `ChatbotAnswerServiceImpl.java:138-142`. Tests: `CitationsTest.listsOnlyCitedSourcesInOrder_AC_006_1`, `listsAdjacentMarkersAndIgnoresOtherForms_AC_006_1` (B5), `ChatbotAnswerServiceImplTest.returnsOnlyCitedSources_AC_006_1`, `ChatbotAnswerControllerTest.writesCitationTypeDiscriminator_AC_006_1` |
| 2 | AC-006.2 unknown markers removed before storing and returning | PASS | `Citations.java:25-34`; `ChatbotAnswerServiceImpl.java:142` stores `cited.text()`. Tests: `CitationsTest.removesUnknownMarkers_AC_006_2` (5 cases), `citesNothingWhenOnlyUnknownMarkers_AC_006_2`, `ChatbotAnswerServiceImplTest.storesAndReturnsTextWithoutUnknownMarkers_AC_006_2` (checks the saved entity text). B1 then `NO_SOURCE`: `answersNoSourceWhenNoMarker_AC_005_5` with `"Unknown [9]"` |
| 3 | AC-006.3 ticket source → `TicketSolutionCitation`, five sections, workaround null when blank, no ticket details | PASS | `ChatbotSource.java:11-29`. Test: `CitationsTest.mapsTicketSourceToFiveSections_AC_006_3` (blank workaround `"  "` → null, B6; the record components are exactly `number, solution` and the five sections). The controller test checks that `ticketId` and `title` are absent from the JSON |
| 4 | AC-006.4 Wiki source → `WikiDocumentCitation` with `wikiDocumentId` and title | PASS | `ChatbotSource.java:32-37`, `numbered` at `:40-47`. Test: `CitationsTest.mapsWikiSourceToDocument_AC_006_4` |

## Traceability
```
  ✓ AC-006.1  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java, backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java, backend/src/test/java/vn/tsolve/chatbot/answer/controller/ChatbotAnswerControllerTest.java
  ✓ AC-006.2  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/ChatbotAnswerServiceImplTest.java, backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java
  ✓ AC-006.3  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java
  ✓ AC-006.4  backend/src/test/java/vn/tsolve/chatbot/answer/service/impl/CitationsTest.java
  ✗ AC-006.5  no test under this module's paths (…)
  ✗ AC-006.6  no test under this module's paths (…)
  ✗ AC-006.7  no test under this module's paths (…)
trace chatbot (STORY-006): 4/7 acceptance criteria have a test
```
The three ✗ rows (AC-006.5 – AC-006.7) are Task 9 (`cited-documents`, pending) and are outside this task's scope. Every criterion of Task 8 is covered.

## Issues

CRITICAL (blocks pass): none.

Informational (does not block). I confirmed each of these with a probe of the compiled `Citations.of`, run outside the repo:

1. `backend/src/main/java/vn/tsolve/chatbot/answer/service/impl/Citations.java:11`: the marker regex also matches array indexing in technical answers. `"Read args[1] first."` counts as a citation of source 1, and `"Use args[9] here [1]."` becomes `"Use args here [1]."`. This follows the design's marker rule and the literal wording of AC-006.1/AC-006.2. However, it can turn a reply with no real citation into `ANSWERED`, which is a weak risk against GBR-36.
   Fix (if wanted): accept a marker only when it is not right after a letter or digit (`(?<![\p{L}\p{N}])\[([1-9]\d?)\]`), and add a `CitationsTest` case.
2. `Citations.java:29-32`: the space cleanup misses three cases. A removed marker at the start of the text or of a line leaves a leading space or punctuation (`"[9] Restart [1]."` → `" Restart [1]."`, `"[9], then [1]."` → `", then [1]."`, `"a\n[9] b"` → `"a\n b"`). `\r` is not in `WORD_END`, so `"a [9]\r\nb"` keeps a trailing space. These are cosmetic only; the spec does not cover whitespace.
   Fix (if wanted): also drop the space after a removed marker when nothing or a newline comes before it, and add `\r` to `WORD_END`.
3. `backend/src/test/java/vn/tsolve/chatbot/answer/controller/ChatbotAnswerControllerTest.java:130-158`: no JSON test checks that a null `workaround` is written as `"workaround": null`. The contract requires the key. It works today because the backend sets no global Jackson inclusion, but nothing would catch a later `non_null` setting.
   Fix (if wanted): set `workaround` to null in this test and assert `jsonPath("$.citations[1].solution.workaround").value(nullValue())` together with `.exists()`.

## Assumptions made
1. **Marker form.** The spec does not define what a "marker `[n]`" is. I accepted the design's rule (builder check 06/11): only `\[([1-9]\d?)\]`. So `[0]`, `[100]`, `[01]`, `[ 1 ]` and `[1, 2]` count as plain text and AC-006.2 does not remove them, even though they would show to the user as unresolved markers. **Open judgment call for the user:** should `[0]` and numbers of 100 or more also be removed as unknown markers?
2. **Whitespace and array indexing.** I treated Informational items 1 and 2 as non-blocking, because the spec only says "remove that marker". **Open judgment call:** is mangling `args[9]` or crediting `args[1]` acceptable for an IT-helpdesk chatbot, or should Task 8 tighten the marker rule now?
3. **Iteration.** I recorded `iteration: 2`. This follows the plan path (`04-plans/chatbot/iteration-2/tasks.md`), the dispatcher, and the earlier Tasks 6–7 review, although `progress.json` `currentIteration` reads `1`.
4. **Coverage.** The backend has no coverage tool and the spec sets no threshold. I report this rather than set a target.

## Test Report
See `task-8-answer-citations-test-report.md`. In brief, from `backend/` in Git Bash:
`export JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2025.1.1.1/jbr"; export PATH="$JAVA_HOME/bin:$PATH"; mvn -B -Dapi.version=1.44 verify`
- `[INFO] Tests run: 939, Failures: 0, Errors: 0, Skipped: 0` (Surefire)
- `[INFO] Tests run: 297, Failures: 0, Errors: 0, Skipped: 0` (Failsafe)
- `spotless:2.43.0:check` → `[INFO] BUILD SUCCESS`, `Total time: 08:17 min`, exit 0
- `CitationsTest` 10/10, `ChatbotAnswerServiceImplTest` 21/21, `ChatbotAnswerControllerTest` 13/13 passed
- Coverage: not measured (no plugin)
