---
module: wiki
feature: wiki-indexing
task: 7
verdict: pass
attempt: 1
date: 2026-11-15
reviewer: harnessclaude-reviewer
iteration: 1
---

# Review: wiki — Task 7: Indexing job (commit a54e8b47)

## Summary
This review checks the code against AC-004.14 to AC-004.16. It reads only `git show a54e8b47`. There is no critical or major finding.

**The pass holds only if `WikiIndexIT` and `mvn verify` are green.** The reviewer did not run them.

Verified by reading:
- **`WikiIndexJobHandler`:** its `handlerType` is `wiki-index`, the type Task 6 enqueues. The job runs in these steps:
  - It reads the document in a short read-only transaction and returns if the document is not Published (AC-004.16).
  - It embeds the passages in batches of 16, outside any transaction (`NOT_SUPPORTED`), as `EmbeddingClient` requires.
  - In one `REQUIRES_NEW` transaction it checks Published again under `PESSIMISTIC_WRITE`, deletes the old chunks, inserts the new ones and sets `INDEXED` (AC-004.14).
  - A trash committed while the embedding runs changes nothing.
- **Failures (AC-004.15):** any of these keeps no new passage, sets `FAILED` and leaves the document Published:
  - the embedding call fails
  - it returns the wrong number of vectors
  - a vector has the wrong dimension
  - a vector holds a non-finite value

  A database failure during the write rolls back and is passed up to the worker.
- **`PassageSplitter`:**
  - Passages are at most 1,000 characters.
  - The cut is at the last paragraph break in the window. If there is none, it falls back to whitespace, then to a hard cut.
  - Blank text is skipped.
  - Only the first 200 passages are kept, with the required `ponytail:` comment.
- **Storage:** vectors are written through a native `CAST(:vector AS vector)` together with the model name.
- **Scope:** no new dependency and no scope creep.

## Criteria

| Criterion | Result | Detail |
|-----------|--------|--------|
| Functionality | pass | AC-004.14 to AC-004.16 are implemented as written |
| Completeness | pass | Each AC is named in unit tests and in `WikiIndexIT`. The IT covers redelivery, a late failure, rollback after an insert failure, and a trash during embedding |
| Correctness | pass (conditional) | Not run by the reviewer. The IT waits for the orchestrator's run |
| Integration | pass | The handler type matches the publish enqueue. The Task 1 ports read the chunks filtered by Published |

## Traceability
```
trace wiki (STORY-004): 23/23 acceptance criteria have a test
```
- AC-004.14: `PassageSplitterTest`, `WikiIndexJobHandlerTest`, `WikiIndexIT`.
- AC-004.15 and AC-004.16: `WikiIndexJobHandlerTest`, `WikiIndexIT`.

## Issues
All issues are minor.
1. `backend/src/main/java/vn/tsolve/wiki/service/PassageSplitter.java:40`: minor: the hard cut at 1,000 characters can split a UTF-16 surrogate pair (an emoji or a rare CJK character). Both passages then store a lone surrogate. Fix: before the hard cut, add `if (Character.isHighSurrogate(content.charAt(limit - 1))) return limit - 1;`.

## Assumptions made
- **A1.** AC-004.15 says "keep no passage of that run". This review reads it as allowing passages from an earlier successful run to stay when a later run fails, and the IT asserts exactly that. Documents cannot be edited, so the old passages still match the text.
- **A2.** `mvn` was not run. The pass depends on the orchestrator's `mvn verify`.
- **A3.** The feature status was not changed. If `mvn verify` is green, run `bun harnessclaude/hooks/sdd.ts feature wiki wiki-indexing passing`.

## Test Report
See `task-7-wiki-indexing-test-report.md`.
