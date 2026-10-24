---
module: chatbot
feature: citations
task: 2
verdict: pass
attempt: 2
date: 2026-10-23
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: chatbot — Task 2: Citations of an answer

## Summary
The citations work as the spec says. Lint and typecheck are clean, and all 36 chatbot tests pass. `trace` gives
19/19 for STORY-001 and STORY-004, and AC-002.9 and AC-002.10 have tests too. The availability contract matches the
spec. A ticket citation cannot show ticket details: the contract has no ticket fields, and `TicketSolution` renders
only the five sections. Nothing is rendered as HTML. The BUG-007 fix keeps MSG01 and MSG02 when the question is
sent. The spec has not changed since it was approved.

The verdict is **fail** for one reason. The AC-002.10 test does not prove its criterion. It makes its assertions
before the failed availability request has answered. A version that toasted on that error, or hid the links, would
still pass it (Issue 1). The code itself is right: `query.data` stays `undefined` on an error, and the app sets no
`onQueryError`, so no toast is shown. The fix is to the test only, and it is small. The other findings do not block.

Reviewed: `git diff 48cb785..b64f804`, outside `harnessclaude/`, and `generated/` only where it matters (the URL
builder sends the ids as repeated `wikiDocumentIds=` params). The spec is the contract. I read the design only for
its Edge Cases rows 9, 10, 19 and 20.

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.7 (citation half) list in number order under the answer | PASS | `ChatAnswer.tsx:23-25`, `CitationList.tsx:22` sorts by `number`; test "AC-001.7 lists the citations under the answer in number order". The mock data is already in order, so the sort itself is not tested (Issue 3). |
| 2 | AC-001.7 (reworded) box emptied only if it still holds the sent question | PASS | `KnowledgeChatbotPage.tsx:30`; test "AC-001.7 keeps a question typed while the answer was written" (`holdAnswer`, clear, type, release, value kept). The Task 1 test "… empties the question box" still passes. |
| 3 | AC-001.8 (citation half) every citation field as plain text | PASS | No `dangerouslySetInnerHTML` or `innerHTML` in the feature (grep). Test "AC-001.8 shows HTML in a citation as plain text": the Wiki title `<b>VPN</b>` and a `<script>` Symptoms are shown as text, and `querySelector('b, script')` is null. |
| 4 | AC-001.9 Wiki: "[n]", title, "· Wiki document" as a link to 11.2, same tab | PASS | `CitationList.tsx:27-37`; the test checks the href `/wiki/documents/9001`, that there is no `target`, and that a click navigates. "[n]" sits next to the link, not inside it (Assumption 3). |
| 5 | AC-001.10 ticket: "[n] Solution from a ticket" + Show, no ticket details | PASS | `CitationList.tsx:48-59`. The test checks the exact text `^[2] Solution from a ticket Show$` and that the item has no `<a>` (edge case 9). |
| 6 | AC-001.11 Show expands five sections inline (Workaround only if present), Hide collapses | PASS | `TicketSolution.tsx:8-26`. Two AC-001.11 tests: Workaround null is left out and present is shown (edge case 10). Hide removes the sections and brings back Show. |
| 7 | AC-002.9 ask availability with the shown Wiki ids; MSG110 in place of the link | PASS | `ConversationThread.tsx:15,38-44`, `useUnavailableDocuments.ts`; the test waits for MSG110 and checks the link is gone (edge case 20). The test does not check the request's ids (Issue 5). |
| 8 | AC-002.10 availability fails → Wiki citations as links | **MISSING (proof)** | The code is right (`useUnavailableDocuments.ts:12`: `data ?? []`; `global-errors.ts:35-38` sets no `onQueryError`). The test asserts before the 500 has arrived (Issue 1). |
| 9 | AC-004.4 mock lists only the asked ids marked unavailable | PASS | `mocks/handlers.ts:40-46`; the test asks `[9001, 9003, 42]` and gets `[9003]`. |
| 10 | AC-004.5 `resetMockData()` returns the initial data | PASS | The mock data is `readonly` and never changes, so `resetMockData()` is empty. The test checks the initial answers and ids after calling it (Assumption 4). |

EXTRA: the `aria-label="Citations"` list and `aria-expanded` on Show/Hide are accessibility additions with no
visible text. The sort in `CitationList.tsx:22` is defensive; the contract already promises number order. The
`400`/`401`/`403` responses and `minItems: 1` on the new path are not in the spec's sketch, but they follow the
other paths of the contract. None of these needs a decision.

