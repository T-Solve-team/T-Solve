---
module: jira-app
date: 2026-11-07
owner: BA
depth: Normal
---

# Spec: jira-app

The T-Solve Jira App (Forge UI Kit, `jira-app/`) and the Jira App API endpoints it calls
(`/api/v1/jira-app/**`). This branch (KAN-294) holds **only STORY-010**; STORY-001 to STORY-009 (Solution Form,
Issue Panel, KAN-126) are written on `feat/KAN-126-jira-app-solution-form` and are merged into this file when
KAN-126 reaches `main` — their ids stay reserved. Sources: RESEARCH-004, the `chatbot` spec (STORY-005 to
STORY-007), SRS System Messages. Terms: **user** = the T-Solve user linked to the Jira account in the FIT
`principal`.

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| 2026-11-07 | STORY-010 (new) | Add STORY-010 "Hỏi T-Solve" page (KAN-294): ask the Knowledge Chatbot inside Jira before creating an issue, then "Solved" or a prefilled Jira Create dialog | RESEARCH-004 Option C. User decisions of 07/11: answers come from the `chatbot` module's answer service with the same sources as the web chatbot (all Solutions and Wiki the user may see, not only the open Jira project); only Jira accounts linked to a T-Solve user ask; the Create dialog is prefilled with the questions and what was tried; "Solved" is UI only until chatbot ratings (KAN-182); the created issue gets label `tsolve-asked` so the tracker's suggestion job skips it. Written on its own branch from `main` (user, 07/11): STORY-001..009 stay on KAN-126 |

## Requirements

### Functional
5. A "Hỏi T-Solve" page in each Jira project lets a linked user ask the Knowledge Chatbot before creating an issue;
   "Chưa được, tạo ticket" opens Jira's Create dialog prefilled with the conversation (STORY-010). Requirements 1
   to 4 belong to KAN-126.

