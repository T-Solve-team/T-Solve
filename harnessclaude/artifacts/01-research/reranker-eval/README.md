# RAG retrieval eval (research spike, 22/11/2026)

Measures which RAG improvements help T-Solve before any spec or product code changes.
Corpus: 600 tickets sampled (seed 42) from `dataset-tickets-multi-lang3-4k.csv` (en, de, es, fr, pt), each embedded
as one text like `EmbeddingText.of`. Questions: 100 tickets rewritten by the chat model as an employee's question;
the gold answer is that ticket.

| Script | Does |
|---|---|
| `gen_queries.py` | `corpus.jsonl` and `queries.jsonl` (Vietnamese) or, with `--same-language`, `queries_same.jsonl` |
| `gen_variants.py <queries file>` | 3 rewrites + 1 hypothetical answer per question, in one call (multi-query + HyDE) |
| `evaluate.py [--rerank] [--queries f]` | cosine, bm25, hybrid RRF, mq_hyde, mq_hyde+bm25, each also `+rerank` (bge-reranker-v2-m3 on the top 20) |
| `run_all.sh <csv>` | everything above, on the GPU server; results in `results_<model>.txt` |
| `analyze.py [--rerank]` | cut-off sweep against single gold tickets (Run 2) |
| `judge.py` | LLM-as-judge: grades every top-5 ticket 0/1/2 (cached in `judgments.jsonl`), good@1/good@5/nDCG@5 and the cut-off sweep over all 300 questions (Run 3) |
| `borderline.py [low high n]` | a random sample of top-5 pairs in a cosine band with the judge's grade, for a person to re-grade |

Data files (`*.jsonl`, `*.npy`, the CSV) are git-ignored: they hold the dataset's tickets.

Settings: `OLLAMA_URL` (default `http://localhost:11434`), `CHAT_MODEL` (default `qwen3:4b`; use a non-thinking
model such as `qwen3:4b-instruct-2507-q4_K_M`: `qwen3:4b` thinks ~1,300 tokens per call even with `think:false`, ~29 s per
question on an RTX 3060, and is the cause of BUG-335).

Metrics: hit@1, hit@5, MRR@10, and kept@0.7 = gold in the top 5 with cosine >= 0.7 (what `CHATBOT_MIN_SIMILARITY`
lets through today).

## Early result (dev laptop, 60 Vietnamese questions from `qwen3:4b`, files in `local-run/`)

| method | hit@1 | hit@5 | MRR@10 | kept@0.7 |
|---|---|---|---|---|
| cosine (today) | 0.52 | 0.83 | 0.66 | 0.65 |
| bm25 | 0.13 | 0.30 | 0.22 | 0.27 |
| hybrid RRF | 0.27 | 0.68 | 0.43 | 0.55 |

15/60 questions have no source >= 0.7, so the product would answer NO_SOURCE although cosine ranks the gold in the
top 5 for 83 %. BM25 is weak here because the questions are Vietnamese and the tickets are not: the same-language
set (`queries_same.jsonl`) is the fair test for hybrid search.

## Run 1 on the GPU server (RTX 3090, 22/11/2026, chat model `qwen3:4b-instruct-2507-q4_K_M`)

Full output: `results_qwen3_4b-instruct-2507-q4_K_M.txt` (the line "on this CPU" was a mislabel, since fixed in `evaluate.py`: it ran on the GPU).

| method | VI hit@1 | VI hit@5 | VI kept@0.7 | same-lang hit@1 | same-lang hit@5 | same-lang kept@0.7 |
|---|---|---|---|---|---|---|
| cosine (today) | **0.43** | **0.70** | 0.50 | **0.57** | 0.77 | **0.65** |
| hybrid RRF | 0.29 | 0.63 | 0.44 | 0.35 | 0.69 | 0.59 |
| mq_hyde | 0.38 | 0.65 | 0.46 | 0.46 | 0.70 | 0.59 |
| cosine+rerank | 0.38 | 0.64 | 0.45 | 0.48 | **0.78** | 0.64 |
| hybrid+rerank | 0.39 | 0.63 | 0.45 | 0.50 | 0.77 | 0.62 |

Reading (before the follow-up `analyze.py`):
1. The 0.7 cut-off loses the most: 36/100 (VI) and 20/100 (same language) questions get NO_SOURCE although cosine
   ranks the gold in the top 5 for 70 % / 77 %.
2. Hybrid RRF (equal weights) and multi-query + HyDE are worse than cosine alone on every metric: not worth building
   on this evidence.
3. The reranker keeps hit@5 but lowers hit@1. Open question: are its top-1 "misses" near-duplicate tickets (the
   dataset has many)? Latency: 0.53 s median for 20 tickets on the 3090 in fp32.

Follow-up: `gen_queries.py --negatives` (100 questions with no answer in the corpus) and `analyze.py --rerank`
(cut-off sweep for cosine and reranker score; near-duplicate check of top-1 misses).

## Run 2: cut-offs and misses (`analysis.txt`, 22/11/2026)

