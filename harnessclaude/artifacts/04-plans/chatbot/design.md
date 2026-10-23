---
module: chatbot
date: 2026-10-23
---

# Design: chatbot

Spec: `artifacts/03-modules/chatbot/spec.md` · Module: `artifacts/03-modules/chatbot/module.md` ·
Rules: `frontend/AGENTS.md` and `frontend/guidelines/` (they win over anything here that contradicts them).

## Architecture Overview

Frontend only for now: the backend is served by MSW from the contract in `frontend/openapi/tsolve-api.yaml`.
Everything lives in `frontend/src/features/chatbot/`; no file of `app/`, `shared/`, `mocks/` or `test/` changes
(one exception, see Key Decisions: the fe-foundation placeholder test of 12.1).

```
QuestionBox ──submit──► useAskQuestion (generated askChatbot mutation, handlesOwnErrors)
     ▲  keeps text on error          │ question + last 10 turns of the open conversation
     │                               ▼
     │                 POST /api/v1/chatbot/answers  ── MSW mocks/handlers.ts (keyword answers) — later the backend
     │                               │ ChatbotAnswer
     │                               ▼
     └──────────── useConversations (state + sessionStorage 'tsolve.session.chatbot.conversations')
                                     │ conversations, openConversation
              ┌──────────────────────┴───────────────────────┐
     ConversationList (New conversation, list)       ConversationThread → ChatAnswer → CitationList
                                                                         │ wiki ids
                                                     useUnavailableDocuments (generated query)
                                                     GET /api/v1/chatbot/unavailable-documents
```

- **Ask:** `useAskQuestion` wraps the generated `useAskChatbot` with `meta: { handlesOwnErrors: true }`. On a
  400 with field errors it calls `applyApiErrors(form, error)`; on any other error `showMessage('MSG06')`. The
  question box is cleared only on success, so the question is kept on failure (AC-001.13).
- **Pending answer:** while the mutation runs, the thread shows the question and a loading answer; Send, New
  conversation and the conversation list are disabled, so the answer always lands in the conversation it was
  asked in.
- **Conversations:** `useConversations(userId)` keeps `{ conversations, openId }` in React state. `openId === null`
  is the empty "New conversation", which is never stored. When the first answer of the empty conversation arrives,
  a conversation is created (`crypto.randomUUID()`, title = first question, `startedAt` = now) and opened. Every
  change is written to `sessionStorage`; on mount the stored value is read once.
- **Storage:** `chatbot-storage.ts` reads and writes `{ version: 1, userId, conversations }` under
  `tsolve.session.chatbot.conversations`, inside `try/catch`. A value of another user, another version or that
  does not parse is dropped (AC-002.8); a blocked storage leaves the state in memory only (AC-002.11). Logout and
  a global 401 already remove the key (fe-foundation `resetSession`).
- **Citations:** `CitationList` renders the citations in number order. A Wiki citation is a `Link` to
  `paths.wikiDocument(id)`, or MSG110 in secondary text when the id is in the unavailable list. A ticket citation is
  "Solution from a ticket" with a Show / Hide text button that expands `TicketSolution` (the five sections, Workaround
  only when not null).
