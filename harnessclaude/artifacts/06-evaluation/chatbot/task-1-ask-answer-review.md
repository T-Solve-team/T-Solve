---
module: chatbot
feature: ask-answer
task: 1
verdict: pass
attempt: 1
date: 2026-10-23
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: chatbot — Task 1: Ask a question and show the answer

## Summary
The ask path works as the spec says, and each of this task's criteria has a test that names it and checks it. The
criteria are AC-001.1–8, AC-001.12–14 and AC-004.1–3. Lint and typecheck are clean, and the 22 feature tests pass.
The contract matches the spec's Interface Contracts. A ticket citation has no field that could carry ticket details.
Answer text is shown only as React text. The commit changes nothing in `app/` or `shared/` outside `generated/`.
The chatbot spec has not changed since it was approved (`sdd.ts diff chatbot`). I found no CRITICAL issue. Two
IMPORTANT points are judgment calls for the user: the 401 exception to AC-001.13, and the citation parts of
AC-001.7/8 that are left to Task 2. The four MINOR points do not block.

Reviewed: `git show a5c323a` (outside `generated/`), plus the fe-foundation change request `507955c`
(`route-table.test.tsx` row 12.1 removed). The spec is the contract. I read the design only for its Edge Cases rows
1–8, 11 and 21.

### Stage 1 — Spec compliance

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | AC-001.1 thread, box, Send on `/chatbot` | PASS | `KnowledgeChatbotPage.tsx:41-47`; test "AC-001.1 … to an Employee" / "… Project Manager who is also a Knowledge Manager" (edge case 21) |
| 2 | AC-001.2 Enter sends, Shift+Enter new line | PASS | `QuestionBox.tsx:21-25` (it also skips IME composition); test AC-001.2 checks the value `VPN\nfrom home` and that one request is sent |
| 3 | AC-001.3 Send sends | PASS | `QuestionBox.tsx` `htmlType="submit"`; test AC-001.3 checks the body `{ question, history: [] }` |
| 4 | AC-001.4 blank → MSG01, not sent | PASS | `rules.required` has `whitespace: true`; test AC-001.4 (edge case 1) |
| 5 | AC-001.5 count 1,000; >1,000 → MSG02, not sent | PASS | `count={{ show: true, max: 1000 }}` with no native `maxLength`, plus `rules.maxLength`; tests check "0 / 1000", 1,001 → MSG02 and 1,000 → sent (edge case 2) |
| 6 | AC-001.6 POST with history; pending question and loading answer; Send disabled | PASS | `KnowledgeChatbotPage.tsx:16-38`, `ConversationThread.tsx:21-28`; three AC-001.6 tests: double Enter sends once (edge case 4), `aria-busy` and Send disabled then enabled, follow-up sends history |
| 7 | AC-001.7 ANSWERED text with markers; box emptied | PASS (text part) | `ChatAnswer.tsx:16`; test AC-001.7. The "list of its citations in number order" part is not rendered yet. It is Task 2's `CitationList` (Issue 2, Assumption 2). |
| 8 | AC-001.8 text and citation fields as plain text | PASS (text part) | No `dangerouslySetInnerHTML` or `innerHTML` in the feature (grep). Test AC-001.8 sends `<b>` and `<script>` and asserts that no `b` or `script` element exists (edge case 8). Citation fields are not rendered yet (Task 2). |
| 9 | AC-001.12 NO_SOURCE → MSG109 with "Jira", no citations | PASS | `ChatAnswer.tsx:16`; `msg()` fills `{tracker_name}` from `TRACKER_NAME`; test checks "create an issue in Jira" (edge case 11) |
| 10 | AC-001.13 non-field failure → MSG06 toast, pending removed, question kept, nothing added | PASS (one exception) | `KnowledgeChatbotPage.tsx:31-34`; tests for 503 and for a network error (edge cases 5, 6). A 401 shows MSG07 and signs the user out instead of MSG06 (Issue 1). |
| 11 | AC-001.14 400 field error under the box, question kept | PASS | `applyApiErrors(form, error)`; test checks that MSG02 comes from the server, the box keeps its value and no MSG06 is shown (edge case 7) |
| 12 | AC-004.1 keyword answers with markers, a Wiki and a ticket Solution citation | PASS | `mocks/data.ts`, `mocks/handlers.ts:43-49`; `it.each` over VPN, leave, EXPENSE (the last checks case-insensitive matching) |
| 13 | AC-004.2 no match → NO_SOURCE, no citations | PASS | test AC-004.2 |
| 14 | AC-004.3 empty / >1,000 → 400 MSG01 / MSG02 on `question` | PASS | `mocks/handlers.ts:17-31`; two AC-004.3 tests check the exact `fieldErrors` |