### Non-Functional
1. Every Jira App API call is verified with a FIT (tracker STORY-001); the user comes from the FIT `principal`.
2. UI calls answer within 20 seconds (Forge's limit is 25 s); an AI call that takes longer counts as unavailable.
3. UI Kit components only; every server call goes through one client module in `jira-app/`; logs carry ids,
   issue keys and error types only — never question, answer or Solution text, or personal data.
4. AI failures never block the user: creating an issue stays possible without AI (GBR-12).

## User Stories

### STORY-010: Ask T-Solve before creating an issue
- As an Employee, I want to ask T-Solve about my problem inside Jira before I create an issue, so that I fix it myself when a known Solution exists and, when not, the issue I create says what I already tried.
- Terms: **page** = the "Hỏi T-Solve" Jira project page; **conversation** = the questions and answers on the page since it was opened; **chatbot answer** = the answer of the `chatbot` module's answer service (chatbot STORY-005 to STORY-007).
- Acceptance:
  - AC-010.1: THE Jira App SHALL show a project page titled "Hỏi T-Solve" in every Jira project of the site, with a question box, a send button and the note "Trả lời từ Solution và Wiki của T-Solve".
  - AC-010.2: IF the Jira account is not linked to an active T-Solve user, THEN THE page SHALL show MSG62 instead of the question box and SHALL still show "Chưa được, tạo ticket", which opens the Create dialog of AC-010.9 with nothing prefilled.
  - AC-010.3: WHEN the user sends a question, THE Jira App SHALL call `POST /api/v1/jira-app/questions` with the question and, as history, the earlier turns of the conversation — at most the last 10.
  - AC-010.4: WHEN the server receives a valid question from a linked user, THE server SHALL return the chatbot answer for that user — the same sources, citations and outcomes as the web Knowledge Chatbot, from every Solution and Wiki document the user may see.
  - AC-010.5: IF the question is empty or only white space, THEN THE Jira App SHALL show MSG01 under the box and send nothing; IF it is longer than 1,000 characters, THEN THE Jira App SHALL show MSG02 and send nothing; THE server SHALL answer 400 with the same codes whatever the Jira App checked.
  - AC-010.6: WHEN the answer has outcome `ANSWERED`, THE page SHALL show its text, its sources (Ticket ID and title of each Solution, title of each Wiki document) and the buttons "Đã giải quyết" and "Chưa được, tạo ticket".
  - AC-010.7: WHEN the answer has outcome `NO_SOURCE`, THE page SHALL show MSG109 and the buttons "Đã giải quyết" and "Chưa được, tạo ticket".
  - AC-010.8: IF the answer request fails, does not answer within 20 seconds, or the server answers 5xx, THEN THE page SHALL show MSG06, keep the question in the box and still show "Chưa được, tạo ticket" (GBR-12).
  - AC-010.9: WHEN the user clicks "Chưa được, tạo ticket", THE Jira App SHALL open Jira's Create issue dialog for the open Jira project with Summary = the first question of the conversation cut to 255 characters, Description = every question of the conversation followed by a part "Đã thử theo T-Solve" with the last answer and its sources, and the label `tsolve-asked`; the user can change every field before clicking Create.
  - AC-010.10: WHEN the user clicks "Đã giải quyết", THE page SHALL end the conversation, show the text "Vấn đề đã được giải quyết. Cảm ơn bạn!" (a Jira App label, not an SRS message — user, 2026-11-07), create no issue and store nothing.
  - AC-010.11: IF the FIT user is not linked to an active T-Solve user, THEN THE server SHALL answer `POST /api/v1/jira-app/questions` with 403 MSG62 and call no AI.
  - AC-010.12: THE Jira App SHALL keep the conversation only while the page is open; it SHALL store no question or answer itself and SHALL log no question or answer text.

## Out of Scope
- STORY-001 to STORY-009 (Solution Form, Issue Panel, Published Ticket Preview) — KAN-126, on its own branch.
- Images in the question (OCR or a vision model, attaching them to the created issue) — a later version
  (RESEARCH-004).
- Recording "Đã giải quyết" on the server — after the chatbot ratings backend (KAN-182).
- Sources limited to the open Jira project — the page answers like the web chatbot (user, 2026-11-07).
- Anything on Jira's own Create dialog (RESEARCH-004 Options A, A+, B).
- Linking the Jira account to a T-Solve user — `identity`; a configured stub until then.

## Interface Contracts

### Exposed by this module
Proposal, added to `frontend/openapi/tsolve-api.yaml` before code; FIT-authenticated, user = FIT `principal`.

| Endpoint | Screen | Story |
|----------|--------|-------|
| `POST /api/v1/jira-app/questions` (question + history → the chatbot answer: outcome, text, citations) | Hỏi T-Solve | 010 |

Forge: `jira:projectPage` for the page, `invokeRemote` to the endpoint above, and `CreateIssueModal`
(`@forge/jira-bridge`) for the prefilled Create dialog — whether it can prefill labels and where the page shows in
Jira's navigation are checked in a spike first (RESEARCH-004, open questions).

### Required from other modules
| Owner | Needed operation |
|-------|------------------|
| `identity` | Jira accountId → T-Solve user (id, active) — stub until built |
| `chatbot` (Trần Hải Anh) | Answer a question with history for a given T-Solve user (`ChatbotAnswerService`, chatbot STORY-005 to STORY-007), callable from the Jira App API — to agree with the owner |
| `tracker` | Skip the suggestion job for an issue created with label `tsolve-asked` — a change of tracker AC-011.8 (tracker spec, KAN-22) |

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] The endpoint is in `frontend/openapi/tsolve-api.yaml`, `npm run generate:api` leaves no diff
- [ ] `mvn verify` and `forge lint` pass; the flow works on the Forge development site
- [ ] The chatbot call agreed with Trần Hải Anh

<!-- ACCEPTANCE CRITERIA: one testable statement per item, id AC-<story>.<n> (never reuse a
     removed id), in EARS form: THE <system> SHALL <response>, optionally led by
     WHEN <trigger>, WHILE <state>, IF <unwanted condition> THEN, or WHERE <feature is included>.
     A story with a WHEN criterion also gets an IF … THEN criterion for its failure path.
     SPEC EDIT RULE: Any edit to this file that changes business logic, requirements, stories, or
     acceptance criteria MUST add a new row to the Changelog table above. Once the user has approved the
     spec, any edit locks the module's code until they approve the new version (`sdd.ts approve <module>`). -->
