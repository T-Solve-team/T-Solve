---
id: RESEARCH-006
title: Classify Chatbot questions before searching (KAN-243)
question: How should T-Solve classify a Chatbot question (greeting, off-topic, prompt injection, real question) and rewrite it into a search query before the search, if KAN-243 is built?
status: complete
date: 2026-11-17
related: [chatbot, dashboard-audit, ai, KAN-243, KAN-179, KAN-181]
---

# Classify Chatbot questions before searching (KAN-243)

## Research Question
If the team builds KAN-243 ([UC-51, GBR-39] "Chatbot classifies questions before searching"), how should it be
built on the current Chatbot code, and at what cost?

## Context
- KAN-243 is **Done in Jira, but only as "Dropped"**. A comment on 16/11 says the team decided not to classify
  questions before searching, that no code was built, and that every `NO_SOURCE` answer counts as a knowledge gap.
  The comment says the harness artifacts were updated on the branch `docs/KAN-243-chatbot-drop-classification`.
  That branch and its commits are not in this checkout (checked 17/11). This checkout still names KAN-243 as
  out of scope in `chatbot/spec.md` and as "not built yet" in `dashboard-audit/spec.md`.
- The user asked on 17/11 for this spike to cover **how to build it**, in case the story comes back.
- The story's acceptance criteria (from Jira):
  1. Greeting or thank-you: fixed reply MSG143, with no AI model call.
  2. Off-topic question: refused with MSG142 and not searched.
  3. Attempt to change the Chatbot's instructions (prompt injection): MSG154, and not searched.
  4. Any other question: rewritten into a short stand-alone search query (filler removed, follow-ups combined with
     the session context). The query is used only for the search and is never shown.
  5. A general question that no source answers: MSG109.
  6. Refused questions and greetings are not counted as knowledge gaps (dashboard UC-34).
  - Depends on KAN-179 and KAN-181. Source: "Report_3_BreakDown" UC-51, GBR-39.

## Findings

### The current pipeline (facts)
`ChatbotAnswerServiceImpl.ask` (`backend/src/main/java/vn/tsolve/chatbot/answer/service/impl/`):
1. Trims the question. Builds the retrieval text: the question alone, or the previous turn's question plus a
   newline plus this question. A `ponytail:` comment marks this as the place for an LLM query rewrite.
2. Embeds the text (`EmbeddingClient`), then searches Solutions (`SolutionRetrieval.nearest`) and Wiki
   (`WikiSources.nearest`) with `CHATBOT_MIN_SIMILARITY` (default 0.7) and `CHATBOT_SOURCE_LIMIT` (default 5).
3. With no source: saves `NO_SOURCE` and makes no LLM call.
4. Otherwise: one `LlmClient.chat` call with `AnswerPrompt`. The question, earlier turns and sources are escaped
   and wrapped in tags, and the system prompt says that tagged text is data, never instructions. If the answer
   cites no source, the outcome is `NO_SOURCE`.
5. All AI calls share one deadline (`CHATBOT_ASK_TIMEOUT`, default 60 s). A timeout or provider failure throws
   `AiUnavailableException`.

Other facts that matter here:
- `LlmClient.chat(messages, jsonSchema)` can already constrain the answer to a JSON schema (Ollama `format`).
  `SolutionAiServiceImpl` uses this today. A classifier needs no new AI client code.
- The chat model is self-hosted Ollama (`OLLAMA_CHAT_MODEL`, no default). Each extra call adds a full local
  model round trip.
- `ChatbotAnswerResponse.Outcome` is `ANSWERED | NO_SOURCE`. The DB column is `chatbot_answer.outcome
  VARCHAR(20)` with `CHECK (outcome IN ('ANSWERED', 'NO_SOURCE'))` (changelog `013-chatbot-answer.sql`).
- The dashboard's knowledge gaps (`DashboardRepository`) count only `outcome = 'NO_SOURCE'`. Any new outcome
  value is therefore left out of the gaps with **no dashboard change** (criterion 6 comes for free).
- The frontend `ChatAnswer.tsx` shows `answer.text` for `ANSWERED` and `msg('MSG109')` for anything else.
  `model.ts` sends `''` as the answer of non-`ANSWERED` turns in the history.
- Criterion 5 (MSG109) is today's behaviour already.

### Message ids conflict (fact)
In `frontend/src/shared/messages/catalog.ts`, **MSG142 is "No Spaces found"** and **MSG143 is "Not a member of
any Project."**. MSG154 does not exist. The baseline SRS (`00-baseline/…_v3460.docx`) and
`System_Messages.docx` define neither GBR-39 nor these three messages, and "Report_3_BreakDown" is not in the
repo. The texts of the three messages are unknown, and two of the ids are taken.

### Option A: rules only (keyword and pattern lists)
- Greeting and thank-you: a short message that matches a small list ("hi", "hello", "thanks", "xin chào",
  "cảm ơn" …) after lower-casing and stripping punctuation. This is cheap, makes no AI call, and is required
  by criterion 1 in any case.
- Off-topic and injection detection by keywords has poor recall. Users write in Vietnamese and English, and
  injection has unlimited wording ("ignore previous instructions", role-play, encoded text). Lists need upkeep.
