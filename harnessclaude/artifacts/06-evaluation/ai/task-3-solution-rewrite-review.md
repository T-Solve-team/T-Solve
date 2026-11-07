---
module: ai
feature: solution-rewrite
task: 3
verdict: pass
attempt: 1
date: 2026-11-05
reviewer: harnessclaude-reviewer
---

# Review: ai — Task 3: AI rewrite of a Solution

## Summary
Verdict: PASS WITH FIXES (no blocking finding; the fixes below are non-blocking). All 5 criteria (AC-005.1-005.5) have a test that names them and asserts them. Edge cases 23-27 are covered. The three triggers of AC-005.3 are implemented exactly (LLM failure of any `RuntimeException`, a filled section left empty by the answer, total over 10,000 after stripping), the empty-section rule of AC-005.2 forces the output, and AC-005.4 returns the same instance with no LLM call. The exception reaches the client as 503 MSG68 through the existing `GlobalExceptionHandler`. Nothing logs text. No draft test was altered (the test diff has no removed line). The weak spots are: one untested design choice (count after forcing), a stricter-than-spec reading of "wrong shape", repeated per-field code with brace-less `if`s, and the test names.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Filled sections only are sent (stripped, with labels); model answer parsed with the draft's `parseSections` (trailing tokens refused, non-object, missing or non-text field refused); empty-in-input sections forced to ""; blank/null/Unicode-space sections count as empty; all-empty returns the given instance untouched. |
| Completeness | pass | 5/5 criteria traced; edges 23-27 each have a test (see Issues for the weak ones). |
| Correctness | pass | Reviewer ran `mvn -B -o test -Dtest=SolutionAiServiceImplTest,AiRewriteUnavailableExceptionTest`: 38 tests (37 + 1), 0 failures. Manager ran `mvn clean verify` green (546 unit + 35 IT) and spotless. |
| Integration | pass | `SolutionAiService.rewrite(SolutionSections)` matches the spec's Interface Contracts; the exception extends `MessageCodeException` in `ai/exception/`, so the one global handler maps it (status 503, `code` MSG68); `MSG68` exists in the frontend catalog. Collaborators are only `LlmClient` and `ObjectMapper`. |

## Traceability
`sdd.ts trace ai STORY-005`: 5/5 acceptance criteria have a test (AC-005.1 to AC-005.5 in SolutionAiServiceImplTest.java; AC-005.3 also in AiRewriteUnavailableExceptionTest.java).

## Checks done

- AC-005.3 triggers (SolutionAiServiceImpl.java:113-140): the whole call, parse and rule sit in one `try`; `catch (RuntimeException | JsonProcessingException)` covers `AiUnavailableException`, a null or empty answer (`readValue` on null throws `IllegalArgumentException`), and any wrapped failure. A filled-in-input section with an empty (after `strip()`) answer throws. The limit uses `>` on stripped text: exactly 10,000 passes, 10,001 fails (test at line 355 pins both).
- Count after forcing (lines 128-134): the model's text for a section that was empty in the input is discarded first, so it cannot push the total over 10,000. Judged reasonable: the limit protects what the caller gets back, and the caller never sees the discarded text. It is the same total the caller will save. Not pinned by a test (issue 2).
- AC-005.2: forcing is per section by the input's emptiness, not by the answer's; a model that fills an empty section or fills it with text cannot change the result. Test line 298 covers `"  "`, `null` and `"　"` as empty.
- AC-005.4 (lines 95-103): nulls become `""` before `strip()`, so no NPE on a null section; `totalLength == 0` returns `solution` itself (the same instance, per "unchanged"). Test line 369 uses `isSameAs` and asserts the stub saw no call.
- AC-005.5: no repository, no event, no save; fields are only `LlmClient` and `ObjectMapper` (test line 381 pins that by reflection). The given record is immutable, a new record is returned.
- Logging: no logger, no `System.out`; the caught exception is dropped, the thrown one has no cause. Nothing can leak Solution or answer text (NFR 2).
- Exception (AiRewriteUnavailableException.java): extends `MessageCodeException(SERVICE_UNAVAILABLE, "MSG68", Map.of())`. `GlobalExceptionHandler.handleMessageCode` -> `coded()` reads `getCode()`, `getParams()`, `getField()`; it reads `getMessage()` only when the code is null, so the `getMessage()` override returning `null` (lines 15-18) does not change the body. The handler's catch-all ERROR line is not reached for it. The MockMvc test proves 503 and `$.code` = MSG68.
- Draft unchanged: `draft()` body and the draft tests have no diff line removed; `parseSections`, `section`, `totalLength`, `JSON_SCHEMA`, `OUTPUT_LIMIT` are shared with the rewrite without change to behaviour.
- AGENTS.md: constructor injection, no Lombok, no wildcard import, interface plus `@Service` impl, no outbound call in a transaction, exception unchecked in `<module>/exception/` extending a base, never returns null. Spotless passes (manager).

