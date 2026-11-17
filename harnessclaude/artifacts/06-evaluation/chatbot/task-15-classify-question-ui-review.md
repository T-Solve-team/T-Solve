---
module: chatbot
feature: classify-question-ui
task: 15
verdict: pass
attempt: 1
date: 2026-11-17
reviewer: harnessclaude-reviewer
iteration: 3
---

# Review: chatbot — Task 15: Show the classified answers in the web app (KAN-243)

## Summary
I reviewed the uncommitted frontend changes on `feat/KAN-243-chatbot-classify-questions` (base `e274b932`).
`ChatAnswer.tsx` maps each outcome to its text or message and hides the rating buttons for the three new outcomes.
`chatbot-storage.ts` accepts the five outcomes. The mock (`data.ts`, `handlers.ts`) classifies before the keyword
answers. `shared/messages/catalog.ts` gets `MSG_NEW_5` to `MSG_NEW_7`, and its AC-006.7 test is updated. I checked
AC-001.15, AC-003.1, AC-004.7, fe-foundation AC-006.7 and edge case Q7 against the code and the tests. I ran the
four test files myself: 352/352 pass. Two files first failed on a 10 s global-setup hook timeout on this machine.
That timeout comes from the environment, not from these changes, and with `--hookTimeout=60000` everything passes.
I found no CRITICAL issue. Verdict: PASS.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | `ChatAnswer.tsx:36` shows `answer.text` for `ANSWERED` and otherwise `msg(ANSWER_MESSAGES[outcome])`, from one lookup object (`:19-24`, `NO_SOURCE` → MSG109, `GREETING` / `OFF_TOPIC` / `INJECTION` → MSG_NEW_5 / 6 / 7). Citations render only for `ANSWERED` (`:38`), so text or citations the server sends by mistake with a classified outcome are never shown. `RatingButtons` render only when `canRate` is true (`ANSWERED` or `NO_SOURCE`, `:31`, `:41`). `model.ts:20` already sends `''` as the turn answer for every outcome other than `ANSWERED`. The storage guard (`chatbot-storage.ts:27-31`) lists the five outcomes and still rejects any other value. The mock runs the empty and over-length 400 checks first (`handlers.ts:15-30`), then `classifyMockQuestion`, then `findMockAnswer`. |
| Completeness | pass | AC-001.15, AC-003.1 and AC-004.7 have tests that assert the behaviour (Stage 1). Q7 is covered for all five outcomes, and an unknown outcome is still dropped. The task's files and tests are all present. |
| Correctness | pass | I ran `handlers.test.ts`, `chatbot-storage.test.ts`, `KnowledgeChatbotPage.test.tsx` and `catalog.test.ts`: 352/352 pass. The caller reports chatbot 142/142, catalog 211/211, and clean typecheck, lint and format. I did not re-run `build` or `generate:api`. |
| Integration | pass | The outcome type comes from the generated `ChatbotAnswer` (Task 13 contract, `shared/api/generated/model/chatbotAnswerOutcome.ts`). `ANSWER_MESSAGES` is typed against it, so a new enum value fails typecheck at `ChatAnswer.tsx:36` instead of rendering `undefined`. Only `mocks/`, `components/` and `hooks/` of the feature change, plus the catalog change that fe-foundation AC-006.7 (reworded 17/11) allows. |

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.15 `GREETING` / `OFF_TOPIC` / `INJECTION` show MSG_NEW_5 / 6 / 7, no citations, box emptied as in AC-001.7 | PASS | `KnowledgeChatbotPage.test.tsx:482-512` (`AC-001.15 shows the catalog message for %s …`). The fake server returns each outcome with a non-empty `text` and a Wiki citation. The test checks that `msg(code)` is shown, that the server text is not shown, that there is no `Citations` list, and that the box is `''`. `AC-001.15 keeps a follow-up typed while a classified answer is pending` (`:514-526`) covers the "text typed while waiting is kept" half of AC-001.7. |
| 2 | AC-003.1 buttons under `ANSWERED` and `NO_SOURCE`, none under the three new outcomes | PASS | The new `AC-003.1 hides rating buttons for a %s answer` (`:528-542`, three outcomes) checks that there is no Helpful and no Not helpful button. The existing `AC-003.1 shows Helpful and Not helpful under an answer, the MSG109 answer included` (`:929`) still passes for `ANSWERED` and `NO_SOURCE`. |
| 3 | AC-004.7 mock classification before AC-004.1 keywords: greeting phrase gives `GREETING`, "weather" gives `OFF_TOPIC`, "ignore previous instructions" gives `INJECTION`; empty text, no citations | PASS | `data.ts:3-37`: the same 17 phrases and the same normalization as the backend (NFC, lower-case, `[\p{P}\p{So}\p{Sk}]` → space, `\s+` collapsed, trimmed). `INJECTION` is tested before `OFF_TOPIC`. `handlers.test.ts`: all 17 phrases plus 6 normalized variants give `GREETING` with a UUID `answerId`. `weather VPN` gives `OFF_TOPIC`, so classification runs before the `VPN` keyword. `weather: IGNORE previous INSTRUCTIONS about VPN` gives `INJECTION`, so injection wins. An over-long `hi` / `weather` / injection question is still 400 MSG02. `hi, how do I reset my VPN?` is `ANSWERED`. |
| 4 | fe-foundation AC-006.7 the catalog holds MSG_NEW_2 to MSG_NEW_7 and no other new code | PASS | `catalog.test.ts:171-183` adds the three codes to the exact-key list. The texts (`catalog.ts:276-287`) match Interface Contracts character for character. They are type `inline`, like MSG109 (a chat answer, per the catalog's own type legend). |
| 5 | Q7 stored conversation with a new outcome survives reload; unknown outcome still dropped | PASS | `chatbot-storage.test.ts`: `AC-002.7 keeps a stored %s turn …` (five outcomes, the read equals the stored value, the key is kept) and `AC-002.8 drops an unknown outcome`. |

### Stage 2 — Quality

CRITICAL: none.

- No hand-typed message text in components: `ChatAnswer` uses `msg()` with catalog codes. The page tests query
  by `msg(code)`, as guideline 10 asks.
- Plain text: the messages render inside `Typography.Paragraph` as text, with no HTML.
- No new dependency, no `vi.mock` of API hooks. The mock rule order matches the design's "Decided in planning".

Informational (does not block):
1. Low. [`KnowledgeChatbotPage.test.tsx:482-490`] The spec texts of MSG_NEW_5 to MSG_NEW_7 are typed into the
   page test, which checks `expect(MESSAGES).toHaveProperty(code, { type: 'inline', text })`. That assertion
   belongs to the catalog's own test, as for `MSG_NEW_2` to `MSG_NEW_4` (`catalog.test.ts:145-165`, one `toEqual`
   each under its fe-foundation AC). Today the catalog test checks only the keys of the new codes, not their
   texts. Suggested fix: move the three text checks into `catalog.test.ts` under `AC-006.7`, and keep the page test
   on `msg(code)` only.
