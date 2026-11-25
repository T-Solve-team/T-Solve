# Chatbot iteration 4 — decisions

Decisions taken without asking (the user's standing rule: pick the best option, log it here). The spec approval is
the user's check of them.

## 2026-11-25 — RAG measurement (STORY-014, STORY-015)

| Question | Options | Choice | Why |
|---|---|---|---|
| What to measure first | Retrieval only · retrieval + answer text (LLM judge) | Retrieval only, no chat model | Every planned change (hybrid search, thresholds, embedding text) is a retrieval change; deterministic, cheap, no judge to trust |
| Where the evaluation runs | JUnit test excluded from CI · Spring profile runner · admin REST endpoint | Spring profile `rag-eval` that runs and exits | Uses the real beans and the real Workspace database; no endpoint to secure; a test would need the AI server inside `mvn verify` |
| How the runner reaches a Workspace | Request site · `TenantContext.runIn(RAG_EVAL_WORKSPACE_ID)` | `TenantContext.runIn` | One database per Workspace (KAN-98); the same call the jobs use |
| Wiki visibility during the run | Company-wide only · all documents · a named user | The user `RAG_EVAL_USER_ID` through `WikiSources.nearest` | Measures what a real asker gets (AC-005.1); a Knowledge Manager sees the most |
| How gold lines name a source | Ticket key / document title · public UUID | `kind` + public UUID | Unique and already in Ticket Detail and Wiki URLs; titles and ticket keys can repeat |
| Ticket id for a Solution source | New `nearestWithIds` · add `ticketId` to `RetrievedSolution` | Add `ticketId` (search AC-010.2 reworded) | One query, one record; `nearestInProjectDepartment` already returns it; AC-006.3 still keeps it from clients |
| Metrics | recall@k, MRR, no-source rate · plus nDCG | recall within the kept sources, MRR@k, no-source rate, per group | What the review asked for; recall is computed the way the Chatbot keeps sources, so it reflects real answers |
| Threshold during the run | `CHATBOT_MIN_SIMILARITY` · 0 | Retrieve with 0, score against `CHATBOT_MIN_SIMILARITY` | Shows the similarity of the right source when it is below the threshold, which is what tuning the threshold needs |
| Groups (department, source, doc type) | Look up from the database · labels in the gold line | Free `groups` labels in the gold line | No extra queries; any grouping the team wants |
| Where the sources of an answer are stored | Child table `chatbot_answer_source` · `JSONB` column | `sources JSONB NOT NULL DEFAULT '[]'` | Read only for debugging, deleted with the answer, no join needed yet |
| What is stored per source | ids and scores · plus text | `n`, `kind`, `id`, `similarity`, `cited` | No source text or rewritten query (Requirement 11, AC-005.10 spirit) |
| The real gold set | In the repo · outside it | Outside it; only `gold.example.jsonl` in the repo | It names real tickets and documents of one Workspace database |
| Changeset number | — | `056-chatbot-answer-sources.sql`, or the next free number on `main` | 055 is the last on `main` (26/11) |
