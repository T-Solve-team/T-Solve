---
module: chatbot
feature: ratings
task: 4
verdict: pass
attempt: 1
date: 2026-10-24
reviewer: harnessclaude-reviewer
---

# Review: chatbot — Task 4: Rate an answer (KAN-76, STORY-003, AC-004.6)

## Summary
AC-003.1–003.6 and AC-004.6 are met and each has a test. No critical findings. Verdict: PASS.

## Traceability
`trace chatbot (STORY-003): 6/6 acceptance criteria have a test`

## Issues (none blocking)
1. `useRateAnswer.ts`: `onSuccess` is passed to `mutate()`, so it does not run if the page unmounts mid-request; the
   server keeps the rating, the local highlight is not set. Not covered by the spec; logged as BUG-008 and fixed
   by a quick change (`onSuccess` as a hook option, and the rating written to the session storage when the screen is
   gone).
2. `KnowledgeChatbotPage.tsx`: the `openId !== null` guard has no `else`; unreachable today.
3. The rating buttons' labels are literals; allowed (button labels are fixed English text, not system messages).
4. One running request disables the buttons of every answer in the thread; within AC-003.2 and edge 22.