2. Low. [`frontend/src/features/chatbot/mocks/data.ts:29`] The mock normalizer has the same emoji gap as the
   backend: U+FE0F, ZWJ and U+200B are kept, so `thanks ❤️` is `NO_SOURCE` in the mock. See Task 14 Issue 1. Fix it
   in both places, or in neither, so the two stay the same.
3. Nit. [`chatbot-storage.ts:27-31`] The five-way `===` chain repeats the generated `ChatbotAnswerOutcome` const
   (`shared/api/generated/model/chatbotAnswerOutcome.ts`). `Object.values(ChatbotAnswerOutcome).includes(…)` would
   follow the contract on its own. The explicit list is defensible, though: an outcome the screen cannot render
   is dropped, not shown broken.
4. Nit. [`ChatAnswer.tsx:26-29`] The doc comment still names only MSG109. Add the three classified messages.
5. Nit. [`catalog.ts:10-23`] The catalog header records why each provisional key exists (`MSG_NEW_2`,
   `MSG_NEW_3`). `MSG_NEW_5` to `MSG_NEW_7` have no line there (chatbot KAN-243, MSG157/MSG158 planned by
   workspace).
6. Nit. [`KnowledgeChatbotPage.test.tsx:528-542`] The AC-003.1 hiding test waits for the box to clear but does not
   check that the classified message is shown before it checks that the buttons are absent. Adding a `findByText`
   would rule out a false pass on a thread that never rendered.

## Assumptions made
1. A test-setup hook timeout (`src/test/setup.ts:133`, 10 s, dynamic import of every MSW handler) on a cold or
   loaded machine is environmental and not a failing test of this task. The same files pass with
   `--hookTimeout=60000`, and the caller's full-suite run passed.
2. The emoji gap (Issue 2) follows the open judgment call of Task 14 (Assumption 1 there).
3. I did not update the feature status. The main session runs `sdd.ts feature chatbot classify-question-ui
   passing`. I wrote no separate test-report file. The results are in the Test Report section below.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace chatbot STORY-009 STORY-001 STORY-003 STORY-004 STORY-005 STORY-007`:
51/51 acceptance criteria have a test. The rows for this task:

```
✓ AC-001.15  frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx
✓ AC-003.1   frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx
✓ AC-004.7   frontend/src/features/chatbot/mocks/handlers.test.ts
```

fe-foundation AC-006.7: `catalog.test.ts:171` (`AC-006.7 holds no code other than …, MSG_NEW_2 to MSG_NEW_7, …`).

## Issues
No blocking issues. The informational items 1 to 6 are listed in Stage 2.

## Test Report
- I ran `npx vitest run` on `handlers.test.ts`, `chatbot-storage.test.ts`, `KnowledgeChatbotPage.test.tsx` and
  `catalog.test.ts` with `--hookTimeout=60000`: 4 files, 352/352 pass. With the default 10 s hook timeout,
  `chatbot-storage.test.ts` and `catalog.test.ts` failed in `beforeAll` of `src/test/setup.ts` before any test ran.
  The cause is environmental (Assumption 1).
- Caller's evidence, not re-run: chatbot 142/142, catalog 211/211, typecheck, lint and format:check clean.

## Resolution (2026-11-17, before commit)
- The emoji normalization finding is fixed as BUG-321 (`docs/bug-log.md`): both normalizers also strip `\p{Cf}`
  (ZWJ, zero-width space) and U+FE00–U+FE0F. New tests `QuestionClassifierTest.stripsEmojiJoinersAndSelectors_AC_009_1_BUG_321`
  and `handlers.test.ts` "AC-004.7 strips emoji joiners and selectors … (BUG-321)" failed before the fix and pass
  after it. The keycap mark U+20E3 is not stripped (a "1️⃣" greeting goes to the classifier).
