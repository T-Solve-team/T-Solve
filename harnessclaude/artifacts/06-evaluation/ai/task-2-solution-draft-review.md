---
module: ai
feature: solution-draft
task: 2
verdict: pass
attempt: 1
date: 2026-11-05
reviewer: harnessclaude-reviewer
---

# Review: ai — Task 2: AI draft of a Solution

## Summary
Verdict: PASS WITH FIXES (no blocking finding; the fixes below are non-blocking). All 5 criteria (AC-004.1-004.5) have a test that names them and asserts them. Edge cases 16-21 and 27 are covered. The selection rule of AC-004.4 and the fallback of AC-004.2 are implemented exactly as the spec and the planning decisions say; I found no off-by-one, no NPE and no immutable-list write. The service logs nothing, so no issue or answer text can leak. The weak spots are in the tests: three branches of the rule (stop versus skip, the 10,000 boundary, the exact cut) would survive a small code change.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Five-field JSON schema and prompt; trimmed sections; fallback (Symptoms = trimmed description, Resolution steps = trimmed last comment, rest empty, `isAiDraft` false); triggers: any `RuntimeException` from the client, bad JSON / trailing tokens / non-object / missing or non-text field, a required section blank, trimmed total over 10,000. Workaround may be blank (not in the check). |
| Completeness | pass | 5/5 criteria traced; edges 16-21 and 27 each have a test (see Issues for the weak ones). |
| Correctness | pass | Reviewer ran `mvn test -Dtest=SolutionAiServiceImplTest`: 9 tests, 0 failures. Manager ran `mvn clean verify` green (517 unit + 35 IT) and spotless. |
| Integration | pass | Interface in `service/`, impl in `service/impl/`, records in `dto/`; matches the spec's Interface Contracts (`draft(IssueContent)`, the three records, `comments` oldest first). `rewrite` is Task 3. |

## Traceability
`sdd.ts trace ai STORY-004`: 5/5 acceptance criteria have a test (AC-004.1 to AC-004.5, all in SolutionAiServiceImplTest.java).

## Checks done

- AC-004.4 selection (SolutionAiServiceImpl.java:84-101): the description is cut with `substring(0, min(len, 12000))`; `remaining = 12000 - cut length`; comments are read from the end; a comment with `length > remaining` stops the loop (`break`, not `continue`); an exact fit is kept; the kept list is an own `ArrayList`, then reversed to oldest first. The summary is not counted. A null element becomes `""` (no NPE); a null list becomes empty. Correct.
- AC-004.2 (lines 62-82, 139-145): the fallback is built before the call, so an exception from `chat` or from parsing cannot lose it. `catch (RuntimeException | JsonProcessingException)` covers `AiUnavailableException`, `null` answer (NPE or IllegalArgument), empty answer, wrapped client errors. `FAIL_ON_TRAILING_TOKENS` rejects `{...} junk`; trailing whitespace is accepted. A JSON `null` or a number as a field is not textual, so it gives the fallback. Total counts the trimmed sections.
- AC-004.5: a null or blank description gives `""`; null or empty comments give `""`.
- No logger, no `System.out`, no exception message with text. The caught exception is dropped, not logged.
- AGENTS.md: constructor injection, no Lombok, no wildcard import, records in `dto/`, interface plus `@Service` impl, never returns null, no outbound call in a transaction (none here). Text-block prompts and `String.formatted` are safe: issue text is an argument, so a `%` in it is not interpreted.

## Issues

