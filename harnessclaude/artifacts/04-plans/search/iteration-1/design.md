---
module: search
date: 2026-11-05
iteration: 1
---

# Design: search — iteration 1

Master: `artifacts/04-plans/search/design.md`

Iteration 1 built the web app part of the module first, in three tasks, one per screen and Jira Story: 6.2 Ticket
List (KAN-227), 6.1 Search (KAN-145), 8.1 Give Feedback (KAN-148), on MSW mocks. Every key decision of the master's
web app part (Q1–Q4, Ticket List location, boolean names, cards, date range, `204`, popup errors) is in this
iteration.

Change of 2026-11-05: the backend joins this iteration (spec STORY-005 to STORY-009), because a task is done only
with the real backend and the web app connected. The master's section "Backend" applies in full:

- **Packages:** `vn.tsolve.search.{scope, embedding, list, query, feedback, suggestion}`, each with the layers of
  `backend/AGENTS.md`; `scope` and `embedding` come first (branch 1), then `list`, `query`, `feedback` in parallel
  on disjoint files, then `suggestion` after `query`.
- **Data:** five search changesets (008–012 on today's `main`), one per commit, each with a `--rollback`:
  `public_id` UUID on `ticket`, `project`, `department`; `ticket.updated_at` kept by a trigger; `ticket_embedding`
  (`vector(1024)`); `search_log`; `ticket_feedback`.
- **Visibility:** the port `VisibleScope` with the fail-closed stand-in `NoVisibleScope`, and the dev-only
  `DevAllVisibleScope` when `SEARCH_DEV_SCOPE=all` (local demo); the read model
  `SearchTicket` (`@Immutable @Subselect`); searchable = `PUBLISHED` and an `ACTIVE` Department of the user.
- **Ranking:** bge-m3 query embedding, cosine distance on pgvector, cut-off `SEARCH_MIN_SIMILARITY` (0.5); keyword
  fallback on title, Symptoms, Resolution steps when Ollama fails or times out; one `search_log` row per page-0
  search and per suggestion request.
- **Embedding:** AFTER_COMMIT listener on `TicketsPublishedEvent` → `applicationTaskExecutor`, plus the catch-up
  job (search's own `@EnableScheduling`); never inside a transaction; one batch of `SEARCH_EMBED_BATCH` (4) at a
  time; an id that fails 3 times in a row is skipped until restart.
- **AGENTS rule 2 exception (user, 06/11):** search reads `ticket`, `project`, `department` read-only, contained in
  `SearchTicket` and the native pgvector SQL; the BE lead confirms it at PR review.
- **Contract files:** `tsolve-api.yaml` and the generated client are shared by several branches; those branches merge
  one at a time, each rebased with `npm run generate:api` re-run.
- **Frontend connection:** per screen, `npm run api:pull` (keeping only that feature's differences),
  `npm run generate:api`, drift fixed in the feature, MSW mocks kept for tests and the dev mock mode.

Answered on 05/11: `project.name` is `project.jira_project_key` for now (AC-006.6, one mapping line to change when
project-integration adds a name); `reviewDueDate` is always null (AC-006.7); `resolvedAt` is the start of
`resolved_date` in Asia/Ho_Chi_Minh and nullable in the contract, the card shows "Resolved —" for null (AC-007.5,
AC-002.15).

Not in this iteration: the membership behind `VisibleScope`, the Forge token for suggestions, an ANN index, the
Workspace binding of the background embedding, Wiki retrieval (spec Out of Scope).
