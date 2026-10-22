---
name: jira-app
repos: []
dependencies: [tracker]
complexity: medium
paths: [jira-app/**]
---

# Module: jira-app

## Description
The T-Solve Jira App: an Atlassian Forge app built with UI Kit. It reports ticket events (created, moved to the
Done status category) to the T-Solve server, posts internal comments (triage decision, Done reminder), and
gives Staff the Issue Panel (Solution suggestions, search again, Done warning) and the Solution Form (AI draft,
submit, "No Solution needed"). It holds no business logic: everything goes through the server's Jira App API.

## Responsibilities
- Forge `manifest.yml`: triggers, issue panel, Forge Remote to the T-Solve server, minimal scopes
- Trigger on issue created → report the event
- Trigger on issue updated → report only when the status changed into the Done category
- Bounded retry for event delivery (e.g. Async Events queue); final failure logged with ids only
- Internal comments: triage decision on create; reminder mentioning the assignee on Done without a Solution
  (internal on Jira Service Management projects)
- Issue Panel: suggestions, "search again", warning while a Done ticket has no Solution
- Solution Form UI: show AI draft, edit, submit, "No Solution needed" with a reason
- One client module for all server calls; UI Kit only; glossary words exact; no ticket content in logs

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Issue Panel + Solution Form | Jira UI (UI Kit) | Used by Staff inside Jira |
| Internal comments | Jira issue comments | Triage decision, Done reminder |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| tracker | Jira App API (Forge Remote, FIT) | Events, panel data, search again, Solution Form actions |
| Jira Cloud (external) | Forge events `avi:jira:created:issue`, `avi:jira:updated:issue`; comment REST API | Event source; comments |
| `identity` (other owner) | T-Solve Sign-in screen / account link | Needed before Solution Form submit |

## Target Repos
- monorepo `jira-app/`: Forge app source, manifest, tests

## Related Artifacts
- Spec: `artifacts/03-modules/jira-app/spec.md`
- Brief: `artifacts/02-discovery/context-brief.md`
- Rules: `jira-app/AGENTS.md`
