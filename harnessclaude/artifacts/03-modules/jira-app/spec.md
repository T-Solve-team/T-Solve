---
module: jira-app
date: 2026-11-07
owner: BA
depth: Normal
---

# Spec: jira-app

The T-Solve Jira App (Forge UI Kit, `jira-app/`) and the Jira App API endpoints it calls
(`/api/v1/jira-app/**`). STORY-001 to STORY-008 (KAN-126): Solution Form, AI Rewrite, Issue Panel (STORY-003, STORY-006
and STORY-009 retired). STORY-010 (KAN-294): Ask T-Solve page. Sources: Report3 (UC-19, GBR-04, 06, 08, 10, 12, 19, 26,
33, 36, System Messages), RESEARCH-003, RESEARCH-004, the `ticket-review` spec (STORY-009, STORY-012), the `chatbot`
spec (STORY-005 to STORY-007). Terms: **user** = the active T-Solve Account linked to the Jira account in the FIT
`principal`; **Project** = the ACTIVE T-Solve Project whose `jira_project_key` is the issue's project key;
**member** = a user with a `project_membership` row in that Project (`MEMBER` or `PROJECT_MANAGER`); **form** = 4.1
Solution Form; **panel** = 3.3 Issue Panel; **Solution Form ticket** = a `ticket` row with source `SOLUTION_FORM`,
the one `ticket-review` reviews; **the issue's ticket** = the `ticket` row whose Ticket ID is the issue key, whatever
its source (a Solution Form ticket, a Jira import or a CSV import) — Ticket IDs are unique (`uq_ticket_ticket_id`), so
an issue has at most one.

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-07 | STORY-010 (new) | Add STORY-010 "Hỏi T-Solve" page (KAN-294): ask the Knowledge Chatbot inside Jira before creating an issue, then "Solved" or a prefilled Jira Create dialog | RESEARCH-004 Option C. User decisions of 07/11: answers come from the `chatbot` module's answer service with the same sources as the web chatbot (all Solutions and Wiki the user may see, not only the open Jira project); only Jira accounts linked to a T-Solve user ask; the Create dialog is prefilled with the questions and what was tried; "Solved" is UI only until chatbot ratings (KAN-182); the created issue gets label `tsolve-asked` so the tracker's suggestion job skips it. Written on its own branch from `main` (user, 07/11): STORY-001..009 stay on KAN-126 |
| 2026-11-08 | STORY-010 | Add `GET /api/v1/jira-app/questions/eligibility` to the Exposed interfaces; no criterion changes | Planning builder check: AC-010.2 shows MSG62 before any question, which the POST alone cannot tell the page (user chose this over rewording AC-010.2, 08/11) |
| 2026-11-08 | STORY-010 | AC-010.6 reworded: sources shown like the web chatbot — "[n] <title> · Wiki document", "[n] Solution from a ticket" with its Solution, no ticket id, link or Project. AC-010.9: the sources in the Create dialog use the same labels, without Solution text | Task 5 found that AC-010.6 ("Ticket ID and title of each Solution") contradicts GBR-36 and AC-010.4: a `TicketSolutionCitation` carries only the Solution. User chose to match the web (08/11) |
| 2026-11-08 | STORY-010 | AC-010.1: the page title is "Ask T-Solve" (was "Hỏi T-Solve"); requirement 5, the terms and the Exposed table follow. The other page labels stay Vietnamese | User decision in the Task 6 dev-site check (08/11) |
| 2026-11-11 | STORY-010 | Every page and Create-dialog label in English: AC-010.1 note "Answers from T-Solve Solutions and Wiki"; AC-010.2, 010.6 to 010.10 buttons "Solved" and "Not solved, create ticket", the solved text "Your problem is solved. Thank you!", the description parts "Tried with T-Solve:" and "Sources:". The card heading "Ask T-Solve before creating a ticket", the placeholder "Describe your problem" and the "Send" button follow | User decision (11/11): the Jira App is English only, like the page title |
| 2026-11-11 | STORY-001..009 | Port STORY-001 to STORY-009 from `feat/KAN-126-jira-app-solution-form` (spec of 01/11, Evidence removed, STORY-003 stays retired) and re-base them on `main`: the user and Project membership come from `project_membership`, the linked Account from the Jira App link stand-in; Finish and Close create a Solution Form ticket that enters `ticket-review`'s Review Queue (AC-004.1, AC-006.2); the panel also shows a change request (MSG64, new AC-008.7) through `ticket-review`'s feedback endpoint; panel search reuses the `search` module's ranked ticket search; the AI draft and rewrite call the `ai` module's chat client with a configured stand-in | KAN-126 resumed on a fresh branch from `main` (user, 11/11): the old branch was 61 commits behind and its ticket services predate `ticket-review`'s ticket storage. User decisions 11/11: show MSG64, reuse search, use the ai chat client, keep Evidence out |
| 2026-11-13 | STORY-001, STORY-004, STORY-008 | New term "the issue's ticket" (the ticket whose Ticket ID is the issue key, of any source). AC-001.2 to AC-001.5, AC-004.3, AC-008.2 and AC-008.3 use it instead of "a Solution Form ticket … in that Project"; AC-004.3 names MSG31. AC-004.1, AC-006.2 and AC-008.7 still mean the Solution Form ticket | KAN-126 PR review: the criteria said only a Solution Form ticket blocks the form, but a Jira-import or CSV ticket with the same key does too, because `ticket.ticket_id` is unique across sources and Projects (changeset 003). A second ticket for the key cannot be stored, so the spec follows the schema; the code (`TicketRepository.findStatusByTicketId`) already does this. User chose to reword the spec, not filter by source (13/11) |
| 2026-11-14 | STORY-008, STORY-009 | Remove the Published-ticket search from the Jira App: AC-008.4, AC-008.5, AC-008.6 and AC-008.8 are removed, STORY-009 (3.4 Published Ticket Preview, AC-009.1, AC-009.2) is retired. The panel keeps the Solution status only (AC-008.1 to AC-008.3, AC-008.7); `GET /api/v1/jira-app/tickets/search`, `GET /api/v1/jira-app/tickets/{ticketId}` and the panel's "search allowed" flag are removed; `search` is no longer a dependency. Out of Scope gains the search | User decision in the KAN-126 dev-site test (14/11): no Published-ticket search in Jira; it stays on the T-Solve web app. The SRS puts UC-26 search, 3.4 and MSG76 in the Jira App — this is a deviation the user will agree with the team. User chose to remove the UI and the backend endpoints, so no code is left that nothing calls |
| 2026-11-14 | STORY-005, STORY-006 | Close keeps a Draft instead of submitting an empty ticket: AC-005.1 reworded (closing the form — Close or the dialog's close control — stores the Draft and shows MSG41), new AC-005.5 (no Save as draft button, no ticket on close, the server never stores an empty Solution); STORY-006 retired (AC-006.1, AC-006.2, AC-006.3 removed, MSG70 no longer shown); the solution endpoint no longer takes "all empty = Close". The Draft reminder email moves to a separate issue (Out of Scope) | KAN-126 dev-site test (14/11): the team's Review Queue never holds an empty ticket, so the empty Solution of AC-006.2 must not reach it; with Close saving the Draft a separate Save as draft button is redundant (user 14/11). Deviation from the SRS (Save as draft button, MSG70) to agree with the team |

## Requirements

### Functional
1. The form opens by itself when an issue of a Project moves to a Done status, only for members of that Project,
   and only while the issue has no ticket of any source (STORY-001).
2. The form edits the five-section Solution, pre-filled by a Draft, the AI draft or the issue; it can Finish or
   Close, and Close keeps the content as the user's Draft (STORY-002, STORY-004, STORY-005). Only Finish hands a
   ticket to `ticket-review`.
3. Rewrite with AI shows 4.2 AI Rewrite Preview (STORY-007).
4. The panel shows the issue's Solution status, including a Project Manager's change request (STORY-008).
5. An "Ask T-Solve" page in each Jira project lets a linked user ask the Knowledge Chatbot before creating an issue;
   "Not solved, create ticket" opens Jira's Create dialog prefilled with the conversation (STORY-010). Requirements 1
   to 4 belong to KAN-126.

### Non-Functional
1. Every Jira App API call is verified with a FIT (tracker STORY-001); the user comes from the FIT `principal`.
2. UI calls answer within 20 seconds (Forge's limit is 25 s); an AI call that takes longer counts as unavailable.
3. UI Kit components only; every server call goes through one client module in `jira-app/`; logs carry ids,
   issue keys and error types only — never question, answer or Solution text, or personal data.
4. AI failures never block the user: the form and creating an issue stay usable without AI (GBR-12).

## User Stories

### STORY-001: Open the Solution Form when an issue is Done
- As a Ticket Resolver, I want the Solution Form to open when I move an issue of my Project to Done so that I write the Solution while I still remember it.
- Acceptance:
  - AC-001.1: WHEN the issue being viewed changes to a status of the Done status category, THE Jira App SHALL ask the server whether the form may open and open the form when it may.
  - AC-001.2: THE server SHALL allow the form only when the issue's project key is a Project's, the user is a member of it, and the issue's ticket does not exist.
  - AC-001.3: IF the user is not linked to an active T-Solve Account, is not a member, or the issue's ticket already exists, THEN THE Jira App SHALL not open the form.
  - AC-001.4: WHILE the issue is in a Done status, the user may open the form and the issue's ticket does not exist, THE panel SHALL show a button that opens the form.
  - AC-001.5: IF an issue whose ticket already exists is reopened and moved to Done again, THEN THE Jira App SHALL not open the form (GBR-04).

### STORY-002: Write the Solution
- As a Ticket Resolver, I want to write the Solution in the five sections of the template so that it can be reviewed and reused.
- Acceptance:
  - AC-002.1: WHEN the form opens without a Draft, THE Jira App SHALL fill the five sections — Symptoms, Root cause, Resolution steps, Workaround, Scope of applicability — with the AI draft the server makes from the issue summary and description.
  - AC-002.2: IF the AI draft is unavailable or takes longer than the UI call allows, THEN THE Jira App SHALL fill Symptoms from the issue summary and description, leave the other sections empty, and show MSG67.
  - AC-002.3: WHEN the user clicks Finish with a required section empty (every section except Workaround), THE Jira App SHALL show MSG01 under that section and not submit.
  - AC-002.4: WHEN the user clicks Finish and the five sections together exceed 10,000 characters, THE Jira App SHALL show MSG02 and not submit.
  - AC-002.5: THE server SHALL reject a submission with an empty required section (400 MSG01) or more than 10,000 characters in total (400 MSG02), whatever the Jira App checked.

### STORY-004: Finish
- As a Ticket Resolver, I want to submit the Solution so that it enters the Project's Review Queue.
- Acceptance:
  - AC-004.1: WHEN the user clicks Finish with a valid Solution, THE server SHALL create exactly one Solution Form ticket in Pending review for the issue — source ticket id = the issue key, title = the issue summary, the Project, the Solution and the user as writer — that enters `ticket-review`'s Review Queue with its entry time and review due date set as `ticket-review` requires (its AC-009.11, GBR-08, GBR-10), and SHALL delete the user's Draft for the issue.
  - AC-004.2: WHEN the submission succeeds, THE Jira App SHALL close the form and show MSG66.
  - AC-004.3: IF the issue's ticket already exists, THEN THE server SHALL not create a Solution Form ticket and SHALL answer 409 MSG31.
  - AC-004.4: IF the server cannot be reached or answers 5xx, THEN THE Jira App SHALL retry automatically up to 3 times and, if it still fails, show MSG69 and keep the form open with its content.

### STORY-005: Keep a Draft
- As a Ticket Resolver, I want what I wrote to be kept when I close the form so that I can finish it later.
- Acceptance:
  - AC-005.1: WHEN the user closes the form (its Close button or the dialog's close control), THE server SHALL store the five sections as the user's only Solution Form Draft for the issue, without validation and without creating a ticket, and THE Jira App SHALL close the form and show MSG41.
  - AC-005.2: WHEN the form opens and the user has a Draft for the issue, THE Jira App SHALL fill the sections from the Draft instead of the AI draft.
  - AC-005.3: THE server SHALL show a Draft only to its creator.
  - AC-005.4: IF saving the Draft fails, THEN THE Jira App SHALL show MSG06 and keep the form open with its content.
  - AC-005.5: THE Jira App SHALL offer no separate Save as draft button and SHALL never create a ticket when the form is closed; THE server SHALL create a Solution Form ticket only from a submission that passes AC-002.5, so a Solution with every section empty never enters the Review Queue.

### STORY-007: Rewrite with AI
- As a Ticket Resolver, I want AI to improve my Solution's wording so that it is clearer, while I stay in control (GBR-12).
- Acceptance:
  - AC-007.1: WHEN the user clicks Rewrite with AI, THE Jira App SHALL show 4.2 AI Rewrite Preview with the current Solution and the rewritten Solution section by section.
  - AC-007.2: WHEN the user clicks Accept, THE Jira App SHALL replace the form's five sections with the rewritten ones and go back to the form.
  - AC-007.3: WHEN the user clicks Discard, THE Jira App SHALL go back to the form with the Solution unchanged.
  - AC-007.4: IF the rewrite fails, is unavailable or takes longer than the UI call allows, THEN THE Jira App SHALL show MSG68 and leave the Solution unchanged.

### STORY-008: Issue Panel
- As a Ticket Resolver, I want the panel to show the issue's Solution status so that I know what is left to do for it.
- Acceptance:
  - AC-008.1: IF the Jira account is not linked to an active T-Solve Account, THEN THE panel SHALL show MSG62 and nothing else.
  - AC-008.2: WHILE the issue's ticket is in Pending review, THE panel SHALL show MSG65.
  - AC-008.3: WHILE the user has a Draft for the issue and the issue's ticket does not exist, THE panel SHALL show MSG77 with a button that reopens the form with the Draft.
  - AC-008.7: WHILE the issue's Solution Form ticket written by the user is in Changes requested, THE panel SHALL show MSG64 with the Project Manager's name and comment, read from `ticket-review`'s `GET /api/v1/jira-app/issues/{issueKey}/review-feedback`.

### STORY-010: Ask T-Solve before creating an issue
- As an Employee, I want to ask T-Solve about my problem inside Jira before I create an issue, so that I fix it myself when a known Solution exists and, when not, the issue I create says what I already tried.
- Terms: **page** = the "Ask T-Solve" Jira project page; **conversation** = the questions and answers on the page since it was opened; **chatbot answer** = the answer of the `chatbot` module's answer service (chatbot STORY-005 to STORY-007).
- Acceptance:
  - AC-010.1: THE Jira App SHALL show a project page titled "Ask T-Solve" in every Jira project of the site, with a question box, a send button and the note "Answers from T-Solve Solutions and Wiki".
  - AC-010.2: IF the Jira account is not linked to an active T-Solve user, THEN THE page SHALL show MSG62 instead of the question box and SHALL still show "Not solved, create ticket", which opens the Create dialog of AC-010.9 with nothing prefilled.
  - AC-010.3: WHEN the user sends a question, THE Jira App SHALL call `POST /api/v1/jira-app/questions` with the question and, as history, the earlier turns of the conversation — at most the last 10.
  - AC-010.4: WHEN the server receives a valid question from a linked user, THE server SHALL return the chatbot answer for that user — the same sources, citations and outcomes as the web Knowledge Chatbot, from every Solution and Wiki document the user may see.
  - AC-010.5: IF the question is empty or only white space, THEN THE Jira App SHALL show MSG01 under the box and send nothing; IF it is longer than 1,000 characters, THEN THE Jira App SHALL show MSG02 and send nothing; THE server SHALL answer 400 with the same codes whatever the Jira App checked.
  - AC-010.6: WHEN the answer has outcome `ANSWERED`, THE page SHALL show its text, its sources in number order — a Wiki document as "[n] <title> · Wiki document", a ticket source as "[n] Solution from a ticket" with its Solution, never the ticket's id, link or Project (GBR-36) — and the buttons "Solved" and "Not solved, create ticket".
  - AC-010.7: WHEN the answer has outcome `NO_SOURCE`, THE page SHALL show MSG109 and the buttons "Solved" and "Not solved, create ticket".
  - AC-010.8: IF the answer request fails, does not answer within 20 seconds, or the server answers 5xx, THEN THE page SHALL show MSG06, keep the question in the box and still show "Not solved, create ticket" (GBR-12).
  - AC-010.9: WHEN the user clicks "Not solved, create ticket", THE Jira App SHALL open Jira's Create issue dialog for the open Jira project with Summary = the first question of the conversation cut to 255 characters, Description = every question of the conversation followed by a part "Tried with T-Solve:" with the last answer and, after "Sources:", its sources labelled as in AC-010.6 (no Solution text), and the label `tsolve-asked`; the user can change every field before clicking Create.
  - AC-010.10: WHEN the user clicks "Solved", THE page SHALL end the conversation, show the text "Your problem is solved. Thank you!" (a Jira App label, not an SRS message — user, 2026-11-07), create no issue and store nothing.
  - AC-010.11: IF the FIT user is not linked to an active T-Solve user, THEN THE server SHALL answer `POST /api/v1/jira-app/questions` with 403 MSG62 and call no AI.
  - AC-010.12: THE Jira App SHALL keep the conversation only while the page is open; it SHALL store no question or answer itself and SHALL log no question or answer text.

## Out of Scope
- Evidence in the Jira App (attach, MSG34, MSG72, the Evidence list of 3.4): Forge UI calls carry at most 500 KB
  (RESEARCH-003); Evidence is added on the T-Solve web app (user, 2026-11-01, kept 2026-11-11). KAN-126 "Done when"
  items about Evidence are not met — reported to the team.
- Resubmitting after Changes requested (MSG71) — the panel only shows the change request (AC-008.7).
- The issue-created event and the AI suggestion comment — `tracker`.
- AI drafting and rewriting quality — `ai` (Trần Hải Anh); this module calls its chat client through one service.
- Searching Published tickets and reading one in Jira (the SRS's 3.3 search box, 3.4 Published Ticket Preview,
  MSG04 and MSG76 in the panel; formerly AC-008.4 to AC-008.6, AC-008.8 and STORY-009): search stays on the T-Solve
  web app (UC-26) — user, 2026-11-14; a deviation from the SRS to agree with the team.
- Evidence download — web app.
- Reminding writers of their unsubmitted Drafts — a daily email digest per writer from a worker job, in a separate
  Jira issue (user, 2026-11-14); it does not change `worker`'s or `ticket-review`'s existing jobs.
- Images in the question (OCR or a vision model, attaching them to the created issue) — a later version
  (RESEARCH-004).
- Recording "Solved" on the server — after the chatbot ratings backend (KAN-182).
- Sources limited to the open Jira project — the page answers like the web chatbot (user, 2026-11-07).
- Anything on Jira's own Create dialog (RESEARCH-004 Options A, A+, B).
- Linking the Jira account to a T-Solve user — `identity`; a configured stub until then.

## Interface Contracts

### Exposed by this module
Proposals, added to `frontend/openapi/tsolve-api.yaml` before code; FIT-authenticated, user = FIT `principal`.
Also exposed, in Java: the production `ReviewJiraAccess` (interface of `ticket-review`, `vn.tsolve.review.service`)
— Jira principal + issue key → linked Account and Project — used by this module's endpoints and by `ticket-review`'s
feedback endpoint (AC-008.7).

| Endpoint | Screen | Story |
|----------|--------|-------|
| `POST /api/v1/jira-app/issues/{issueKey}/solution-form` (issue summary + description) → may open, Draft or AI draft or prefill, MSG67 flag | 4.1 | 001, 002, 005 |
| `POST /api/v1/jira-app/issues/{issueKey}/solution` (JSON: five sections) | 4.1 | 004 |
| `PUT /api/v1/jira-app/issues/{issueKey}/solution-draft` (on Close) | 4.1 | 005 |
| `POST /api/v1/jira-app/solution-rewrites` (five sections → rewritten five sections) | 4.2 | 007 |
| `GET /api/v1/jira-app/issues/{issueKey}/panel` → linked, ticket status, Draft, may open | 3.3 | 001, 008 |
| `GET /api/v1/jira-app/questions/eligibility` (→ `linked`: whether the FIT user is linked to an active T-Solve user; calls no AI) | Ask T-Solve (on open, AC-010.2) | 010 |
| `POST /api/v1/jira-app/questions` (question + history → the chatbot answer: outcome, text, citations) | Ask T-Solve | 010 |

Forge: `jira:issueViewBackgroundScript` (opens the form), `jira:issuePanel` (panel), Modal for 4.1/4.2,
`requestJira` for the issue summary/description; `jira:projectPage` for the Ask T-Solve page, `invokeRemote` to the
endpoints above, and `CreateIssueModal`
(`@forge/jira-bridge`) for the prefilled Create dialog — whether it can prefill labels and where the page shows in
Jira's navigation are checked in a spike first (RESEARCH-004, open questions).

### Required from other modules
| Owner | Needed operation |
|-------|------------------|
| `identity` | Jira accountId → T-Solve user (id, active) — stub until built |
| `project` / `review` | Project of a Jira project key, the user's `project_membership` rows (today read by `ticket-review`'s `ProjectMembershipRepository`) |
| `ticket-review` (Phạm Tùng Dương) | The Solution Form ticket storage it reviews (`ticket`, source `SOLUTION_FORM`, entry time and due date, AC-009.11/AC-012.2); `GET /api/v1/jira-app/issues/{issueKey}/review-feedback` (MSG64) |
| `ai` (Trần Hải Anh) | Chat client (`LlmClient`) for the AI draft and rewrite — prompts owned here, a configured stand-in for demos |
| `chatbot` (Trần Hải Anh) | Answer a question with history for a given T-Solve user (`ChatbotAnswerService`, chatbot STORY-005 to STORY-007), callable from the Jira App API — to agree with the owner |
| `tracker` | Skip the suggestion job for an issue created with label `tsolve-asked` — a change of tracker AC-011.8 (tracker spec, KAN-22) |

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] The endpoints are in `frontend/openapi/tsolve-api.yaml`, `npm run generate:api` leaves no diff
- [ ] `mvn verify` and `forge lint` pass; the flow works on the Forge development site
- [ ] The chatbot and AI calls agreed with Trần Hải Anh; the ticket storage agreed with Phạm Tùng Dương

<!-- ACCEPTANCE CRITERIA: one testable statement per item, id AC-<story>.<n> (never reuse a
     removed id), in EARS form: THE <system> SHALL <response>, optionally led by
     WHEN <trigger>, WHILE <state>, IF <unwanted condition> THEN, or WHERE <feature is included>.
     A story with a WHEN criterion also gets an IF … THEN criterion for its failure path.
     SPEC EDIT RULE: Any edit to this file that changes business logic, requirements, stories, or
     acceptance criteria MUST add a new row to the Changelog table above. Once the user has approved the
     spec, any edit locks the module's code until they approve the new version (`sdd.ts approve <module>`). -->
