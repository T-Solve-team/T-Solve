# Task 10: UUID string ids for /me, session and chatbot

**Module:** fe-foundation
**Assignee:** Trần Trung Hiếu
**Status:** completed (merged in PR #70)
**Iteration:** 1

---

## Session 2026-10-26

### Working on: uuid-ids (KAN-205, branch `feature/KAN-205-fe-foundation-uuid-ids`, merged and deleted)
- Stories STORY-001, STORY-009; AC-001.8, AC-009.7. `/me`, session and chatbot ids are UUID strings in the contract, the generated client, the hooks and the mocks.
- `useConversations` and `useRateAnswer` take a required `userId: string` (decision Q1).
- `KnowledgeChatbotPage` is a wrapper that renders `KnowledgeChatbot({userId})`; it renders null without a session (decision Q3).
- Also in the PR: the review queue `departmentId` and the users `GET /api/v1/users` `departmentId` are UUID strings (contract, client, mocks).
- BUG-017 fixed: a review `departmentId` that is not a UUID is ignored (test named with BUG-017).
- BUG-018 logged, open: flaky `UserListPage` tests (owner Phạm Tùng Dương, KAN-107).

```
trace fe-foundation (STORY-001, STORY-009): 15/15 acceptance criteria have a test
```

### Verify (frontend, one after another)
- Full frontend verify passed; 541 tests.
- Review: PASS on attempt 1 (`06-evaluation/fe-foundation/task-10-uuid-ids-review.md`).

### Hand-off (2026-10-26)
- done: UUID string ids for /me, session and chatbot; review queue and user list `departmentId` as UUID; BUG-017 fixed
- open: BUG-018 (flaky user list tests, identity owner); the `/users` and review-queue backend contracts must take UUID `departmentId`
- next: fe-foundation Task 8 site sign-in (KAN-206)
- branch: `feature/KAN-205-fe-foundation-uuid-ids` (merged, deleted)
- PR: #70 merged as `0c2a550`