1. SolutionAiServiceImplTest.java:118-131: medium. The AC-004.4 comment test does not tell "stop at the first comment that does not fit" from "skip it and go on". With the description at 11,990 the room is 10: LAST (4) and NEWER (5) fit, TOO-LONG-12 (11) stops, and OLD (3) would not fit either (1 left), so a `continue` instead of `break` also passes. Fix: make an older comment that would fit after the one that does not (for example room 10, comments `"AB"`, `"TOO-LONG-12"`, `"LAST"`) and assert `"AB"` is absent. Also add an exact-fit comment (length equal to the room) to pin `>` against `>=`, and a case where the newest comment alone is longer than the room (no comment is sent).
2. SolutionAiServiceImplTest.java:105-116: medium. The cut test is only an upper bound on the description. The text is 12,000 x, then `TAIL`; `doesNotContain("TAIL")` passes if the code cuts at 12,001 to 12,003 characters. Fix: end the 12,000 x with a different marker and assert the user message holds exactly 12,000 description characters (for example description = `"x".repeat(11_999) + "Y" + "Z"`, assert contains `"Y"` and not `"Z"`).
3. SolutionAiServiceImplTest.java:61-74: medium. Only 10,001 is tested; a `>=` instead of `>` at SolutionAiServiceImpl.java:75 (10,000 wrongly refused) would pass. Fix: add a test with a total of exactly 10,000 that returns `isAiDraft` true. Same for a blank Workaround: add a test that a blank workaround still gives an AI draft (the spec says it may be empty; the loop at lines 51-58 skips it).
4. SolutionAiServiceImplTest.java:92-103: medium. AC-004.3 (and the first trigger of AC-004.2, "the LLM call fails") assert only `isAiDraft` false. The fallback content (Symptoms = description, Resolution steps = last comment) after an LLM failure with a filled issue is asserted nowhere; the other tests use a working client or an empty issue. Also only `AiUnavailableException` is thrown; the code catches every `RuntimeException`. Fix: in this test assert `sections` equals `("Description", "", "Last", "", "")` and add one case with `new IllegalStateException()` thrown by the stub.
5. SolutionAiServiceImplTest.java:76-90: low. Missing malformed cases that the code handles: trailing text after the object (the manager asked for it), a JSON `null` field, a non-object root (`[]`, `null`), a blank or empty answer, an answer `null` from the client. Fix: add them to the answer list; they are one line each.
6. SolutionAiServiceImplTest.java:133-145: low. AC-004.5 is tested only with description and comments absent together. The two independent cases are not isolated: description present and no comments (Resolution steps empty, Symptoms kept), and comments present and no description (Symptoms empty, Resolution steps kept); also a null element in the comments list. Fix: add the three cases.
7. SolutionAiServiceImpl.java:129,144,142,145: low. `trim()` removes only characters up to U+0020, but `isBlank()` uses Unicode white space. A description of only an ideographic space (U+3000) stays non-empty in the fallback Symptoms (AC-004.5 expects empty), while the same text as an AI section is blank. Fix: use `strip()` in `section` and in `fallback` so both sides use the same rule. (A non-breaking space U+00A0 is not white space for either; leave it.)
8. SolutionAiServiceImpl.java:86: low. The cut can split a surrogate pair (an emoji at position 12,000), leaving a lone surrogate in the prompt. The JSON encoder may refuse it; the failure is a `RuntimeException`, so the result is the fallback, never a throw. Fix: if `Character.isHighSurrogate(description.charAt(end - 1))`, cut one less (or use `offsetByCodePoints`; the spec counts "characters" and the code counts UTF-16 units).
9. SolutionAiServiceImpl.java:79-81: low. A wrong-shape answer, a rule failure, or a client failure all end in the fallback with no log line from the service. The client logs its own failures (AiUnavailableException path) but not a parse failure or a too-long answer, so an operator cannot see why MSG67 appears. Fix: one WARN naming only the reason (`invalid-json`, `blank-section`, `too-long`, the exception class), never the answer or issue text.
10. SolutionAiServiceImplTest.java (all methods): low. AGENTS section 2 asks the method name to cite the AC id (`..._AC_004_2`); the ids are only in `@DisplayName` (same as Task 1 finding 7). `sdd.ts trace` accepts it. Fix: put the id in the method name.
11. SolutionAiServiceImpl.java:98-100: info. Comments are joined with a bare newline, with no marker between comments; a multi-line comment cannot be told from two comments in the prompt. Not a spec point; add a separator only if the draft quality needs it.
12. SolutionAiServiceImpl.java:61: info. `draft(null)` throws an NPE (caller error, not an AI failure); AC-004.3 does not cover it. The fallback sections are not cut to 12,000 (Symptoms is the whole description, as the spec says); the caller must bound it when it saves.
13. SolutionAiServiceImplTest.java:147-162: info. Edge 27 checks only that the text survives `ObjectMapper` as a Java `String`; the UTF-8 bytes on the wire belong to the client tests of Task 1. Acceptable.

Defects to log in docs/bug-log.md: none (issues 7 and 8 are low and found in review before release; the reviewer cannot edit the log, so the developer logs them if fixed as bugs).

## Test Report
See task-2-solution-draft-test-report.md.

## Assumptions made
- "The issue's description" and "last comment" in the fallback are trimmed (tests assert trimmed text). The spec does not say; trimming matches the AI sections, which are trimmed too.
- The 10,000-character limit counts the trimmed sections (the same text the caller would get).
- A blank (whitespace only) description or comment counts as "no description" or "no comment" (AC-004.5).
- The 12,000 characters are UTF-16 units (`String.length()`), the summary is neither counted nor cut, and a single newest comment longer than the room stops the selection with no comment sent (planning decision: first one that does not fit stops).
- `options.temperature` 0.2 is not sent; accepted by the manager (ponytail comment at line 67), not flagged.
- Security review skipped: no auth, no storage, no URL; the output is plain text shown to the user for editing. Prompt injection in an issue can change only the draft text, which the user reviews before saving.
