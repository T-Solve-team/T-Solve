---
id: RESEARCH-003
module: jira-app
status: complete
date: 2026-11-01
---

# RESEARCH-003: Forge limits for the Solution Form (KAN-126, Task 1)

## Question
Can the Forge app (1) open the Solution Form by itself when an issue moves to Done, and (2) send up to 5 Evidence
files of 10 MB to the T-Solve backend through `invokeRemote`?

## Findings (documentation)
- **`invokeRemote` body is JSON only** — it is passed through `JSON.stringify(body)`; multipart / FormData is not
  described. Source: Forge docs, `invokeRemote` (UI bridge).
- **Front-end request payload limit: 500 KB** for UI bridge calls (`invoke`, `invokeRemote`); response limit 5 MB;
  invocation request limit 5 MB. Source: Forge docs, Invocation limits.
  → A 10 MB Evidence file cannot be sent in one `invokeRemote` call (base64 adds a third). **The design's
  "multipart on POST …/solution" does not work.**
- **`jira:issueViewBackgroundScript`** supports UI Kit (`render: native`), runs invisibly on the issue view only.
  Source: Forge manifest reference.
- **`JIRA_ISSUE_CHANGED`** (`events.on` from `@forge/bridge`) is client side, only for issue view modules, fires
  on issue update or comment, may lag a few seconds, and Atlassian states not every change is guaranteed to be
  received. A transition made on the board (no issue view open) cannot reach it. Sources: Forge manifest
  reference (background script), Atlassian developer community "Real time issue events (EAP)".
  → The panel button (AC-001.4) is required, not only a fallback.

## Verified on the development site (bdang798.atlassian.net, 2026-11-01, forge tunnel)
- [x] The background script runs on the issue view, including the issue detail opened from the board.
- [x] A transition In Progress → Done made in the issue view fires `JIRA_ISSUE_CHANGED`; re-reading the status
  with `requestJira` took 265 ms. Payload: `changes: [{changeType: "updated", atlassianId}]` — **no status in
  the payload**, so the status must be read again and compared with the last known category.
- [x] A UI Kit `Modal` (`@forge/react`) rendered by the background script opens on top of the issue view.
  (A bridge `Modal` with its own resource fails to deploy: an unreferenced resource is treated as a Custom UI
  folder.)
- [x] A transition made by dragging a card to Done on the board, with no issue view open: nothing happens (no
  event, no form) — confirmed. The panel button is the only way to reach the form in that case.

## Consequences for the design
- Open the form only when the change's `atlassianId` is the viewer's own accountId, so other people viewing the
  same issue do not get it.
- The Modal is a component of the background script's UI Kit tree, not a separate resource.
- The panel button (AC-001.4) stays: board drags and missed events never reach the background script.

## Decision
Evidence is left out of the Jira App (user, 2026-11-01) and added on the web app. Sending parts of 300 KB
was the alternative considered.
