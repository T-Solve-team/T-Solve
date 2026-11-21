# RAG tuning decisions (KAN-377, 22/11/2026)

The user asked to finish the six steps after the eval and to decide each open point by the recommendation, with
quality first, and to list the decisions here for review. Evidence: `README.md` (Runs 1–3), `judge.txt`.
Change any row by telling Claude; the spec and code follow the row.

| # | Question | Options | Choice | Why |
|---|---|---|---|---|
| D1 | Is the LLM judge good enough to decide on? | (a) yes (b) no, wait for people grading real data | (a), with its bias written down | Hand check: 27–28/30 agreement in the 0.60–0.70 band where the decision is made; lenient on loose matches (~1 in 4 of random grade-2 pairs), so "bad" is read as a lower bound |
| D2 | Chatbot cut-off `CHATBOT_MIN_SIMILARITY` | 0.60 / **0.65** / 0.70 (today) | **0.65** | 0.70 gives NO_SOURCE to 22 % of questions that have a helpful ticket; 0.65 cuts that to 9 % for ≥1 % answers from unhelpful sources. 0.60 (missed 3 %, bad ≥3 %) puts an employee in front of more wrong answers, and the judge's leniency grows below 0.65. Quality first → 0.65 |
| D3 | Triage cut-off `SUGGESTION_MIN_SIMILARITY` | **0.60** / 0.65 / 0.70 | **0.60** | The suggestion is a Jira comment an IT agent reads next to its sources, not an answer to an employee; a missed suggestion costs more than a weak one. Triage spec planned this tuning "on KAN-49 data later": the setting stays an env variable, so that round can still move it |
| D4 | Build the reranker (`bge-reranker-v2-m3`)? | (a) build now (b) postpone (c) drop | **(b) postpone** | No measurable gain: good@1 0.85 = 0.85 (VI), 0.92 = 0.92 (same language); its score separates answerable questions no better than cosine. It would add a service on the AI server, a client, fallbacks and ~0.5 s per question. Revisit only if real data shows bad answers whose right ticket is ranked 2–5 |
| D5 | Hybrid BM25 + cosine for Solution retrieval | (a) build (b) postpone (c) drop | **(c) drop** | Worse at the top in all three runs (good@1 −0.10 to −0.12). Not related: the Wiki search page keeps its keyword + meaning ranking (W8), which serves search, not the chatbot |
| D6 | Multi-query + HyDE | (a) build (b) postpone (c) drop | **(b) postpone** | +0.01 good@1 for one more LLM call per question; worse in Run 1. Follow-up questions already get a rewritten query (KAN-243). Revisit if real data shows short or vague questions missing |
| D7 | Small-to-big retrieval for the Wiki | measure now / later | **later** | The dataset has no Wiki documents; measure on real uploaded documents once typed chunking (KAN-374) is merged |
| D8 | One cut-off for tickets and Wiki passages in the chatbot | (a) one setting (b) two settings | **(a) one** | AC-005.1 already uses one; there is no Wiki data to tune a second. Risk: passage similarity may sit lower than whole-ticket similarity; check NO_SOURCE answers for Wiki questions after deploy |
| D9 | BUG-335 (chat calls time out on staging) | (a) only infra (b) infra + the client sends `"think": false` | **(b)** | qwen3 hybrid models think by default; Ollama turns it off only when the request says `"think": false`. Sent on every call (ai AC-001.1), a hybrid model such as `qwen3:14b` answers without a ~1,300-token thinking block. `qwen3:4b` (now the 2507 thinking build) ignores it, so the env must name a non-thinking or hybrid model. The GPU server itself is platform Task 22 (Hiếu, KAN-270) |
| D10 | Which chat model on the GPU | the team note's Qwen3-14B Q4_K_M / `qwen3:4b-instruct-2507-q4_K_M` | **not decided here** | KAN-140 owns the model choice (`SEP490_G89/Notes/Chon_Model_AI.md` §5 plans Qwen3-14B). Either works with D9; the eval ran on the 4B instruct model |
| D11 | Jira and branch | one issue per step / **one Task** | **KAN-377** (Task under E09 AI Module, KAN-85), branch `feature/KAN-377-ai-rag-tuning` from `origin/main` | One change set: two cut-offs and one client flag. Local `main` is 199 commits behind, so the branch starts from `origin/main` |
| D12 | Where to work | switch the main checkout / **a worktree** | `.claude/worktrees/KAN-377-rag-tuning` | The main checkout is on `feat/KAN-376-wiki-view-images` with your open BUG-356 row; switching would carry or conflict it |
| D13 | The uncommitted "AI server has a GPU" doc edits | one commit / **split by branch** | KAN-374-only file (`doc-type-chunking-decisions.md`) committed alone on the KAN-376 branch (`e89a7627`); everything else committed alone on KAN-377 (`c53167aa`) | Keeps each commit on the branch whose files it touches; nothing mixed into a feature commit |
| D14 | Features and commits | one commit for all / **one per feature** | `ai/chat-no-thinking`, `chatbot/rag-cut-off`, `triage/suggestion-cut-off`: each a code commit plus a harness commit | Project rule: one feature per commit |
| D15 | Dataset files in git | commit / **ignore** | `.gitignore` keeps `*.jsonl`, `*.npy`, the CSV and `borderline.txt` local | They hold the public dataset's tickets; scripts and results are enough to re-run |

## After deploy (step 6, needs the AI server of Task 22)

1. AI server: Ollama on the GPU with `bge-m3` and the KAN-140 chat model (D10); `OLLAMA_NUM_PARALLEL` above 1 so an
   embed does not queue behind a chat (BUG-335 log). No reranker service (D4).
2. Staging `.env`: `CHATBOT_MIN_SIMILARITY=0.65`, `SUGGESTION_MIN_SIMILARITY=0.60` (or leave them unset: these
   become the defaults).
3. Re-check BUG-335 with its own steps; then set its status.
4. After a week of use: the NO_SOURCE share on the Knowledge Dashboard and the answer ratings. Many NO_SOURCE with a
   ticket that exists → lower the cut-off by 0.05; many thumbs-down on answered questions → raise it, or reopen D4.