### Stage 2 — Quality
- **Contract** (`openapi/tsolve-api.yaml:196-224, 560-568`): `GET /api/v1/chatbot/unavailable-documents`,
  `operationId: listUnavailableCitedDocuments`, tag `chatbot`, query `wikiDocumentIds` int64[] (form, exploded:
  the generated URL builder appends one param per id), and 200 `UnavailableDocuments { wikiDocumentIds: int64[] }`
  required. This matches the spec's Interface Contracts.
- **Ticket details (edge case 9, risk "ticket details leak")**: `TicketSolutionCitation` is
  `{ number, type, solution }`. `TicketSolution` picks the five named fields, so even extra fields from a server
  would never be rendered.
- **Failed availability (edge case 19)**: by reading the code, an error leaves `data` undefined, so every link
  stays. Queries get no toast: `createAppQueryClient` passes only `onUnauthorized` and `onMutationError`. A 401 on
  this query goes to the global sign-out, which is right.
- **BUG-007** (`QuestionBox.tsx:96-97`, `validateTrigger="onChange"` on this one field): `form.submit()` checks
  every rule whatever the trigger, so the AC-001.4 "only white space" test and both AC-001.5 tests still pass.
  `setFieldValue('question', '')` after an answer runs no check, so the cleared box shows no MSG01. The test failed
  before the fix (session log). I did not re-check that, because I may not edit code.
- **Guidelines**: no `any`, `@ts-ignore` or non-null `!` (grep). MSG109 and MSG110 come only from `msg()`. There is
  no raw antd Tag, Table, Modal, Upload or message (lint). Imports are only from `@/shared/api`, `@/shared/messages`,
  `@/shared/routing`, `@/shared/ui`, `@/shared/forms`, antd, icons and react-router (lint, import boundaries). The
  largest component is 60 lines (`CitationList.tsx`, with the private `TicketCitation` below it, which C1 allows),
  and the page is 58 lines (C2). Keys come from the data (`citation.number`, section label; C10). The CSS Module
  uses only antd token variables (U3).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Every criterion of the task works as the spec says (Stage 1 rows 1–10). AC-002.10 I checked by reading the code. |
| Completeness | fail | Every id has a test (19/19, plus AC-002.9/10). The AC-002.10 test does not prove its criterion (Issue 1). Edge cases 9, 10 and 20 are tested; edge case 19 is not really tested. |
| Correctness | pass | 36/36 chatbot tests, lint and typecheck clean. No code defect blocks. |
| Integration | pass | The availability contract matches the spec. The feature uses only fe-foundation's `paths.wikiDocument`, MSG110 and the query client's error handling, and changes no file outside `features/chatbot/` except `openapi/` and `generated/`. |

## Traceability

```
  ✓ AC-001.1 … AC-001.14   frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx
  ✓ AC-004.1 … AC-004.5    frontend/src/features/chatbot/mocks/handlers.test.ts
trace chatbot (STORY-001, STORY-004): 19/19 acceptance criteria have a test

  ✓ AC-002.9   frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx
  ✓ AC-002.10  frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx
trace chatbot (STORY-002): 2/11 — the other 9 are Task 3's (KAN-72)
```

I read every test that names one of this task's ids. All of them check their criterion except AC-002.10 (Issue 1).
The citation halves of AC-001.7 and AC-001.8 that the Task 1 review handed to this task are now tested (Stage 1
rows 1 and 3). The out-of-order case it asked for is not (Issue 3).

## Issues

**CRITICAL** (blocks pass)

1. `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx:347-358`. The test "AC-002.10 keeps the
   Wiki links and shows no message when the availability check fails" does not wait for the check to fail. The Wiki
   link appears when the answer arrives. That is also when the availability query starts, and while it is pending
   `data` is `undefined`, so the link shows anyway. `findByRole('link', …)` returns at that moment, and the three
   assertions run while the 500 is still on its way. So the test would pass for code that showed MSG06 on the error
   or replaced the links once it failed. Edge case 19 ("links shown, no toast") is not proven. Stage 1 counts this
   as MISSING, so the verdict is fail. The code itself is correct.
   Fix: wait until the request has failed, then assert. `renderApp` already returns `queryClient`, so after asking, `await waitFor(() =>
   expect(queryClient.getQueryCache().find({ queryKey: ['/api/v1/chatbot/unavailable-documents'] })?.state.status)
   .toBe('error'))`, then assert the link is there and neither MSG110 nor MSG06 is shown.

**IMPORTANT** (does not block)