## Issues

1. SolutionAiServiceImpl.java:106-111: low. Four brace-less one-line `if`s and one wrapped brace-less `if` (lines 110-111, the next line looks like the body but a later edit that adds a line is a classic bug). The rest of `backend/src/main` has none (a grep finds only these lines); the draft code beside it uses braces. Spotless does not enforce it. Fix: use braces, or better remove the repetition (issue 4).
2. SolutionAiServiceImplTest.java:355: low (weak test). The count-after-forcing choice (lines 128-134) is pinned by no test: counting the model's text for empty-in-input sections before forcing would pass every test. Also the "stripped text" rule is not pinned (an answer padded with spaces beyond 10,000 raw characters but 10,000 stripped). Fix: add (a) Solution with only Symptoms filled, answer with the five sections and a long text in an empty one so the raw total is over 10,000 but the forced total is not: the call succeeds; (b) an answer whose padded sections total over 10,000 raw and exactly 10,000 stripped succeeds.
3. SolutionAiServiceImpl.java:162-183 (used at 117): low, judgement call. A missing, `null` or non-text field for a section that was EMPTY in the input fails the whole rewrite (`parseSections` returns null if any of the five fields is not text). AC-005.3 names three triggers; this is a fourth that the spec does not name (a wrong-shape answer is treated as an LLM failure, as the draft does and as `AiUnavailableException`'s own Javadoc says). Real models often write `"workaround": null` for a section they were told to leave empty, and that value would be discarded by the forcing anyway. Fix (only if the owner agrees): read a missing/null/non-text field as `""` when the input section is empty and refuse it only when the input section was filled. Otherwise keep and add one line to the spec. Recorded under Assumptions.
4. SolutionAiServiceImpl.java:105-133: low. The five-way "is the input section empty" logic is written three times (labels, emptied-check, forcing), 15 near-identical lines. A helper such as `String keep(String original, String answer)` (returns `""` when the original is empty) plus a loop over the five pairs, or a small `Section(label, original, answer)` list, would shorten it and make a sixth section a one-place change. The draft does not need it. Fix: factor it when touching the file; not a reason to block.
5. SolutionAiServiceImpl.java:126,135,138: low. The code throws `AiRewriteUnavailableException` inside the `try` whose `catch (RuntimeException ...)` catches it and throws a second, identical one. Behaviour is right (it is a `RuntimeException`), the double throw is noise. Fix: keep the check inside, add `catch (AiRewriteUnavailableException e) { throw e; }` first, or set a flag and throw after the `try`. Or leave as is.
6. SolutionAiServiceImplTest.java:298-306: low (weak test). The partial Solution test asserts only `contains("Failure", "Restart")` for the message sent. It does not prove the empty sections are NOT sent (a bug that sends `"Root cause: "` with blank text would pass), and AC-005.1 is "send its FILLED sections". Fix: assert `doesNotContain("Root cause", "Workaround", "Scope")` on the user message.
7. SolutionAiServiceImplTest.java:341-350: low (weak test). Malformed list lacks a literal JSON `null` root, a `null` answer from the client (stub throws `AiUnavailableException` for a null answer, so the `IllegalArgumentException` path is not reached), and a non-text field in a section that is empty in the input (issue 3). Fix: add `"null"`; make the stub able to return null.
8. SolutionAiServiceImplTest.java:271-410: low. AGENTS section 2 and section 8 ask the method name to cite the AC id (`..._AC_005_3`); the ids are only in `@DisplayName` (same as Task 1 finding 7 and Task 2 finding 10). `sdd.ts trace` accepts it. Fix: put the id in the method name.
9. SolutionAiServiceImplTest.java:315,327,336,361,391: low. `vn.tsolve.ai.exception.AiRewriteUnavailableException` is written fully qualified six times; an import fixes it (the class already imports `AiUnavailableException` from the same package).
10. AiRewriteUnavailableException.java:15-18 and AiRewriteUnavailableExceptionTest.java:30: info. The `getMessage()` override returning `null` is not needed: the base message is the code `"MSG68"`, which is not content, and `AiUnavailableException` has no such override. It is harmless (handler and logs do not read it for a coded exception) but it locks a surprising behaviour in a test and makes `toString()` print only the class name. Fix: delete the override and the `getMessage` assertion; keep the `getCause()` assertion.
11. SolutionAiServiceImplTest.java:381-394: info. `rewriteOnlyReturnsValue` proves AC-005.5 by the field list of the class plus record immutability; that is a design lock, not behaviour. Acceptable (there is nothing else to call), and it catches a repository being added.
12. SolutionAiServiceImpl.java:94: info. `rewrite(null)` throws an NPE (caller error, not an AI failure; the spec is silent). The rewrite has no input cap (the draft cuts at 12,000); the spec sets none for a rewrite. A failure (parse, empty section, too long) leaves no log line, so an operator cannot tell why MSG68 appeared; same as Task 2 finding 9. Fix: a WARN with only the reason, never text, if wanted.

Defects to log in docs/bug-log.md: none.

## Weak tests
- `keepsUnfilledSectionsEmpty` (SolutionAiServiceImplTest.java:298): does not assert that empty sections are not sent (issue 6).
- `rewriteOutputLimit` (line 355): does not pin count-after-forcing or stripped length (issue 2).
- `rejectsMalformedRewriteAnswer` (line 334): no `null` root, no `null` client answer, no unfilled-section shape case (issue 7).
- `rewriteOnlyReturnsValue` (line 381): reflection field list only (issue 11).
- `AiRewriteUnavailableExceptionTest`: sound for the 503/MSG68 mapping; its `getMessage` assertion locks an unneeded override (issue 10).

## Test Report
See task-3-solution-rewrite-test-report.md.

## Assumptions made
- A wrong-shape answer (invalid JSON, trailing text, non-object, missing / `null` / non-text field) counts as "the LLM call fails" for AC-005.3, as the draft does. The spec names no separate trigger for it (issue 3 asks the owner whether an unfilled section's bad field should be tolerated).
- The 10,000 limit counts the stripped sections AFTER the empty-in-input ones are forced to empty (the implemented choice; judged reasonable).
- A section blank by `String.strip()` (whitespace including U+3000) counts as empty, consistent with the draft's `isBlank()`.
- "Unchanged" in AC-005.4 is read as the same instance returned (nulls and blanks are not normalised).
- `options.temperature` 0.2 is not sent; accepted by the manager, not flagged.
- PII masking (AGENTS section 4) is out of scope: KAN-138 was cancelled and the spec lists it under Out of Scope.
- Security review skipped: no auth, no storage, no URL; the output is plain text returned to the caller; prompt injection in a Solution can change only the rewrite text, which the writer reads before submitting (GBR-12).