EXTRA: none. The `aria-label="Question"` and the `<section aria-label="Conversation">` come from the design's
accessible-states decision. They add no visible text.

### Stage 2 — Quality
- Contract (`openapi/tsolve-api.yaml`, OpenAPI 3.1.0) matches the spec for the ask path. It has the `askChatbot`
  operation with 200 / 400 / 401 / 403 / 503. `ChatbotQuestion` requires `question` (1..1000) and `history`
  (maxItems 10). `ChatbotTurn`, `ChatbotAnswer` (uuid `answerId`) and the `oneOf` + `discriminator` citation are as
  specified. `CitedSolution` has `workaround: [string, 'null']`. orval generated a real union
  (`WikiDocumentCitation | TicketSolutionCitation`) and `workaround: string | null`. The availability and rating
  paths are correctly absent.
- Ticket details: `TicketSolutionCitation` is `{ number, type, solution }`, and `CitedSolution` holds only the five
  Solution sections. The contract or the mock data has no id, link, title, Project, writer, snapshot or Evidence.
- Guidelines: no `any`, `@ts-ignore` or non-null `!` (grep). Every MSG text comes from `msg()` / `showMessage()`.
  The feature uses no raw antd Tag, Table, Modal, Upload or message (lint). It imports API code only from
  `@/shared/api` and `@/shared/api/mocks` (lint, import boundaries). The page has 53 lines and the largest component
  58 (C2). The feature hook does not reuse the generated name (`useAskQuestion` wraps `useAskChatbot`). The
  mutation sets `handlesOwnErrors`, so there is never a double toast. Send is loading and disabled while pending,
  and a ref blocks a second submit before the next render. The question is kept on failure and cleared only on
  success (09 §3).

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | All 14 criteria of this task are met (Stage 1). The 401 path follows the global handler (Issue 1). |
| Completeness | pass | Every criterion of the task has a test. The citation halves of AC-001.7/8 are left to Task 2 by the plan (Issue 2). Edge cases 1–8, 11 and 21 are tested. |
| Correctness | pass | 22/22 feature tests, lint and typecheck clean; no CRITICAL finding |
| Integration | pass | The contract matches the spec's Interface Contracts. The page relies only on `@/shared/api`, `@/shared/forms`, `@/shared/messages` and `@/shared/ui`. The 401 handling uses the global handler, which still runs with `handlesOwnErrors`. |

## Traceability

```
  ✓ AC-001.1 … AC-001.8   frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx
  ✗ AC-001.9, AC-001.10, AC-001.11   no test under this module's paths   (Task 2, KAN-73)
  ✓ AC-001.12, AC-001.13, AC-001.14   frontend/src/features/chatbot/pages/KnowledgeChatbotPage.test.tsx
  ✓ AC-004.1, AC-004.2, AC-004.3   frontend/src/features/chatbot/mocks/handlers.test.ts
  ✗ AC-004.4, AC-004.5   no test under this module's paths   (Task 2, KAN-73)
trace chatbot (STORY-001, STORY-004): 14/19 acceptance criteria have a test
```

Only Task 2's ids are missing, as expected. I read each test that names a Task 1 id, and each one checks its
criterion.

## Issues

No CRITICAL issues.

**IMPORTANT** (does not block; needs a decision)

1. `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.tsx:32`. A 401 returns early, so the user gets the
   global MSG07 dialog and is signed out, not MSG06. AC-001.13 says MSG06 "for any reason other than field errors".
   The code follows guideline 06 §6 (a 401 is always handled globally, and one error never shows two messages), and
   `shared/api/query-client.ts:38` calls `onUnauthorized` even when `handlesOwnErrors` is set. So the behaviour is
   right, but the spec text does not allow it, and this branch has no test.
   Fix: reword AC-001.13 through Change Mode ("… other than field errors or an expired session (401) …"), then add
   a test "AC-001.13 leaves a 401 to the session-expired dialog" that checks MSG07 is shown and MSG06 is not.
