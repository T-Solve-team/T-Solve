---
id: RESEARCH-004
title: Ask T-Solve's AI before creating a Jira issue
question: Where in Jira Cloud can a user ask T-Solve's AI about a problem before creating a ticket, and which way should T-Solve build?
status: complete
date: 2026-11-07
related: [RESEARCH-003, tracker STORY-011, chatbot, jira-app]
---

# Ask T-Solve's AI before creating a Jira issue

## Research Question
Can the user ask T-Solve's AI about a problem inside Jira, before a ticket exists, so a problem T-Solve already
knows is solved without a ticket ("deflection")? The first idea was a button in Jira's own Create issue dialog
that sends the Summary and Description the user typed to the AI.

## Context
New requirement (user, 2026-11-07). KAN-22 (tracker STORY-011) answers *after* an issue is created; this asks
*before*. Spike app `tsolve-create-spike` (throwaway Forge app, Developer Space "T-Sovle Jira extension",
installed on the dev site `bdang798.atlassian.net`, team-managed space SAM1), tested by the user in their own
browser on 2026-11-07.

## Findings

### The Create dialog cannot be changed directly
Jira Cloud's UI is Atlassian's code. Forge apps render only in extension points (UI Kit or iframes) and cannot
inject script or DOM into Jira pages. A browser extension could, but only for users who install it and it breaks
when Atlassian changes the page — not a product option.

### Option A — custom field with its own UI on the Create dialog (`jira:customField`, `edit.experience: issue-create`)
Tested:
- The field renders inline in the Create dialog (team-managed space, new create experience) with its own text,
  input and button. A button click works and its answer shows in the dialog.
- Closing the dialog without creating asks Jira's "Your changes won't be saved" → Discard creates nothing, so
  "answered, no ticket" works.
