# Test report: chatbot — Task 11: Rating columns (answer-rating-schema, KAN-182)

Date: 2026-11-09 · Commit under review: `b9079eb` (the files of Task 11 have no diff to `HEAD` `6555eba`)

## Reviewer run
Run in `backend/`:
`mvn -q verify -DargLine="-Dapi.version=1.44" -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false
"-Dit.test=ChatbotRatingSchemaIT,ChatbotAnswerTimeoutIT,ReviewStorageIT,ChatbotSchemaIT,SearchChangesetRollbackIT"
-Dspotless.check.skip=true`. Exit code 0.

| IT | Run | Failures | Errors | Time |
|----|-----|----------|--------|------|
| `vn.tsolve.chatbot.rating.ChatbotRatingSchemaIT` | 2 | 0 | 0 | 9.6 s |
| `vn.tsolve.chatbot.ChatbotAnswerTimeoutIT` | 1 | 0 | 0 | 60.7 s |
| `vn.tsolve.review.ReviewStorageIT` | 4 | 0 | 0 | 25.5 s |
| `vn.tsolve.chatbot.ChatbotSchemaIT` | 3 | 0 | 0 | 13.5 s |
| `vn.tsolve.search.SearchChangesetRollbackIT` | 1 | 0 | 0 | 6.0 s |

## Controller run (reported, not re-run)
- `mvn clean verify -DargLine="-Dapi.version=1.44"`: 939 unit tests and 302 ITs, 0 failures.
- `spotless:check` cannot run in the git worktree (JGit: `Cannot find git repository`). A check of the 14 changed
  Java files without the ratchet is clean.

## Traceability
`bun harnessclaude/hooks/sdd.ts trace chatbot STORY-008`: 6/6. AC-008.6 is covered by `ChatbotRatingSchemaIT.java`.

## Coverage
This is a schema-only task, so there is no line-coverage figure. Every clause of AC-008.6 has an assertion: the
types, nullability, the allowed values, both directions of "both null or both set", and the rollback and re-apply.