| cosine cut-off | right (VI) | none (VI) | no-answer questions answered |
|---|---|---|---|
| 0.55 | 0.70 | 0.03 | 0.97 |
| 0.60 | 0.68 | 0.11 | 0.89 |
| 0.70 (today) | 0.50 | 0.36 | 0.68 |

1. The 0.7 cut-off does not separate answerable from unanswerable questions: it drops 36 % (VI) / 20 % of questions
   that have an answer and still lets 68 % of questions with no answer through. The reranker score separates them
   no better (0.5 → 78 % of no-answer questions still answered).
2. The dataset itself blurs both numbers: it holds the same ticket in several languages and many near-identical
   tickets (e.g. "Urgente: Desconexiones de Zoom 5.11.0" and "Urgent: Zoom 5.11.0 Disconnections"). Many top-1
   "misses" are right answers, and many "no-answer" questions do have a matching ticket. Ticket-to-ticket cosine
   >= 0.9 catches only 6–10 of 43–62 misses, because a translation scores lower than that.
3. Some `queries_same.jsonl` questions came out in Vietnamese anyway (the prompt says "a Vietnamese company").

Conclusion so far: single-gold metrics on this dataset cannot decide the cut-off or the reranker. Next: judge the
relevance of every retrieved ticket (LLM as judge, or people on real T-Solve data), not only the one gold ticket.

## Run 3: graded relevance with an LLM judge (`judge.txt`, 22/11/2026)

`judge.py` asked `qwen3:4b-instruct-2507-q4_K_M` to grade each of the 2,811 (question, ticket) pairs that any
method put in its top 5: 2 = same problem, the answer would help; 1 = related, different problem; 0 = unrelated.
A question counts as answerable when any pooled ticket got 2. 39 minutes on the RTX 3090 with 4 parallel slots.

| set | answerable | cosine good@1 / good@5 | rerank | hybrid | mq_hyde |
|---|---|---|---|---|---|
| Vietnamese questions | 91 % | **0.85** / 0.88 | 0.85 / 0.88 | 0.73 / 0.86 | 0.86 / 0.89 |
| same-language questions | 96 % | **0.92** / 0.95 | 0.92 / 0.95 | 0.82 / 0.93 | 0.92 / 0.96 |
| "no-answer" questions | 91 % | 0.83 / 0.90 | 0.82 / 0.91 | 0.74 / 0.86 | — |

Cut-off over all 300 questions (good = a kept source is graded 2; bad = sources kept, none graded 2;
missed = nothing kept although a grade-2 ticket exists; refused = nothing kept, none exists):

| cosine cut | good | bad | missed | refused | | reranker cut | good | bad | missed | refused |
|---|---|---|---|---|---|---|---|---|---|---|
| 0.55 | 0.90 | 0.08 | 0.01 | 0.01 | | 0.05 | 0.88 | 0.04 | 0.03 | 0.05 |
| 0.60 | 0.89 | 0.03 | 0.03 | 0.05 | | 0.10 | 0.87 | 0.03 | 0.04 | 0.06 |
| **0.65** | **0.83** | **0.01** | **0.09** | 0.07 | | 0.30 | 0.83 | 0.02 | 0.09 | 0.06 |
| 0.70 (today) | 0.71 | 0.00 | 0.22 | 0.07 | | 0.50 | 0.78 | 0.01 | 0.14 | 0.07 |

Check of the judge (graded by hand, Claude, from `borderline.py` and a random sample):
- Cosine band 0.60–0.70, 30 random top-5 pairs: hand and judge agree on 27–28; one clear false 2 (a billing
  question matched to a printer-jam ticket). 17/30 pairs in this band are genuinely helpful: the 0.70 cut throws
  them away.
- 25 random grade-2 pairs: about 18 right, the rest are the same product but a different symptom (should be 1).
  The judge is lenient by roughly a quarter on loose matches, so "good" is an upper bound and "bad" a lower bound;
  the gap grows as the cut-off goes down.
- 91 % of the "no-answer" questions do have a helpful ticket: the dataset repeats each problem in many tickets and
  languages, so this set does not test refusals. `refused` stays near 0.07 whatever the cut-off.

Reading:
1. The cut-off, not the ranking, is the problem: cosine already puts a helpful ticket first for 85–92 % of
   questions, but 0.70 keeps none for 22 % of all questions. 0.65 recovers most of them (missed 0.09) for about one
   bad answer in a hundred (a lower bound); 0.60 recovers more (missed 0.03) at three or more in a hundred.
2. The reranker changes nothing measurable here (good@1 equal on every set) and costs a service and ~0.5 s.
3. Hybrid RRF is worse at the top (good@1 −0.10 to −0.12); multi-query + HyDE gains at most 0.01 for one more LLM
   call per question.

Decisions taken from this: `decisions.md`.

## Limits
No real Wiki documents (small-to-big is not measured); synthetic questions from one ticket each (near-duplicate
tickets count as misses); latency is only meaningful on the GPU server.