- The edit view's context has `renderContext: "issue-create"`, `issue: {type, typeId}`, `project: {id, key,
  type}`, `fieldValue` … but **not** the Summary or Description being typed. The user would type the question a
  second time in the field.
- Team-managed spaces: the app's field is global; an admin must add it to the space (space Fields → Add field)
  and drag it onto each work type. No public REST API does this for team-managed spaces (community answers,
  feature request JRACLOUD-91386); company-managed screens can be set by the app through
  `POST /rest/api/3/screens/{screenId}/tabs/{tabId}/fields`.

### Option A+ — add UI modifications (`jira:uiModifications`) to read Summary and Description
- UIM supports Global Issue Create for company- and team-managed Jira Software. `getFieldById('summary')`
  returns a string; `description` returns ADF (rich-text renderer). Values could reach the field through the
  app's resolver and Forge storage.
- Registering a UIM for a project and work type (`POST /rest/api/3/uiModifications`, `asApp`) returned 201 for
  SAM1/Task. Reading the values in the dialog was **not tested** (spike stopped by the decision below).
- Cost: a UIM app must declare the whole classic scope group — `read:jira-user`, `read:jira-work`,
  `write:jira-work`, `manage:jira-configuration`, `manage:jira-project` — so every customer admin sees
  admin-level permissions at install; plus one UIM registration per project and work type, plus the team-managed
  field setup above. Community reports onInit/onChange not running reliably on GIC (ECO-1187 for the
  "Create work item" flow).

### Option B — Rovo agent (`rovo:agent`)
A T-Solve agent in Atlassian's Rovo chat panel (opened from the top bar on every page; also `/ai` in the
editor) is the only official "floating chat". Requires Rovo on the customer's site: `bdang798` has none (the
Create placeholder reads "type / for actions" only); `t-solve.atlassian.net` has it. Not tested.

### Option C — a T-Solve chat page in Jira (`jira:projectPage` / `jira:globalPage`)
A page fully under T-Solve's UI: the user describes the problem, the AI answers from Published Solutions and
Wiki; "Solved" ends it, "Not solved, create ticket" opens Jira's Create dialog prefilled through
`CreateIssueModal` from `@forge/jira-bridge`. No admin scopes, no per-project setup, works for team-managed
spaces, and can reuse the `chatbot` module's backend. Cost: the user goes to the page instead of the Create
button. Where `jira:projectPage` shows in the new navigation (horizontal tab, "+" menu or sidebar) was not
checked.

### Images (evidence) in the chat
Forge UI-to-backend calls carry at most 500 KB (RESEARCH-003); search embeds text only, so an image needs OCR or
a vision model to help; `CreateIssueModal` prefill is assumed not to carry attachments (not verified).

## Recommendations
**Chosen: Option C, a separate "Hỏi T-Solve" page in Jira** (user, 2026-11-07). Text only first; images later
(compress in the browser, OCR on the backend, attach to the created issue). Options A/A+ are rejected for the
admin scopes, the per-project setup and the second typing of the question; B depends on Rovo being on every
customer's site. Uninstall `tsolve-create-spike` from `bdang798` and delete its UIM when this is recorded.

## Spike 2 — project page and prefilled Create dialog (KAN-294 Task 1, 2026-11-08)
Throw-away `jira:projectPage` (UI Kit, `render: native`) in the T-Solve app (`TSolve_JiraApp`, development
environment, `forge tunnel`) on `bdang798`, tested by the user in team-managed spaces SAM1 (TESTPRJ) and DP
(Donation Platform). Code not kept; `@forge/jira-bridge ^1.2.0` kept for Task 4.

| Question | Result |
|----------|--------|
| Where the page shows | A tab in the space's horizontal navigation, after Summary · List · Board · … · Docs (· Reports); shown in every space of the site; `useProductContext().extension.project` gives `id` and `key` |
| Space | `context.projectId` opens the dialog on that space ✅ |
| Work type without `issueTypeId` | Jira picks the space's default (Task) ✅ |
| Summary | Prefilled, Vietnamese intact ✅ |
| Labels | `labels: ['tsolve-asked']` prefilled and saved (SAM1-221, DP-117, DP-118) ✅ |
| Description (ADF) | `{version: 1, type: 'doc', content: [paragraph…]}` — four paragraphs, Vietnamese, prefilled and saved as separate paragraphs (DP-118) ✅ |
| Description (plain string) | Also prefilled and saved (DP-117) — undocumented (docs require ADF); not relied on |
| Reporter | The viewing user, not a service account ✅ |
| `onClose` after Create | `{payload: [{issueId: '11862'}]}` — the numeric id, **not** the key; Jira shows its own "Created Task DP-118" toast with a link |
| `onClose` after Cancel | Jira first asks "Your changes won't be saved" → Discard; then `payload` is `[]` |
| Scopes | No new scope: `forge deploy` and `forge lint` passed with only `read:jira-work` |
| Attachments | The dialog has its own Attachment field (user can add images by hand); prefill of attachments not tried |

Outcome: AC-010.1 and AC-010.9 can be built as written; no spec change.

## Open Questions
- ~~Where does `jira:projectPage` render in the new Jira navigation~~ — a tab in the space navigation (Spike 2).
- ~~Does `CreateIssueModal` return the created issue key~~ — no, only `issueId` (Spike 2); the key, if ever
  needed (images later), comes from `GET /rest/api/3/issue/{issueId}`.
- Owner decided: the story goes into `jira-app` (user, 2026-11-07); it calls the `chatbot` backend (Trần Hải Anh,
  not built yet — MSW mocks only) through a port with a stand-in. Which chatbot API, and how its UC-58 "create a
  Jira issue when no answer is found" (service account) fits with the prefilled Create dialog (user as reporter),
  is to agree with the chatbot owner.
- ~~Write the story on KAN-126's branch~~ — changed (user, 2026-11-07): the feature is Jira KAN-294, on its own
  branch `feat/KAN-294-jira-app-ask-tsolve` from `main`, with a `jira-app` spec holding only STORY-010 until
  KAN-126 (STORY-001..009) reaches `main`.
- Mentor feedback on Option C and on images in the first version (question sent by the user).

## Related Artifacts
- RESEARCH-003 (Forge Solution Form spike, on branch `feat/KAN-126-jira-app-solution-form`)
- `artifacts/03-modules/tracker/spec.md` STORY-011 (answer after the issue is created)
- `artifacts/03-modules/chatbot/`, `artifacts/03-modules/jira-app/`
- Sources: Forge manifest references for `jira:customField`, `jira:uiModifications`, `rovo:agent`; Jira bridge
  `uiModifications` API; "Build a Jira UIM app"; ECO-1187; JRACLOUD-91386 (community)
