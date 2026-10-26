# Task 5: UUID string ids for the chatbot

**Module:** chatbot
**Assignee:** Trần Trung Hiếu
**Status:** completed (merged in PR #70)
**Iteration:** 1

---

## Session 2026-10-26

### Working on: uuid-ids (KAN-205, branch `feature/KAN-205-fe-foundation-uuid-ids`, merged and deleted)
- Story STORY-002; AC-002.12. The chatbot ids are UUID strings in the contract, the generated client, the hooks and the mocks.
- `useConversations` and `useRateAnswer` take a required `userId: string` (decision Q1).
- `KnowledgeChatbotPage` is a wrapper that renders `KnowledgeChatbot({userId})`; it renders null without a session (decision Q3).

```
trace chatbot (STORY-002): 12/12 acceptance criteria have a test
```

### Verify (frontend, one after another)
- Full frontend verify passed; 541 tests.
- Review: PASS on attempt 1 (`06-evaluation/chatbot/task-5-uuid-ids-review.md`).

### Hand-off (2026-10-26)
- done: chatbot ids are UUID strings (contract, client, hooks, mocks); `useConversations` and `useRateAnswer` take `userId`
- open: the chatbot backend contract must take UUID ids; BUG-018 (flaky user list tests, identity owner, not chatbot)
- next: fe-foundation Task 8 site sign-in (KAN-206)
- branch: `feature/KAN-205-fe-foundation-uuid-ids` (merged, deleted)
- PR: #70 merged as `0c2a550`