2. `frontend/src/features/chatbot/components/ChatAnswer.tsx:14-18`. AC-001.7 ("followed by the list of its
   citations in number order") and AC-001.8 ("every citation field as plain text") are only half built. No citation
   is rendered, and the AC-001.7 and AC-001.8 tests check only the answer text. The plan gives `CitationList` to
   Task 2. Until then an answer shows `[1]`, `[2]` markers with nothing to point to.
   Fix: Task 2 adds an AC-001.7 test (citations under the text, in number order, also when the server sends them
   out of order) and an AC-001.8 test (HTML in a Wiki title and in each Solution section shown as text).

**MINOR**

3. `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.tsx:33`. If a 400 has field errors only on a field
   the form does not have (for example `history`), `applyApiErrors` returns `true`. `form.setFields` then sets an
   error nobody sees, and the user gets no message. The contract lists only `question`, so the risk is low.
   Fix: show MSG06 unless some field error targets `question`, e.g.
   `error.fieldErrors.some((e) => e.field === 'question') ? applyApiErrors(form, error) : showMessage('MSG06')`.
4. `frontend/src/features/chatbot/mocks/handlers.ts:7`. This repeats `QUESTION_MAX_LENGTH = 1000`, which is
   already in `model.ts:7`.
   Fix: `import { QUESTION_MAX_LENGTH } from '../model'`.
5. `frontend/src/features/chatbot/components/QuestionBox.tsx:8`. The form values type is written by hand. Guideline
   09 §1 says to type the form with the generated request type.
   Fix: `export type QuestionFormValues = Pick<ChatbotQuestion, 'question'>`.
6. `frontend/src/features/chatbot/pages/KnowledgeChatbotPage.tsx:29`. The box stays editable while an answer is
   pending, and on success `setFieldValue('question', '')` also wipes a follow-up the user typed during the wait.
   AC-001.7 says to empty the box, so this meets the spec. I note it only as a UX point.
   Fix (only if the user wants it): clear the box only when it still holds the question that was sent.

## Assumptions made
1. **401 vs AC-001.13 (open judgment call).** I passed the 401 behaviour (MSG07 dialog and sign-out, not MSG06) as
   the intended reading, because guideline 06 §6 and fe-foundation require it. If the user reads AC-001.13
   literally, it is a spec defect, and the fix is a spec rewording, not a code change (Issue 1).
2. **AC-001.7 / AC-001.8 split (open judgment call).** I passed the text parts of AC-001.7 and AC-001.8 in Task 1
   and left their citation parts to Task 2, because tasks.md and design.md put `CitationList` in Task 2. If the
   user wants every criterion in "Covers" to be fully met by its task, the verdict becomes fail until Task 2's tests
   exist, or tasks.md should list AC-001.7/8 under both tasks (Issue 2).
3. I did not run the full test suite, `build`, `format:check` or `generate:api` (the dispatcher said not to; the
   machine is short on memory). I took them from the session log without checking them again. Lint, typecheck and
   the chatbot tests I ran myself.
4. I did not run `sdd.ts feature chatbot ask-answer passing`. The dispatcher allowed me to write only the review
   files, so the main session must make that status change.

## Test Report
See `task-1-ask-answer-test-report.md`. In summary: `npm run lint` exit 0; `npm run typecheck` exit 0;
`npx vitest run src/features/chatbot` gives 2 files and 22/22 passed in 22.8 s; `sdd.ts trace chatbot STORY-001
STORY-004` gives 14/19, and the missing 5 are all Task 2's. No coverage script exists.

## Resolution (FE Lead, 2026-10-23)

1. 401 → spec change: AC-001.13 now excludes a 401 (sign-out, MSG07); approved (spec version 256c1f93); test
   `AC-001.13 leaves a 401 to the sign-out (MSG07) and shows no MSG06`.
2. Citation halves of AC-001.7 / AC-001.8 → listed under Task 2 (KAN-73) in `tasks.md`.
3. Field error on another field → BUG-006, fixed in dcc6b52 with test `… (BUG-006)`.
4. Mock reuses `QUESTION_MAX_LENGTH` from `model.ts` (dcc6b52).
5. `QuestionFormValues = Pick<ChatbotQuestion, 'question'>` (dcc6b52).
6. Left as is: AC-001.7 says the box is emptied when the answer arrives.

Verify after the fixes: format, lint, typecheck clean; 423 tests passed; build OK; `generate:api` no diff.