- Rewriting a follow-up into a stand-alone query is not possible with rules. It stays the current concatenation.
- Cost: no latency. Quality: low for criteria 2–4.

### Option B: one structured LLM call before the search
One `llm.chat` call with a JSON schema such as
`{ "category": "GREETING" | "OFF_TOPIC" | "INJECTION" | "QUESTION", "query": string }`, given the question and
the last turns (escaped and tagged as data, as `AnswerPrompt` does).
- `QUESTION` → embed `query` instead of today's concatenation. This replaces the `ponytail:` comment.
- Other categories → save the matching outcome, return the fixed message, no search.
- Criterion 1 says greetings make **no AI call**, so B alone breaks it. B needs a rule pre-filter for greetings
  (Option A's greeting list).
- Cost: one extra model round trip on **every** question, including those that today end as `NO_SOURCE` with no
  LLM call at all. On a local Ollama model this can be the largest share of the 60 s budget.
- Risk: the classifier is an LLM, so it can itself be injected. That limits the damage to a wrong category; the
  answer prompt keeps its own "tagged text is data" defence. A false `OFF_TOPIC` on a real question hides
  knowledge from the user and also hides a real gap from the dashboard.

### Option C (hybrid): greeting rules, then Option B
1. Rule pre-filter for greeting and thank-you → fixed reply, no AI call (criterion 1).
2. One structured call that classifies and rewrites at the same time (criteria 2–4). One call, not two.
3. If the classifier output is not valid JSON or has an unknown category: fall back to `QUESTION` with today's
   concatenated text (fail open to search). The answer prompt still treats the question as data, and a gap is
   still recorded. A timeout keeps today's behaviour (`AiUnavailableException`, MSG06).

### What changes (for Option C)
- Backend: `Outcome` gains values (for example `GREETING`, `OFF_TOPIC`, `INJECTION`, or one `REFUSED` plus a
  reason). Each value fits `VARCHAR(20)`. A new Liquibase changeset widens the check constraint. A new
  `QuestionPrompt` sits next to `AnswerPrompt`, and the classifier call goes in `ask` before the embedding.
- Frontend: `ChatAnswer.tsx` maps each outcome to its message instead of "anything else → MSG109".
  `npm run generate:api` updates the generated enum. The MSW mock handler gains the new outcomes.
- Dashboard: no change (filters on `NO_SOURCE`).
- Spec: new acceptance criteria in `chatbot/spec.md`. The out-of-scope lines in `chatbot/spec.md` and
  `dashboard-audit/spec.md` change. The spec must be approved again before code.
- Tests: a fake `LlmClient` returning each category. Greeting path asserts zero LLM and embedding calls. Fallback
  on bad JSON.

## Recommendations
- If KAN-243 comes back, build **Option C**: a greeting rule list, then one structured classify-and-rewrite call
  through the existing `LlmClient` JSON-schema support, failing open to search.
- Store the refusal as its own `outcome` value. The dashboard then needs no change.
- Settle the message ids and texts first (see Open Questions). Do not reuse MSG142/MSG143.
- Measure the classifier's latency on the real `OLLAMA_CHAT_MODEL` before committing to it. If it costs too much
  of the 60 s budget, ship only the greeting rules (criterion 1 and part of 6) and keep the current concatenation
  for follow-ups.
- These are recommendations, not decisions. The team's 16/11 decision to drop KAN-243 stands until the team
  changes it.

## Decisions (user, 17/11)
KAN-243 is back in Jira "To Do" (label `must`, updated 17/11). The user decided:
- **Revive KAN-243** into the chatbot spec (plan skill, Change Mode), built as **Option C**.
- **New message ids**, not the Jira ids:
  - MSG_NEW_5 (greeting): "Hi! Ask me about your team's Solutions or Wiki."
  - MSG_NEW_6 (off-topic): "I can only answer questions about your team's knowledge."
  - MSG_NEW_7 (injection): "I can't change how I work. Ask a question about your team's knowledge."
- **Outcome**: three new values `GREETING`, `OFF_TOPIC`, `INJECTION` beside `ANSWERED` and `NO_SOURCE`. The
  dashboard gaps keep counting `NO_SOURCE` only.
- **Fallback**: a classifier answer that is not valid JSON or has an unknown category is treated as `QUESTION`
  with today's concatenated text (fail open). A timeout keeps `AiUnavailableException` (MSG06).
- **Ratings**: no rating on `GREETING`, `OFF_TOPIC` or `INJECTION` turns.

## Open Questions
- The branch `docs/KAN-243-chatbot-drop-classification` is not in this checkout. Revival supersedes it; it should
  not be merged.
- Not in scope (YAGNI): logging refusals to the audit log. Add when a security review asks for it.

## Related Artifacts
- [chatbot spec](../03-modules/chatbot/spec.md), [chatbot module](../03-modules/chatbot/module.md)
- [dashboard-audit spec](../03-modules/dashboard-audit/spec.md): knowledge gaps count `NO_SOURCE` only
- [module map](../03-modules/module-map.md): KAN-243 row
- Jira: [KAN-243](https://t-solve.atlassian.net/browse/KAN-243)
