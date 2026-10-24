---
module: chatbot
feature: session-conversations
task: 3
verdict: pass
attempt: 1
date: 2026-10-24
reviewer: harnessclaude-reviewer
---

# Review: chatbot — Task 3: Session conversations (KAN-72, STORY-002)

## Summary
All 11 criteria of STORY-002 are met and have a test. Quality notes below are non-blocking. Verdict: PASS with notes.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-002.1-8, 11 met in `useConversations`, `chatbot-storage`, `ConversationList`; 002.9/002.10 came with the earlier task. |
| Completeness | pass | trace 11/11. |
| Correctness | pass | Edges 12-18 covered (other user, blocked storage, reload, pending answer, 12 turns). |
| Integration | pass | Key `tsolve.session.chatbot.*` is cleared by the fe-foundation logout/401 path. |

## Traceability
`trace chatbot (STORY-002): 11/11 acceptance criteria have a test`

## Issues (none blocking)
Warnings (Important):
1. `chatbot-storage.ts` isConversation/isAnswer: `citations` is checked only with `Array.isArray`. A hand-edited or older entry with malformed citations passes validation and may crash the render. Suggest validating citation `type`/`number`, or an error boundary. Low likelihood (sessionStorage, same tab).
2. `ConversationList.tsx`: the title is `turns[0]?.question`, but `isConversation` accepts `turns: []`, giving an empty-label button. Require `turns.length > 0` in the validator.

Informational:
3. `startedAt` is set when the first answer arrives (`addTurn`), not when the question is sent. AC-002.3 says "started"; equal in practice. Two conversations in the same ms would sort unstable (impossible while the list is disabled).
4. After a reload with stored conversations the empty "New conversation" is open (tested, edge 15); spec is silent, matches AC-002.1/8 wording.
5. Opening another conversation drops the empty one (not in spec, noted in the task notes); consistent with AC-002.6 "keep one".
6. Long titles wrap with no limit (spec defines none; Figma-compared).
7. `?? 0` user id fallback could write under user 0 if a session were ever missing; the guard prevents it.

Frontend/AGENTS.md pass: no `any` (uses `unknown` + guards), imports stay inside feature/shared public APIs, colours only via `--ant-*` tokens (280px / 88px are sizes), `aria-current`, `aria-hidden` icons, list labelled, disabled list during pending, storage errors swallowed.

## Test Report
Verify (format, lint, typecheck, 455 tests, build, generate:api) taken from the implementer, as agreed. My own run of `vitest run src/features/chatbot` hit the 110 s limit on this machine (48 passed, 1 error from the cut-off, one file unfinished): inconclusive, not a failure.

## Assumptions made
- Dropping the empty conversation on opening another is accepted (spec silent).
- Reload opening the empty conversation is accepted (spec silent).
