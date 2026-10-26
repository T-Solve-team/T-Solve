---
module: search
date: 2026-10-25
iteration: 1
---

# Design: search — iteration 1

Master: `artifacts/04-plans/search/design.md`

Iteration 1 builds the web app part of the module in three tasks, one per screen and Jira Story: 6.2 Ticket List
(KAN-147), 6.1 Search (KAN-145), 8.1 Give Feedback (KAN-148), frontend only with MSW mocks. The master design
applies in full; every key decision of the master (Q1–Q4, Ticket List location, boolean names, cards, date range,
`204`, popup errors) is in this iteration.

The backend `search` package (visibility rules, embeddings, ranking, Search Log, stored Feedback) and the
Issue Panel suggestions (KAN-146) are not in this iteration; they come with a change of the spec.
