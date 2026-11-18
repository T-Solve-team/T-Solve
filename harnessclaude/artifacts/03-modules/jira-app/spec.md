---
module: jira-app
date: 2026-11-07
owner: BA
depth: Normal
---

# Spec: jira-app

The T-Solve Jira App (Forge UI Kit, `jira-app/`) and the Jira App API endpoints it calls
(`/api/v1/jira-app/**`). STORY-001 to STORY-008 (KAN-126): Solution Form, AI Rewrite, Issue Panel (STORY-003, STORY-006
and STORY-009 retired). STORY-010 (KAN-294): Ask T-Solve page. STORY-012 (KAN-130): resubmit after Changes requested. STORY-014: link the Jira account to T-Solve. STORY-011 (KAN-336): daily Draft reminder email. Sources: Report3 (UC-19, GBR-04, 06, 08, 10, 12, 19, 26,
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
| 2026-11-14 | STORY-005 | AC-005.1: closing the form shows the flag "Saved as draft" instead of MSG41 | User in the dev-site test (14/11): the short text says what Close did |
| 2026-11-14 | STORY-011 (new) | Add STORY-011 (KAN-336): a daily worker job emails each writer one plain-text list of their open Drafts (issue key, last saved date, Jira link) at 08:00 Asia/Ho_Chi_Minh; one email per writer per day, retried per writer, no email without an open Draft; `worker` and `identity` added to Required interfaces | User decisions of 14/11: Close keeps a Draft (STORY-005), so writers need a reminder instead of an empty ticket; daily digest per person; the job reuses `worker`'s tenant loop and SMTP settings without changing `worker`'s or `ticket-review`'s jobs |
| 2026-11-15 | STORY-011 | AC-011.4 and AC-011.6 reworded: the reminder queues each writer's email in the shared email outbox (worker STORY-004, KAN-162) instead of sending it over SMTP itself; delivery retries, the missing-sender case and expiry are the outbox's; queuing is retried per writer. The `worker` row of Required interfaces names `EmailOutboxService` instead of the SMTP settings. The body stays plain text | PR #233 (KAN-162) merged on 15/11; user chose to defer to the outbox (15/11) rather than one more mail path. User: plain text for now, an HTML body is to follow after 16/11 |
| 2026-11-14 | STORY-012 (new), STORY-008 | Add STORY-012 Resubmit after Changes requested (KAN-130, UC-19): the writer reopens the form from the panel with "Edit and resubmit", sees MSG64 above the sections, edits the ticket's Solution (or their Draft) and Finish puts the same ticket back into Pending review with a new entry time and due date; Close keeps a Draft. The panel response gains `mayResubmit`, the form request gains `resubmit`; the Out of Scope line on resubmitting is removed | User decisions of 14/11 for the demo (script 2 step 8): due date reset as for a new submission; form filled from the ticket's Solution, Close keeps a Draft; success shows MSG66 (MSG71's SRS text is not available); button + MSG64 in the form. STORY-011 is taken by the KAN-336 branch |
| 2026-11-14 | STORY-008, STORY-012 | AC-008.7: the Jira App shows MSG64 without its last sentence, as `Changes requested by {manager_name}: "{comment}".` (the SRS adds "Update the solution and resubmit."); AC-012.1 and AC-012.2 show the same text. The web app keeps the SRS text | User in the KAN-130 dev-site test (14/11): the panel already shows "Edit and resubmit", so the last sentence repeats it. Deviation from the SRS text of MSG64, Jira App only, to agree with the team |
| 2026-11-15 | STORY-008 | New AC-008.9: while the issue's Solution Form ticket written by the user is Rejected, the panel shows `Rejected by {manager_name}: "{reason}".` from `ticket-review`'s feedback endpoint (its AC-009.13), with no button | User in the KAN-130 dev-site test (15/11): the panel was empty after a Reject. The SRS has no message for it, so the text is a Jira App label like "Edit and resubmit"; the user chose the feedback endpoint over a new panel field |
| 2026-11-15 | STORY-008 | New AC-008.10: while the issue's ticket is Published, whatever its source, the panel shows "Solution approved and published." with no button | User in the KAN-337 dev-site test (15/11): the panel was empty after an Approve. The SRS has no message for it, so the text is a Jira App label; the user chose the status alone, without the Project Manager's name, so no server change |
| 2026-11-17 | STORY-013 | New STORY-013 (KAN-349): the Forge app sends its app system token to the backend — hourly scheduled trigger plus install/upgrade, straight to the remote with `appSystemToken`; scope `write:jira-work` added for the MSG80 comment and issue creation | RESEARCH-005 option D, user decision 17/11: T-Solve calls Jira as its Jira App instead of a customer service account |
| 2026-11-17 | STORY-010 | KAN-294: Ask T-Solve needs no T-Solve sign-in. The page answers anyone in a Jira project that is an Active T-Solve Project, from the Published Solutions and the Wiki of that Project's Department (chatbot STORY-012); the project comes only from the verified FIT (new AC-010.13). AC-010.2 now covers a Jira project or site that is not connected (Jira App text "This Jira project is not connected to T-Solve.", MSG124 for a site), AC-010.4 and AC-010.11 reworded, AC-010.12 says the server stores the question without a user; new AC-010.14 the eligibility answer; new AC-010.15 "Solved" and "Not solved, create ticket" rate the last answer HELPFUL or NOT_HELPFUL. MSG62 is no longer shown on the page | User decisions of 17/11: the page helps people solve a problem in the project they are in, so who asks does not matter; scope = the Project's Department; questions are still stored (without a user) and counted on the Knowledge Dashboard |
| 2026-11-17 | STORY-010 | KAN-294 review of the risks: AC-010.4 answers from the Department's Solutions and only Company-wide Wiki documents (the MSG80 rule, UC-36), so the page never shows a Department-only Wiki document to a requester outside the Department; AC-010.11 and AC-010.14 also refuse a FIT without a Jira user (anonymous access to a Jira project) | User decision of 17/11 after the risk review: same protection as the MSG80 comment |
| 2026-11-17 | STORY-010 | AC-010.4: a Jira account linked to an active T-Solve user also gets the Wiki documents that user may view (Department-only ones included); an unlinked one keeps the Company-wide documents only | User decision of 17/11: Department-only documents for the people allowed to read them, no sign-in required |
| 2026-11-17 | STORY-014 (new), STORY-008, STORY-013 | New STORY-014 (AC-014.1 to AC-014.11): a Jira personal settings page "T-Solve account" links the Jira account to a T-Solve Account through identity's link request (identity AC-001.69 to AC-001.72) and shows the linked Account; the server reads the linked Account from `jira_account_links` (identity AC-001.77) and no longer from the `TSOLVE_STUB_JIRA_ASKERS` stand-in. AC-008.1: the panel shows MSG62 with a link to that page. AC-013.2: scope `read:jira-user` added for the Jira display name of the link request. Requirement 6, Out of Scope, the Exposed table and Required from `identity` follow | User decisions of 17/11: a personal settings page rather than a button in every panel; the linked state shows name and email with "Link another account" (no unlink); the page re-checks every 5 s after the click; the display name is read from Jira with the app token (new scope); the panel links to the page |
| 2026-11-17 | STORY-014, STORY-008 | AC-014.1: the "T-Solve account" page is a Forge `jira:globalPage` in the Apps section of Jira's navigation instead of a `jira:personalSettingsPage` in the profile menu; the story and the term "settings page" follow. AC-008.1's link opens that page. No other criterion changes | KAN-358 dev-site test (user, 17/11): the profile menu of bdang798 showed no "T-Solve account" item (a Forge Preview module), although the page worked by its URL; the user chose the Apps section, which is not a Preview module |
| 2026-11-18 | STORY-008 | AC-008.3: MSG77 and the button that reopens the form with the Draft show only while the issue is in a Done status. New AC-008.11: while the issue is not Done, the panel shows neither and the Draft is kept; when the issue is Done again, the panel shows them again and the form opens with the Draft (AC-001.1, AC-005.2) | Dev-site test (user, 18/11, KAN-365): an issue moved from Done back to In Progress still offered "Continue draft". User chose to hide it and keep the Draft |
| 2026-11-18 | STORY-011 | AC-011.3: the reminder gets an HTML body next to the plain-text one, in the layout of `ticket-review`'s review emails (KAN-333): badge "Draft reminder", heading = subject, greeting with the full name, one card with a table of the open Drafts (issue key linked to Jira, last saved date), closing line and an automated-message footer | User 18/11: the HTML body promised for after 16/11, designed like Hiếu's Approve/Reject emails; user chose one card with a table over one card per Draft |
| 2026-11-18 | STORY-011 | AC-011.1: the run time comes from `JIRA_APP_DRAFT_REMINDER_CRON` (default `0 8 * * *`, also when blank; an invalid value stops the start with an error naming the variable); the time zone stays Asia/Ho_Chi_Minh like the rest of the system | User 18/11: a customer changes when the email goes out through env, without a code change |
| 2026-11-18 | STORY-011 | AC-011.1: the time zone of the schedule comes from `TSOLVE_JOB_TIME_ZONE`, one variable for every scheduled job (default Asia/Ho_Chi_Minh, also when blank; invalid stops the start); the reminder day and the email dates stay in Asia/Ho_Chi_Minh | User 18/11: every job's run time and time zone live in `.env`; one shared time zone, a cron per job |

## Requirements

### Functional
1. The form opens by itself when an issue of a Project moves to a Done status, only for members of that Project,
   and only while the issue has no ticket of any source (STORY-001).
2. The form edits the five-section Solution, pre-filled by a Draft, the AI draft or the issue; it can Finish or
   Close, and Close keeps the content as the user's Draft (STORY-002, STORY-004, STORY-005). Only Finish hands a
   ticket to `ticket-review`.
3. Rewrite with AI shows 4.2 AI Rewrite Preview (STORY-007).
4. The panel shows the issue's Solution status, including a Project Manager's change request (STORY-008); the writer
   edits and resubmits a ticket in Changes requested from the panel (STORY-012, KAN-130).
5. An "Ask T-Solve" page in each Jira project lets a linked user ask the Knowledge Chatbot before creating an issue;
   "Not solved, create ticket" opens Jira's Create dialog prefilled with the conversation (STORY-010). Requirements 1
   to 4 belong to KAN-126.
6. A "T-Solve account" page in Jira's Apps section links the Jira account to a T-Solve Account and shows the linked
   Account; every Jira App call finds its T-Solve user through that link (STORY-014).

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
  - AC-005.1: WHEN the user closes the form (its Close button or the dialog's close control), THE server SHALL store the five sections as the user's only Solution Form Draft for the issue, without validation and without creating a ticket, and THE Jira App SHALL close the form and show the flag "Saved as draft" (a Jira App label, not an SRS message — user, 2026-11-14).
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
  - AC-008.1: IF the Jira account is not linked to an active T-Solve Account, THEN THE panel SHALL show MSG62 and a link "Open T-Solve account settings" (a Jira App label — user, 2026-11-17) that opens the settings page of STORY-014, and nothing else.
  - AC-008.2: WHILE the issue's ticket is in Pending review, THE panel SHALL show MSG65.
  - AC-008.3: WHILE the user has a Draft for the issue, the issue's ticket does not exist and the issue is in a status of the Done status category, THE panel SHALL show MSG77 with a button that reopens the form with the Draft.
  - AC-008.7: WHILE the issue's Solution Form ticket written by the user is in Changes requested, THE panel SHALL show MSG64 with the Project Manager's name and comment, read from `ticket-review`'s `GET /api/v1/jira-app/issues/{issueKey}/review-feedback`, as `Changes requested by {manager_name}: "{comment}".` — the SRS text without its last sentence "Update the solution and resubmit." (user, 2026-11-14).
  - AC-008.9: WHILE the issue's Solution Form ticket written by the user is in Rejected, THE panel SHALL show `Rejected by {manager_name}: "{reason}".` (a Jira App text, not an SRS message — user, 2026-11-15) with the Project Manager's name and the reason read from `ticket-review`'s `GET /api/v1/jira-app/issues/{issueKey}/review-feedback` (its AC-009.13), and SHALL offer no button that opens the form.
  - AC-008.11: WHILE the issue is not in a status of the Done status category, THE panel SHALL show neither MSG77 nor a button that opens the form, and THE Jira App SHALL keep the user's Draft for the issue unchanged, so it shows again and fills the form when the issue is Done again (AC-001.1, AC-005.2).
  - AC-008.10: WHILE the issue's ticket is in Published, THE panel SHALL show "Solution approved and published." (a Jira App text, not an SRS message — user, 2026-11-15) and SHALL offer no button that opens the form.

### STORY-010: Ask T-Solve before creating an issue
- As an Employee, I want to ask T-Solve about my problem inside Jira before I create an issue, so that I fix it myself when a known Solution exists and, when not, the issue I create says what I already tried.
- Terms: **page** = the "Ask T-Solve" Jira project page; **conversation** = the questions and answers on the page since it was opened; **chatbot answer** = the answer of the `chatbot` module's answer service (chatbot STORY-005 to STORY-007).
- Acceptance:
  - AC-010.1: THE Jira App SHALL show a project page titled "Ask T-Solve" in every Jira project of the site, with a question box, a send button and the note "Answers from T-Solve Solutions and Wiki".
  - AC-010.2: IF the open Jira project is not the Jira project of an Active T-Solve Project, THEN THE page SHALL show "This Jira project is not connected to T-Solve." (a Jira App text, not an SRS message — user, 2026-11-17) instead of the question box, or MSG124 instead when the Jira site belongs to no Workspace, and SHALL still show "Not solved, create ticket", which opens the Create dialog of AC-010.9 with nothing prefilled.
  - AC-010.3: WHEN the user sends a question, THE Jira App SHALL call `POST /api/v1/jira-app/questions` with the question and, as history, the earlier turns of the conversation — at most the last 10.
  - AC-010.4: WHEN the server receives a valid question from a Jira project of an Active T-Solve Project, THE server SHALL return the chatbot answer for that Project (chatbot STORY-012) — the same citations and outcomes as the web Knowledge Chatbot, from the Published Solutions of the Project's Department and the Wiki documents the asker may read — the Company-wide ones for a Jira account not linked to an active T-Solve user (as the MSG80 suggestion, UC-36), and every document that user may view on the web for a linked one — whoever the Jira user is, linked to T-Solve or not.
  - AC-010.5: IF the question is empty or only white space, THEN THE Jira App SHALL show MSG01 under the box and send nothing; IF it is longer than 1,000 characters, THEN THE Jira App SHALL show MSG02 and send nothing; THE server SHALL answer 400 with the same codes whatever the Jira App checked.
  - AC-010.6: WHEN the answer has outcome `ANSWERED`, THE page SHALL show its text, its sources in number order — a Wiki document as "[n] <title> · Wiki document", a ticket source as "[n] Solution from a ticket" with its Solution, never the ticket's id, link or Project (GBR-36) — and the buttons "Solved" and "Not solved, create ticket".
  - AC-010.7: WHEN the answer has outcome `NO_SOURCE`, THE page SHALL show MSG109 and the buttons "Solved" and "Not solved, create ticket".
  - AC-010.8: IF the answer request fails, does not answer within 20 seconds, or the server answers 5xx, THEN THE page SHALL show MSG06, keep the question in the box and still show "Not solved, create ticket" (GBR-12).
  - AC-010.9: WHEN the user clicks "Not solved, create ticket", THE Jira App SHALL open Jira's Create issue dialog for the open Jira project with Summary = the first question of the conversation cut to 255 characters, Description = every question of the conversation followed by a part "Tried with T-Solve:" with the last answer and, after "Sources:", its sources labelled as in AC-010.6 (no Solution text), and the label `tsolve-asked`; the user can change every field before clicking Create.
  - AC-010.10: WHEN the user clicks "Solved", THE page SHALL end the conversation, show the text "Your problem is solved. Thank you!" (a Jira App label, not an SRS message — user, 2026-11-07), create no issue and store nothing.
  - AC-010.11: IF the Jira site belongs to no Workspace, THEN THE server SHALL answer `POST /api/v1/jira-app/questions` with 403 MSG124; IF the FIT has no Jira user (`principal`) or no project, or its project is not the Jira project of an Active T-Solve Project, THEN with 403 MSG08; in both cases it SHALL call no AI and store nothing.
  - AC-010.12: THE Jira App SHALL keep the conversation only while the page is open; it SHALL store no question or answer itself and SHALL log no question or answer text; THE server stores each answered question without a user, with its Project (chatbot AC-012.2).
  - AC-010.13: THE server SHALL take the Jira project of a question only from the verified FIT's `context.extension.project` (its key), never from the request body, a parameter or a header, so a user cannot ask in another Department's scope.
  - AC-010.14: WHEN the page opens, THE Jira App SHALL call `GET /api/v1/jira-app/questions/eligibility`, which SHALL answer `status` `CONNECTED`, `SITE_NOT_CONNECTED` (the site belongs to no Workspace) or `PROJECT_NOT_CONNECTED` (AC-010.11, also for a FIT without a Jira user) for the FIT's site and project and SHALL call no AI.
  - AC-010.15: WHEN the user clicks "Solved" or "Not solved, create ticket" after an answer, THE Jira App SHALL send `PUT /api/v1/jira-app/questions/{answerId}/rating` with `HELPFUL` for "Solved" or `NOT_HELPFUL` for "Not solved, create ticket" for the last answer of the conversation, and THE server SHALL store it as the rating of that answer (chatbot AC-012.7); IF the call fails, THEN THE page SHALL go on as AC-010.9 and AC-010.10 say and show no message.

### STORY-012: Resubmit after Changes requested
- As a Ticket Resolver, I want to edit and resubmit my Solution when the Project Manager requests changes, so that the corrected Solution goes back to review (UC-19, KAN-130).
- Terms: **resubmittable** = the issue's ticket is a Solution Form ticket whose writer is the user, it is in Changes requested, and the user is a member of its Project; **resubmission form** = the form opened for a resubmittable ticket.
- Acceptance:
  - AC-012.1: WHILE the issue's ticket is resubmittable, THE panel SHALL show MSG64 (AC-008.7) and a button "Edit and resubmit" (a Jira App label, not an SRS message — user, 2026-11-14).
  - AC-012.2: WHEN the user clicks "Edit and resubmit", THE server SHALL allow the form only while the ticket is resubmittable and SHALL fill it from the user's Draft for the issue when there is one, otherwise from the ticket's current Solution; THE Jira App SHALL show MSG64 with the Project Manager's name and comment above the five sections.
  - AC-012.3: IF the issue's ticket is not resubmittable — another status, another writer, another source, or the user is no longer a member of its Project — THEN THE panel SHALL not show "Edit and resubmit" and THE server SHALL not open the resubmission form.
  - AC-012.4: WHEN the user clicks Finish on the resubmission form with a Solution that passes AC-002.5, THE server SHALL replace the ticket's Solution, move the same ticket to Pending review with a new Review Queue entry time and a review due date of today plus the Project's review range (as for AC-004.1, GBR-08, GBR-10), and delete the user's Draft for the issue; THE Jira App SHALL close the form and show MSG66.
  - AC-012.5: IF at Finish the ticket is no longer resubmittable (for example it expired meanwhile), THEN THE server SHALL change nothing and answer 409 MSG06, and THE Jira App SHALL show MSG06 and keep the form open with its content.
  - AC-012.6: WHEN the user closes the resubmission form, THE server SHALL store the five sections as the user's Draft for the issue as in AC-005.1, the ticket SHALL stay in Changes requested, and THE Jira App SHALL show the flag "Saved as draft".
  - AC-012.7: THE resubmission form SHALL check, rewrite and retry as the first submission does (AC-002.3, AC-002.4, AC-004.4, STORY-007).

### STORY-013: Hand the app token to the backend (KAN-349)
- As the T-Solve backend, I want the Jira App to send me its app token regularly, so that I can call Jira for the
  Workspace when nobody is inside Jira (workspace STORY-014).
- Acceptance:
  - AC-013.1: THE manifest SHALL declare a scheduled trigger with interval `hour` and a trigger on the app's install and
    upgrade events, each invoking the remote endpoint of `POST /api/v1/jira-app/app-token` directly (not through a
    Forge function), with `auth` `appSystemToken: true` and no user token.
  - AC-013.2: THE manifest SHALL request the scopes `read:jira-work`, `write:jira-work` and `read:jira-user` and no
    administration scope (`manage:*`), so the app token can read issues, projects, comments and a user's display name
    (AC-014.6) and add comments and issues only.
  - AC-013.3: WHEN the backend answers the token call with an error, THE Jira App SHALL do nothing else; the next run
    sends a new call (no retry loop in the app).

### STORY-014: Link the Jira account to T-Solve
- As a Ticket Resolver, I want to link my Jira account to my T-Solve Account once, from the T-Solve app in Jira, so that the panel and the Solution Form know who I am in T-Solve (GBR-19, UC-59).
- Terms: **settings page** = the app's "T-Solve account" page in the Apps section of Jira's navigation; **linked Account** = the Active T-Solve Account that identity's `jira_account_links` row of the FIT's Jira account points to, in the Workspace of the FIT's site (identity AC-001.77).
- Acceptance:
  - AC-014.1: THE Jira App SHALL add a page titled "T-Solve account" (Forge `jira:globalPage`) to the Apps section of Jira's navigation for every Jira user of the site.
  - AC-014.2: WHEN the settings page opens, THE Jira App SHALL call `GET /api/v1/jira-app/account-link`, which SHALL answer, for the FIT's site and Jira user only, `status` `LINKED` with the linked Account's full name and email and the link time, `NOT_LINKED`, or `SITE_NOT_CONNECTED` (the site belongs to no Workspace), and SHALL call no AI and store nothing.
  - AC-014.3: WHILE the status is `NOT_LINKED`, THE settings page SHALL show "Your Jira account is not linked to T-Solve." and a button "Sign in to T-Solve" (Jira App labels — user, 2026-11-17).
  - AC-014.4: WHILE the status is `LINKED`, THE settings page SHALL show `Linked to T-Solve as {full_name} ({email}).` with the linked Account's full name and email, and a button "Link another account" (Jira App labels — user, 2026-11-17), and SHALL offer no unlink.
  - AC-014.5: WHILE the status is `SITE_NOT_CONNECTED`, THE settings page SHALL show MSG124 and no button.
  - AC-014.6: WHEN the user clicks "Sign in to T-Solve" or "Link another account", THE Jira App SHALL call `POST /api/v1/jira-app/account-link`; THE server SHALL read the display name of the FIT's Jira account from Jira (`GET /rest/api/3/user?accountId=`) with the Workspace's app token (STORY-013), ask identity's `JiraLinkRequestService` for a link request with the Workspace of the FIT's site, the FIT's Jira account, that display name and the Jira site name (the host of the Workspace's Jira site URL) (identity AC-001.69), and answer with the link it returns.
  - AC-014.7: WHEN the link arrives, THE Jira App SHALL open it in a new browser tab and, instead of the button, show MSG74 with "page" in place of "panel": "Finish signing in in the new tab. This page will update when you are done." (user, 2026-11-17).
  - AC-014.8: WHILE MSG74 is shown, THE Jira App SHALL call `GET /api/v1/jira-app/account-link` every 5 seconds and SHALL show the state of AC-014.4 as soon as the answer is `LINKED` with a link time later than the click; WHEN 10 minutes have passed without that answer (the link request has expired), THE Jira App SHALL stop calling and show the state of the last answer again, with its button.
  - AC-014.9: IF the site belongs to no Workspace, THEN THE server SHALL answer `POST /api/v1/jira-app/account-link` with 403 MSG124; IF the FIT has no Jira user (`principal`), THEN with 403 MSG08; IF Jira or the link request store cannot be reached, or Jira returns no display name, THEN with 503 MSG06; in every case THE server SHALL create no link request, and THE Jira App SHALL show that message, open no tab and keep the button.
  - AC-014.10: THE server SHALL take the site and the Jira account of a link request only from the verified FIT, never from the request body, a parameter or a header (identity AC-001.38, GBR-19).
  - AC-014.11: THE server SHALL find the T-Solve user of every Jira App call — the panel, the Solution Form, the resubmission and the Ask T-Solve answer (AC-010.4) — only as the linked Account (identity AC-001.77): no link, or a link to an Account that is not Active, means not linked; THE server SHALL no longer read the `TSOLVE_STUB_JIRA_ASKERS` setting.

### STORY-011: Remind writers of their unsubmitted Drafts
- As a Ticket Resolver, I want a daily email listing the Solutions I left as Drafts so that I finish and submit them.
- Terms: **reminder job** = the worker job of this story; **open Draft** = a Solution Form Draft (STORY-005) whose
  issue's ticket does not exist; **writer** = the active Account that owns an open Draft.
- Acceptance:
  - AC-011.1: WHILE the worker runs with jobs enabled, THE server SHALL run the reminder job on the cron schedule read from the environment variable `JIRA_APP_DRAFT_REMINDER_CRON`, in the time zone read from `TSOLVE_JOB_TIME_ZONE` (shared by every scheduled job; Asia/Ho_Chi_Minh when unset or blank) — every day at 08:00 (`0 8 * * *`) when the cron variable is unset or blank — under its own recurring job id (not shared with any other job), once for each active Workspace. IF either variable holds an invalid value, THEN THE server SHALL fail to start with an error naming the variable. The day of a reminder (AC-011.5) and the dates in the email stay in Asia/Ho_Chi_Minh.
  - AC-011.2: WHEN the reminder job runs for a Workspace, THE server SHALL send exactly one email to each writer of that Workspace and SHALL send no email to an Account without an open Draft, to an inactive Account or for a Draft whose issue's ticket exists.
  - AC-011.3: THE email SHALL go to the writer's Account email, with the subject "You have 1 unsubmitted Solution draft in T-Solve" for one open Draft and "You have N unsubmitted Solution drafts in T-Solve" for N of them (N > 1), and both an HTML body and a plain-text body. The HTML body SHALL use the layout of `ticket-review`'s review emails (KAN-333): the T-Solve header, the badge "Draft reminder", the subject as heading, a greeting "Hi " + the Account's full name + "," ("Hi there," when the name is blank) and a card holding a table with one row per open Draft, newest saved first — the issue key, linked to the Jira site URL + `/browse/` + the key when that URL is configured, and the date it was last saved (yyyy-MM-dd, Asia/Ho_Chi_Minh) — then the line "Open the issue in Jira and use Continue draft in the T-Solve panel to submit it." and the footer "You got this email because you have unsubmitted Solution drafts on T-Solve. This is an automated message — please do not reply."; every value from the database is HTML-escaped. The plain-text body SHALL carry the same rows, one line per open Draft (the issue key, the date and, when the Jira site URL is configured, the link), followed by the same closing line. The email SHALL contain no Solution text.
  - AC-011.4: THE server SHALL hand each writer's email to the shared email outbox (`worker` STORY-004), whose dispatcher sends it and retries a failed delivery (worker AC-004.4 to AC-004.10). IF queuing a writer's email fails, THEN THE server SHALL retry queuing that writer's email by itself, without queuing again for the other writers.
  - AC-011.5: IF the reminder job runs again for a Workspace on the same day, THEN THE server SHALL not send a writer a second email that day.
  - AC-011.6: THE reminder job SHALL queue the email whether or not an SMTP sender is configured; without one, the outbox keeps and expires the email by its own rules (worker AC-004.9, AC-004.10) and logs the missing sender itself.
  - AC-011.7: THE reminder job SHALL log only ids, counts and error types — never an email address, an issue key list or Draft content.

## Out of Scope
- Evidence in the Jira App (attach, MSG34, MSG72, the Evidence list of 3.4): Forge UI calls carry at most 500 KB
  (RESEARCH-003); Evidence is added on the T-Solve web app (user, 2026-11-01, kept 2026-11-11). KAN-126 "Done when"
  items about Evidence are not met — reported to the team.
- Resubmitting an Expired or Rejected ticket, and resubmitting from the T-Solve web app.
- MSG71: its SRS text is not available; a resubmission shows MSG66 like a first submission (user, 2026-11-14).
- The issue-created event and the AI suggestion comment — `tracker`.
- AI drafting and rewriting quality — `ai` (Trần Hải Anh); this module calls its chat client through one service.
- Searching Published tickets and reading one in Jira (the SRS's 3.3 search box, 3.4 Published Ticket Preview,
  MSG04 and MSG76 in the panel; formerly AC-008.4 to AC-008.6, AC-008.8 and STORY-009): search stays on the T-Solve
  web app (UC-26) — user, 2026-11-14; a deviation from the SRS to agree with the team.
- Evidence download — web app.
- Turning the Draft reminder off per person, HTML or Vietnamese email, reminders by other channels — a later version
  (STORY-011 sends an English HTML email with a plain-text part, like `ticket-review`'s notifications).
- Images in the question (OCR or a vision model, attaching them to the created issue) — a later version
  (RESEARCH-004).
- Recording "Solved" on the server — after the chatbot ratings backend (KAN-182).
- Sources limited to the open Jira project — the page answers like the web chatbot (user, 2026-11-07).
- Anything on Jira's own Create dialog (RESEARCH-004 Options A, A+, B).
- Recording the link and the `/jira-link` confirm page — `identity` (AC-001.69 to AC-001.72). Unlinking a Jira
  account (user, 2026-11-17: "Link another account" replaces the link instead).

## Interface Contracts

### Exposed by this module
Proposals, added to `frontend/openapi/tsolve-api.yaml` before code; FIT-authenticated, user = FIT `principal`.
Also exposed, in Java: the production `ReviewJiraAccess` (interface of `ticket-review`, `vn.tsolve.review.service`)
— Jira principal + issue key → linked Account and Project — used by this module's endpoints and by `ticket-review`'s
feedback endpoint (AC-008.7).

| Endpoint | Screen | Story |
|----------|--------|-------|
| `POST /api/v1/jira-app/issues/{issueKey}/solution-form` (issue summary + description, `resubmit`) → may open, Draft or AI draft or prefill or the ticket's Solution, MSG67 flag | 4.1 | 001, 002, 005, 012 |
| `POST /api/v1/jira-app/issues/{issueKey}/solution` (JSON: five sections) → a new ticket, or the resubmitted one | 4.1 | 004, 012 |
| `PUT /api/v1/jira-app/issues/{issueKey}/solution-draft` (on Close) | 4.1 | 005 |
| `POST /api/v1/jira-app/solution-rewrites` (five sections → rewritten five sections) | 4.2 | 007 |
| `GET /api/v1/jira-app/issues/{issueKey}/panel` → linked, ticket status, Draft, may open, may resubmit | 3.3 | 001, 008, 012 |
| `GET /api/v1/jira-app/questions/eligibility` (→ `status`: `CONNECTED`, `SITE_NOT_CONNECTED` or `PROJECT_NOT_CONNECTED` for the FIT's site and project; calls no AI) | Ask T-Solve (on open, AC-010.2, AC-010.14) | 010 |
| `POST /api/v1/jira-app/questions` (question + history → the chatbot answer: outcome, text, citations) | Ask T-Solve | 010 |
| `PUT /api/v1/jira-app/questions/{answerId}/rating` (`{ rating: HELPFUL | NOT_HELPFUL }` → 204; 404 MSG04 for an answer of another Project) | Ask T-Solve, "Solved" / "Not solved" (AC-010.15) | 010 |
| `GET /api/v1/jira-app/account-link` (→ `status` `LINKED` / `NOT_LINKED` / `SITE_NOT_CONNECTED`; when linked, `fullName`, `email`, `linkedAt`) | T-Solve account (AC-014.2) | 014 |
| `POST /api/v1/jira-app/account-link` (no body → `{ url }`, the `/jira-link?request=<id>` link; 403 MSG124 / MSG08, 503 MSG06) | T-Solve account (AC-014.6, AC-014.9) | 014 |

Forge: `jira:issueViewBackgroundScript` (opens the form), `jira:issuePanel` (panel), Modal for 4.1/4.2,
`requestJira` for the issue summary/description; `jira:globalPage` for the T-Solve account page (Apps section); `jira:projectPage` for the Ask T-Solve page, `invokeRemote` to the
endpoints above, and `CreateIssueModal`
(`@forge/jira-bridge`) for the prefilled Create dialog — whether it can prefill labels and where the page shows in
Jira's navigation are checked in a spike first (RESEARCH-004, open questions).

### Required from other modules
| Owner | Needed operation |
|-------|------------------|
| `identity` | `JiraAccountLinkLookup`: Jira accountId → the linked Active Account (AC-001.77); `JiraLinkRequestService.create` (AC-001.69) |
| `project` / `review` | Project of a Jira project key, the user's `project_membership` rows (today read by `ticket-review`'s `ProjectMembershipRepository`) |
| `ticket-review` (Phạm Tùng Dương) | The Solution Form ticket storage it reviews (`ticket`, source `SOLUTION_FORM`, entry time and due date, AC-009.11/AC-012.2); `GET /api/v1/jira-app/issues/{issueKey}/review-feedback` (MSG64) |
| `ai` (Trần Hải Anh) | Chat client (`LlmClient`) for the AI draft and rewrite — prompts owned here, a configured stand-in for demos |
| `chatbot` (Trần Hải Anh) | Answer a question with history for a given T-Solve user (`ChatbotAnswerService`, chatbot STORY-005 to STORY-007), callable from the Jira App API — to agree with the owner |
| `tracker` | Skip the suggestion job for an issue created with label `tsolve-asked` — a change of tracker AC-011.8 (tracker spec, KAN-22) |
| `worker` (Phạm Tùng Dương) | Used, not changed: `WorkerTenantService` (active Workspaces, run in a Workspace), `JobRunrRuntime` scheduler for a recurring job and per-writer jobs, `EmailOutboxService.queue(OutboundEmail)` of the shared email outbox (STORY-011) |
| `identity` | An Account's email and status by id (STORY-011) |

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