- **Availability:** `ConversationThread` collects the Wiki document ids of the shown conversation and calls the
  generated `useListUnavailableCitedDocuments` (enabled when there is at least one id). Query errors show no toast
  (fe-foundation's global handler only toasts mutations), so on error the links stay (AC-002.10).
- **Text safety:** answer text and citation fields are rendered as React text (`white-space: pre-wrap`), never
  with `dangerouslySetInnerHTML` (AC-001.8).

## Data Model

```ts
// generated from the contract (spec Interface Contracts)
ChatbotQuestion, ChatbotTurn, ChatbotAnswer, ChatbotAnswerOutcome, ChatbotCitation,
WikiDocumentCitation, TicketSolutionCitation, CitedSolution, UnavailableDocuments, ChatbotRating

// features/chatbot/model.ts
export type ChatTurn = { question: string; answer: ChatbotAnswer; rating?: ChatbotRating['rating'] };  // rating: Task 4
export type Conversation = { id: string; title: string; startedAt: string; turns: ChatTurn[] };
export type StoredConversations = { version: 1; userId: number; conversations: Conversation[] };
export const HISTORY_LIMIT = 10;                      // turns sent with a follow-up (AC-002.5)
export const QUESTION_MAX_LENGTH = 1000;              // SRS 12.1

/** The turns sent with a question: the last 10 of the open conversation, oldest first. */
export function toHistory(turns: ChatTurn[]): ChatbotTurn[];  // NO_SOURCE answer → ''
```

**Mock data** (`mocks/data.ts`, ids 9001+ so they never clash with other features):

| Keyword (case-insensitive) | Answer | Citations |
|----------------------------|--------|-----------|
| `vpn` | GlobalProtect setup and the USB selective suspend fix (Figma text) | [1] Wiki 9001 "VPN setup on Windows 11" · [2] ticket Solution (VPN drops every 30 minutes, with Workaround null) |
| `leave` | Public holiday leave rules | [1] Wiki 9002 "Leave policy 2026" · [2] ticket Solution with a Workaround |
| `expense` | Taxi expense claim steps | [1] Wiki 9003 "Expense claim guide" (marked unavailable) · [2] ticket Solution |
| anything else | `NO_SOURCE` | none |

`unavailableDocumentIds` starts as `[9003]`; `resetMockData()` restores it and the answers. The rating handlers
(Task 4) keep no state: `PUT` and `DELETE` answer 204 for any `answerId` (AC-004.6).

## Key Decisions

| Decision | Choice | Alternatives Considered | Rationale |
|----------|--------|------------------------|-----------|
| Backend now | MSW mocks of the contract; backend criteria added later by Change Mode | Build the Java package now | No JDK 21 / Maven on the FE Lead's machine (user, 23/10) |
| Spec scope | User-visible UC-51–53 and the REST contract; UC-53 specified, built later | FE-only spec | One contract; the backend change adds criteria without rewriting stories (user, 23/10) |
| Where conversations live | Browser `sessionStorage`, key `tsolve.session.chatbot.conversations`; the frontend sends the earlier turns | Server-side per login session; both | Logout clearing already exists and is tested; no server state for chat (user, 23/10) |
| Answer delivery | One JSON response with a loading answer | Streaming (SSE) | The generated fetch client has no streaming (user, 23/10) |
| Ticket citation | The Solution by section (Symptoms, Root cause, Resolution steps, Workaround, Scope of applicability) | One `solutionText` (PR #30 `SearchHit`) | Matches Figma (b); search must return sections — to agree with Hải Anh (user, 23/10) |
| MSG110 | `GET /api/v1/chatbot/unavailable-documents` when a conversation is shown | Detect on click | Matches Figma (f) (user, 23/10) |
| Source filtering | Server only; the frontend never filters | — | GBR-31, GBR-36; the UI cannot be trusted (user, 23/10) |
| AI failure | MSG06 toast; the question stays in the box, not in the thread | A "Send again" bubble | Matches Figma (e) plus UC-51 "keeps the question" (user, 23/10) |
| Ratings in KAN-48 | Buttons left out until UC-53 | Disabled buttons | No half-feature (user, 23/10) |
| Rating comment (KAN-75) | None: the two buttons of Figma 12.1 (c) only | Optional comment of at most 1,000 characters (UC-53 wording, the Story's first draft) | Figma (c) has no comment box (user, 24/10) |
| KAN-75 scope | Frontend only; the rating paths are MSW mocks | Backend `chatbot_answer` / `chatbot_rating` now | No JDK 21 / Maven; the backend change later stores the rating with the user, keeps rated and `NO_SOURCE` answers, deletes unrated `ANSWERED` ones at session end (spec Requirement 7) (user, 24/10) |
| Where a rating lives on the client | On its `ChatTurn` (`rating?`), saved with the conversation in `sessionStorage`; storage `version` stays 1 (an optional field) | `GET` the session's ratings | AC-003.4 with no new endpoint; the server is only written to (user, 24/10) |
| When the highlight changes | After the `PUT` / `DELETE` succeeds; both buttons disabled while it runs | Optimistic, undone on failure | AC-003.5 keeps the previous highlight (user, 24/10) |
| Rating buttons | antd `Button`s "Helpful" / "Not helpful" under the citations: the rated one `type="primary"`, the other default, `aria-pressed` on both; the highlighted one removes the rating (AC-003.6) | A toggle group | Matches Figma (c); tests find them by role and pressed state |
| Rating errors | `handlesOwnErrors`: every error but a 401 shows MSG06 (a 401 stays with the global handler: sign out, MSG07) | The global handler | Same pattern as the ask (AC-001.13) |
| Split (KAN-75) | One task, one PR: contract, mocks, buttons, storage (~350 lines) | Two PRs | One behaviour a user can try (user, 24/10) |
| Mock answers | Keyword answers; others `NO_SOURCE` | One fixed answer | Demos every Figma state (user, 23/10) |
| AI provider | Ollama for answers, OpenRouter for embeddings, key in backend `.env` only (GBR-12); the model is chosen in the backend change | — | Report3 decides it (user, 23/10) |
| Split | Three vertical slices, one PR each, each with the contract and mocks its behaviour needs: KAN-71 ask and answer (ask path), KAN-73 citations (availability path), KAN-72 session conversations | One PR for KAN-48 (~1,250 lines); four PRs with the contract alone (KAN-70, closed) | Each PR is one behaviour a user can try and near 400 changed lines; a contract ships with its first screen (guideline 06 "one feature = one PR"); behaviour comes before the line count: KAN-71 is ~850 lines with tests and is not split further (user, 23/10) |
| Errors of the ask | `handlesOwnErrors`: field errors under the box, every other error MSG06 | The global handler | The global handler would show a 503's code; AC-001.13 wants MSG06 always and the box kept |
| History sent | Decided in planning: the last 10 turns, question and answer text only, no citations | Every turn | Bounded request size; citations add nothing to the model's context |
| Newest first | Decided in planning: by the time the conversation was started (its first answer); continuing one does not move it | By last activity | Report3 "newest first" names conversations; Figma (i) puts a new one on top |
| The empty conversation | Decided in planning: not stored; shown as "New conversation" on top while open; a reload opens it again | Store it | Nothing to keep until it has a question |
| While waiting | Decided in planning: Send, New conversation and the list are disabled | Allow switching | The answer always lands in its own conversation (builder check) |
| Keyboard | Decided in planning: Enter sends, Shift+Enter adds a line; the box is a `TextArea` with `count={{ show: true, max: 1000 }}`, label "Question" (visually hidden, used by MSG01/MSG02 and screen readers) | — | SRS 12.1 "Enter sends"; Figma has no visible label |
| Components | Decided in planning: in `features/chatbot/components/`, not `shared/ui` | A kit chat component | Rule of two; only one screen uses them |
| Accessible states | Decided in planning (builder check): the pending answer is an answer block with `aria-busy="true"` and an antd `Spin`; the open conversation's list button has `aria-current="true"`; the thread is a `<section>` labelled by the open conversation's title; the title "Knowledge Chatbot" is the shell's h1 (route title), the page adds none | — | Tests find them by role; no new visible text |
| Citation union | Decided in planning (builder check): `oneOf` with `discriminator: type`; if orval's output is not a usable TS union, Task 1 uses one `ChatbotCitation` object with `type` and the fields of both as optional, and the spec contract is updated through Change Mode | — | Keeps the generated client usable; the shape on the wire is the same |
| Placeholder test of 12.1 | A one-time fe-foundation change request in the KAN-71 PR: AC-005.3 becomes "WHILE a screen of … is not built by its module, … SHALL show a placeholder" (Changelog row); the 12.1 row leaves `app/router/route-table.test.tsx` | Drop only 12.1 from AC-005.3 (a change request again for Wiki, Triage, Workspace Setup); a separate PR first; a quick change of the test only | It changes a criterion, so it is a change request; worded once, later modules remove their row with a quick change (user, 23/10) |

## File Structure

```
frontend/
├── openapi/tsolve-api.yaml                         Modify: tag chatbot, ask path and schemas (Task 1); availability path (Task 2); rating paths (Task 4)
├── src/shared/api/generated/**                     Regenerate (npm run generate:api) (Tasks 1, 2, 4)
└── src/features/chatbot/
    ├── model.ts                                    Create: types, limits, toHistory (Task 1)
    ├── mocks/data.ts                               Create: keyword answers, resetMockData (Task 1); unavailable ids (Task 2)
    ├── mocks/handlers.ts                           Modify: askChatbot (Task 1); listUnavailableCitedDocuments (Task 2)
    ├── mocks/handlers.test.ts                      Create: AC-004.x (Task 1); AC-004.4 (Task 2)
    ├── hooks/useAskQuestion.ts                     Create: mutation + error handling (Task 1)
    ├── hooks/useConversations.ts                   Create: conversations state (Task 1: one conversation in memory; Task 3: list + storage)
    ├── hooks/chatbot-storage.ts (+ .test.ts)       Create: read/write sessionStorage (Task 3)
    ├── hooks/useUnavailableDocuments.ts            Create: availability query (Task 2)
    ├── hooks/useRateAnswer.ts                      Create: rate / remove mutations, MSG06 (Task 4)
    ├── components/RatingButtons.tsx                Create: Helpful / Not helpful (Task 4)
    ├── components/QuestionBox.tsx                  Create: form, TextArea, Enter, Send (Task 1)
    ├── components/ConversationThread.tsx           Create: turns, pending answer (Task 1)
    ├── components/ChatAnswer.tsx                   Create: answer text or MSG109 (Task 1), citations (Task 2)
    ├── components/CitationList.tsx                 Create: Wiki link / MSG110, ticket Solution toggle (Task 2)
    ├── components/TicketSolution.tsx               Create: the five sections (Task 2)
    ├── components/ConversationList.tsx             Create: New conversation, list (Task 3)
    ├── components/*.module.css                     Create: layout, bubbles (Tasks 2–4)
    ├── pages/KnowledgeChatbotPage.tsx              Modify: placeholder → screen (Task 1, list added in Task 3)
    └── pages/KnowledgeChatbotPage.test.tsx         Create: page tests named with AC ids (Tasks 2–4)
```

`routes.ts` and `index.ts` stay as they are (the route already lazy-loads `pages/KnowledgeChatbotPage`).

## External Dependencies
- None new. MSW serves the contract until the backend `chatbot` package exists.
- PR #30 (`search`, Hải Anh): `SearchHit` must carry the Solution by section for the backend change.

## Edge Cases & Risks

Dimensions analyzed: 1 User types, 2 Input extremes, 3 Timing, 5 State transitions, 6 Environment, 7 Error
cascades, 8 Authorization, 9 Data integrity, 10 Integration, 11 Compliance · Skipped: 4 Scale (bounded by the
session and 10 turns), 12 Business logic (no amounts or quotas).

| # | Story | Dimension | Scenario | Severity | Expected behavior | Test task |
|---|-------|-----------|----------|----------|-------------------|-----------|
| 1 | STORY-001 | Input extremes | Only spaces and new lines | Medium | MSG01, nothing sent | Task 1 |
| 2 | STORY-001 | Input extremes | 1,000 characters exactly / 1,001 | Medium | Sent / MSG02, not sent | Task 1 |
| 3 | STORY-001 | Input extremes | Shift+Enter | Low | New line, not sent | Task 1 |
| 4 | STORY-001 | Timing | Enter pressed twice quickly | High | One request (Send disabled while pending) | Task 1 |
| 5 | STORY-001 | Error cascades | 503 MSG06 from the AI module | High | MSG06 toast, question kept in the box, thread unchanged | Task 1 |
| 6 | STORY-001 | Error cascades | Network error | High | Same as 5 | Task 1 |
| 7 | STORY-001 | Integration | 400 with a field error on `question` | Medium | Field MSG under the box, question kept | Task 1 |
| 8 | STORY-001 | Data integrity | Answer text holds `<script>` or HTML | Critical | Shown as text, nothing runs | Task 1 |
| 9 | STORY-001 | Compliance | Ticket citation | Critical | No ticket id, link, title, Project, writer, Evidence in the DOM | Task 2 |
| 10 | STORY-001 | Data integrity | Solution with Workaround null | Low | Workaround section left out | Task 2 |
| 11 | STORY-001 | State transitions | `NO_SOURCE` | High | MSG109 with "Jira", no citations | Task 1 |
| 12 | STORY-002 | Authorization | User A logs out, user B signs in on the same tab | Critical | B sees no conversation of A (key removed; userId check as second guard) | Task 3 |
| 13 | STORY-002 | Data integrity | Stored JSON broken or of an older version | Medium | Dropped; empty conversation | Task 3 |
| 14 | STORY-002 | Environment | Storage blocked (private mode) | Medium | Chat works; conversations in memory only | Task 3 |
| 15 | STORY-002 | Environment | Page reload | Medium | Conversations back; the empty conversation open | Task 3 |
| 16 | STORY-002 | State transitions | New conversation while the open one is empty | Low | No second empty entry | Task 3 |
| 17 | STORY-002 | Timing | Switching conversation while an answer is pending | High | Not possible (list disabled); the answer lands in its own conversation | Task 3 |
| 18 | STORY-002 | Integration | 12 turns, then a follow-up | Medium | Only the last 10 turns are sent, oldest first | Task 3 |
| 19 | STORY-002 | Integration | Availability request fails | Low | Links shown, no toast | Task 2 |
| 20 | STORY-002 | Authorization | A cited Wiki document became hidden | Medium | MSG110 in place of its link | Task 2 |
| 21 | STORY-001 | User types | Employee only, and a user with several roles | Medium | Both open 12.1 (route roles `EMPLOYEE`) | Task 1 |
| 22 | STORY-003 | Timing | Two clicks on the buttons quickly | High | One request (both disabled while it runs) | Task 4 |
| 23 | STORY-003 | Timing | The user opens another conversation while a rating is saving | Medium | The rating lands on its own turn (by conversation id and `answerId`) | Task 4 |
| 24 | STORY-003 | State transitions | Pending answer, or a failed ask (MSG06) | Medium | No rating buttons (no answer yet / no turn) | Task 4 |
| 25 | STORY-003 | Environment | Storage blocked | Low | The rating is kept in memory (AC-002.11) | Task 4 |
| 26 | STORY-003 | Data integrity | Stored turn with an unknown `rating` value | Low | Dropped like other broken data (AC-002.8) | Task 4 |
| 27 | STORY-003 | Error cascades | `DELETE` fails | Medium | MSG06, highlight kept | Task 4 |
| 28 | STORY-003 | Authorization | 401 on a rating request | Medium | Signed out with MSG07, no MSG06 | Task 4 |

Open questions for the spec owner: none.

## Risk Review — 2026-10-23 — Verdict: CAUTION

The module touches personal data (questions may hold PII) and a contract shared with `search`.

| Risk | Severity | Mitigation | Task |
|------|----------|------------|------|
| Conversation of the previous user shown | Critical | `tsolve.session.*` removed at logout and on 401 (tested in fe-foundation); stored `userId` checked on read | Task 3 |
| Ticket details leak through a citation | Critical | The contract has no ticket fields; a test checks the DOM of a ticket citation | Task 1, Task 2 |
| Answer text rendered as HTML | Critical | Plain text only; test with `<script>` | Task 1 |
| Search returns one `solutionText`, not sections | High | Raised with Hải Anh on PR #30 before the backend change; the frontend contract stays | — |
| Mocks drift from the real backend | Medium | MSW handlers typed with the generated types; backend change reuses the contract | Task 1 |
| The 12.1 placeholder test fails when the page is built | High | One-time fe-foundation change request in the KAN-71 PR: AC-005.3 reworded to "while not built", the 12.1 row removed from `route-table.test.tsx` (agreed with the FE Lead, 23/10) | Task 1 |
| PII in questions sent to the AI | High | Backend masks before the AI module (GBR-36); recorded for the backend change | — |