2. `frontend/src/features/chatbot/hooks/useUnavailableDocuments.ts:8-12`. When a follow-up answer cites a new Wiki
   document, the sorted id list changes, so the query key changes and `data` goes back to `undefined` until the new
   check answers. During that time a document already shown as MSG110 turns back into a clickable link, then into
   MSG110 again (400 ms in mock mode, one network round trip later). For that moment, AC-002.9 ("MSG110 in place of
   the link") is not met, and the user can open a document they may no longer view.
   Fix: keep the last answer while the new one loads, i.e.
   `{ query: { enabled: wikiDocumentIds.length > 0, placeholderData: keepPreviousData } }` (from
   `@tanstack/react-query`), plus a test that asks "expense" and then "vpn" and checks that MSG110 stays.

**MINOR**

3. `frontend/src/features/chatbot/components/CitationList.tsx:22`. The sort by `number` is not tested. The AC-001.7
   test uses the VPN mock, which is already in order, so it passes without the sort. The Task 1 review asked for
   "also when the server sends them out of order".
   Fix: in the AC-001.7 test, `answerWith({ citations: [citation 2, citation 1] })` and expect `['[1]', '[2]']`.
4. `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx:130-141`. The BUG-007 test waits a fixed
   500 ms for a message that should never come. Guideline 10 T5 says "wait for the UI, never for time". An absence
   check can hardly avoid this, and the test did fail before the fix, so this is a note only.
   Fix (optional): after `user.tab()`, send the empty box (`{Enter}`) and assert that MSG01 shows exactly once.
   That proves the check runs on send, not on blur, with no time-based wait. Or keep it and add a comment that
   names T5.
5. `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx:336-345`. The AC-002.9 test checks MSG110 but
   not the request ("SHALL ask … with their ids"). It also does not check that the ids of every turn of the
   conversation are sent (`citedWikiDocumentIds`, `ConversationThread.tsx:38-44`).
   Fix: record the `wikiDocumentIds` params, as `recordQuestions` does, after two answers ("vpn", then "expense"),
   and expect `['9001', '9003']`.

## Assumptions made
1. **Issue 1 as a blocker (open judgment call).** I failed the task because the AC-002.10 test does not prove its
   criterion. Under the code-review skill, a test that names an AC but does not check it counts as MISSING. The
   code is correct by reading. If the user accepts proof by reading for this criterion, the verdict would be pass
   with Issue 1 as IMPORTANT.
2. **Issue 2 severity (open judgment call).** I rated the MSG110 → link → MSG110 flicker on a follow-up as
   IMPORTANT and not blocking. Each time it happens it is short, and 11.2 has its own access check. If the user reads
   AC-002.9 as "never a link for a listed document while the conversation is shown", it is a defect to fix in this
   task.
3. **"[n] … as a link" (AC-001.9).** I read the sentence as: the citation shows "[n]", the title and "· Wiki
   document", and the citation is a link. In the code, "[n]" sits next to the link and is not part of it, the same
   as in the ticket rows. I passed it. If "[n]" must be inside the link, the fix is a one-line move.
4. **AC-004.5 with immutable data.** `resetMockData()` is empty because the mock answers and the unavailable ids are
   `readonly` and never change. The test is true by construction, and I passed it. It will matter once a handler
   changes the mock data.
5. **BUG-007 vs guideline 09 / FE-05 ("validate on blur").** I accepted the override on this one field
   (`validateTrigger="onChange"`). It has a BUG id comment, it matches AC-001.4 ("when sent") and Figma (g), and
   `AppForm` keeps the default for every other form. The FE Lead may want a line in guideline 09 saying that a chat
   box validates on change.
6. I did not run the full suite, `build`, `format:check` or `generate:api` (the dispatcher said not to). I took
   them from the session log without checking them again.
7. I did not run `sdd.ts feature chatbot citations fixing`. The dispatcher allowed me to write only the review
   files, so the main session must make that status change.

## Test Report
See `task-2-citations-test-report.md`. In summary: `npm run lint` exit 0; `npm run typecheck` exit 0;
`npx vitest run src/features/chatbot` gives 2 files and 36/36 passed in 39.5 s; `sdd.ts trace chatbot STORY-001
STORY-004` gives 19/19 (exit 0); `sdd.ts diff chatbot` says the spec is unchanged since it was approved. No
coverage script exists.

---

# Attempt 2 — 2026-10-23

**Verdict: PASS.** (Attempt 1 above was FAIL. The frontmatter now holds attempt 2.)

## Summary
Reviewed `git diff b64f804..a5fe25b` outside `harnessclaude/`: `4c9f297` (hook + tests) and `a5fe25b` (guideline
09 note, a quick change). The checks were run at HEAD `77e259b`, which adds only harness files. The working tree
was clean. Lint and typecheck are clean. All 38 chatbot tests pass. `trace` gives 19/19 for STORY-001 and STORY-004,
and AC-002.9 and AC-002.10 have tests. The spec has not changed since it was approved.

The critical finding is fixed. The AC-002.10 test now waits until the availability query for `[9003]` is in `error`
and only then asserts. Findings 2, 3 and 5 are fixed, and each new test fails when its behaviour is broken (mutation
runs below). The reason for keeping finding 4 is sound. One gap is left, and it does not block: the "no toast" half of
the AC-002.10 test still does not prove anything (new Issue A1). That half comes from design edge case 19. It is not
in the spec criterion, which says only "SHALL show the Wiki citations as links", and that part is now proven.

## Attempt 1 findings

| # | Sev. | Status | Evidence |
|---|------|--------|----------|
| 1 | CRITICAL | **Resolved** | `KnowledgeChatbotPage.test.tsx:420-435` waits on `queryClient.getQueryState(getListUnavailableCitedDocumentsQueryKey({ wikiDocumentIds: [9003] }))?.status === 'error'` and then uses `getByRole` for the link. Mutant M2 (on error, every cited document shown as MSG110) makes this test **fail**. |
| 2 | IMPORTANT | **Resolved** | `useUnavailableDocuments.ts:12` has `placeholderData: keepPreviousData`. Test `:392-418` holds the follow-up check, waits until `[9001, 9003]` has been requested, and asserts MSG110 is still shown and the expense link is not. Mutant M1 (without `keepPreviousData`) makes it **fail**. |
| 3 | MINOR | **Resolved** | Test `:263-288` sends citation 2 before citation 1. Mutant M4 (the sort removed, `CitationList.tsx:22`) makes it **fail**. |
| 4 | MINOR | **Kept, the reason is sound** | `:146-157`. A check that a message never shows needs some wait, and the test explains this in a comment (`:153`). The alternative I suggested in attempt 1 (send the empty box, expect MSG01 once) would not catch a regression: antd shows one message per field, so a blur-time MSG01 would still show "once". I withdraw it. |
| 5 | MINOR | **Resolved** | `recordAvailabilityChecks` (`:54-63`) records the `wikiDocumentIds` params. `:389` expects `[[9003]]`, and `:411` expects `[[9003], [9001, 9003]]`, so the ids of every turn are sent. Mutant M5 (only the last turn's ids) makes it **fail**. |

Assumption 5 of attempt 1 (BUG-007 and guideline 09): `a5fe25b` adds the exception to
`frontend/guidelines/09-forms-and-validation.md:20-23`. That settles it.

## `keepPreviousData` against AC-002.9 and AC-002.10
I read `@tanstack/query-core` 5.104.1, `queryObserver.ts:611-649`. Placeholder data is used only while
`status === 'pending'` and `data === undefined`.
- **A failed check (AC-002.10):** when the request fails, `status` becomes `error` and the placeholder is dropped.
  `data` is `undefined`, the set is empty, and every Wiki citation is a link. This also holds for a follow-up check
  that fails after an earlier check succeeded: a document shown as MSG110 then becomes a link, which is what
  AC-002.10 says. With `retry` (the app default) the query stays `pending` between tries, so MSG110 stays until the
  last try fails.
- **A stale MSG110:** a document that became available keeps its MSG110 only while the new check is pending. The new
  data then replaces the old, so MSG110 does not stay after the answer. A document cited for the first time is never
  marked from old data, because the earlier list could not contain it. This is the safe direction for AC-002.9.
- **Task 3 note:** after KAN-72 lets the user switch conversations, the hook will carry the last list over from the
  previous conversation for one round trip. Only ids found in both lists are affected, and the effect is the same
  one-round-trip MSG110 as above. Nothing to do now.

## Mutation runs (scratch copy only; the repo was not touched)
I copied `frontend/` (without `node_modules`, which was linked) to the session scratchpad. I applied one mutant at a
time and ran only the relevant tests of `KnowledgeChatbotPage.test.tsx` (`-t`).

| Mutant | Change | Expected to fail | Result |
|--------|--------|------------------|--------|
| M1 | remove `placeholderData: keepPreviousData` | AC-002.9 "keeps MSG110 while it checks again" | failed (caught) |
| M2 | `query.isError ? wikiDocumentIds : data` (MSG110 for every document on error) | AC-002.10 | failed (caught) |
| M3 | `useEffect(() => { if (query.isError) showMessage('MSG06') })` in the hook | AC-002.10 | **passed (not caught)** |
| M4 | `const ordered = citations` (no sort) | AC-001.7 out of order | failed (caught) |
| M5 | `turns.slice(-1)` in `citedWikiDocumentIds` | AC-002.9 every turn | failed (caught) |

For M3, I then added a probe in the copy that waits for MSG06 after the test's own assertions. The probe found
MSG06. So the mutant did show the toast, and the test's synchronous `queryByText(msg('MSG06'))` ran before the
toast was drawn.

## Re-check of the whole feature
Stage 1 rows 1–7, 9 and 10 of attempt 1 are unchanged by `4c9f297`, which touches only the hook and the test file.
Their tests pass again. Row 8 (AC-002.10) is now **PASS**. Stage 2 still holds for the hook. The new import is
`@tanstack/react-query`, which is already a dependency. There is no `any`, `!` or `@ts-ignore`, and the comment gives
the reason. Lint is clean, so import boundaries and banned components are fine.

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | Every criterion of the task works as the spec says. |
| Completeness | pass | 19/19 plus AC-002.9 and AC-002.10. Each of this task's tests now checks its criterion. Edge cases 9, 10 and 20 are tested. For edge case 19, "links shown" is tested and "no toast" is not (A1). |
| Correctness | pass | 38/38 chatbot tests, lint and typecheck are clean. No code defect was found. |
| Integration | pass | No change to the contract or `generated/`. The hook still uses only the generated client and the query client's error handling. |

## Traceability (attempt 2)
```
  ✓ AC-001.1 … AC-001.14   frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx
  ✓ AC-004.1 … AC-004.5    frontend/src/features/chatbot/mocks/handlers.test.ts
trace chatbot (STORY-001, STORY-004): 19/19 acceptance criteria have a test

  ✓ AC-002.9   frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx
  ✓ AC-002.10  frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx
trace chatbot (STORY-002): 2/11 — the other 9 are Task 3's (KAN-72)
```

## Issues (attempt 2)

**CRITICAL**: none.

**IMPORTANT**: none.

**MINOR**

A1. `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx:433`. The test name says "shows no message".
    But `expect(screen.queryByText(msg('MSG06'))).not.toBeInTheDocument()` runs before an antd toast could be drawn,
    so a version that toasts on the error still passes (M3). The code shows no toast: `useUnavailableDocuments` and
    `createAppQueryClient` set no query `onError`. So this is only a weak guard against a regression of design edge
    case 19, not a spec gap.
    Fix (optional): use the same absence wait as the BUG-007 test (`:154-156`):
    `await expect(screen.findByText(msg('MSG06'), {}, { timeout: 500 })).rejects.toThrow()`. Or drop "no message"
    from the test name.
A2. `KnowledgeChatbotPage.test.tsx:54-63`. `recordAvailabilityChecks` adds a `server.events` listener that is never
    removed (`src/test/setup.ts` only calls `resetHandlers`). The listener stays alive for every later test in the
    file. It has no effect today.
    Fix (optional): `afterEach(() => server.events.removeAllListeners())` in the test file.

## Assumptions made (attempt 2)
1. **"No toast" is not part of AC-002.10 (open judgment call).** I passed AC-002.10 on its spec text ("SHALL show the
   Wiki citations as links"), which the test now proves. "No toast" comes only from design edge case 19. Under the
   rule "check against the spec", I rated A1 MINOR. If the user wants every edge case of the design proven, A1
   becomes a blocker. Its fix is one line in the test.
2. **A short stale MSG110 is accepted.** With `keepPreviousData`, a document that became available keeps its MSG110
   until the follow-up check answers. I read this as consistent with AC-002.9. It shows the last known result, and it
   leans to the safe side.
3. **Finding 4 withdrawn.** See the table above.
4. As in attempt 1, I did not run the full suite, `build`, `format:check` or `generate:api` (the dispatcher said not
   to). The session log reports format and lint clean, 437/437 tests, the build passing and no `generate:api` diff.
   I did not check those myself.
5. I did not run `sdd.ts feature chatbot citations passing`, because I may write only the review files. The main
   session must make that status change.

## Test Report (attempt 2)
See `task-2-citations-test-report.md`, attempt 2. `npm run lint` exit 0 · `npm run typecheck` exit 0 ·
`npx vitest run src/features/chatbot`: 2 files, 38/38 passed in 52.8 s · `sdd.ts trace chatbot STORY-001 STORY-004`
19/19 exit 0 · `trace … STORY-002` AC-002.9 and AC-002.10 covered · `sdd.ts diff chatbot`: unchanged since approval.
