# Task 20: manual-evidence-tab

**Module:** ticket-repository · **Assignee:** Codex (gpt-6.1-sol, high) · **Status:** evaluating

## Session 2026-11-14
### Working on: manual-evidence-tab
- New kit component shared/ui/EvidenceUpload (antd Upload, beforeUpload never uploads): kept + new files listed with remove buttons; refuses other types / > 10 MB with MSG34 and a 6th file with MSG72; exported from shared/ui (FE Lead review needed, frontend AGENTS.md).
- ManualEntryForm: Evidence field after the Solution sections; Save and Save as draft send entry + files (keepEvidenceIds when reopened); reopened Draft lists its evidence; files fieldErrors under Evidence; files cleared after Save; new files not sent twice after Save as draft.
- MSW handlers: Draft Evidence stored and returned; MSG34 / MSG72 400 mimicked.
- Verify (orchestrator): format:check OK, lint 0 errors (1 old warning), typecheck OK, vitest ticket-import + shared/ui + pages 172/172, build OK, generate:api no diff.
- Commit: default@ad87c786

## Session 2026-11-14 (review finding 1)
- Review PASS with INFORMATIONAL 1: a client MSG34/MSG72 stayed after a successful Save (AC-006.14). Fixed by the orchestrator: the message belongs to the value it was raised for, so a value replaced from outside drops it; test fails without the fix.
- Verify: eslint, typecheck OK; vitest EvidenceUpload + ticket-import 133/133.
- Commit: default@fb0c06bf
