---
module: {module name}
date: {date}
owner: BA
---

# Spec: {module name}

## Changelog

| Date | Story | Change | Reason |
|------|-------|--------|--------|
| {date} | STORY-001.. | Initial spec | — |

## Requirements

### Functional
1. {requirement — detail it as acceptance criteria under a story}

### Non-Functional
1. {requirement with measurable target}

## User Stories

### STORY-001: {title}
- As a {role}, I want to {action} so that {benefit}
- Acceptance:
  - AC-001.1: WHEN {trigger}, THE {system} SHALL {response}
  - AC-001.2: IF {unwanted condition}, THEN THE {system} SHALL {response}

### STORY-002: {title}
- As a {role}, I want to {action} so that {benefit}
- Acceptance:
  - AC-002.1: WHILE {state}, THE {system} SHALL {response}

## Out of Scope
- {what this module deliberately does not do, and where it goes instead (a later module or iteration)}

## Interface Contracts

### Exposed by this module
{APIs, events, types this module provides}

### Required from other modules
{What this module needs from dependencies}

## Definition of Done
- [ ] Every acceptance criterion has a test whose name starts with its AC id
- [ ] {checklist item}

<!-- ACCEPTANCE CRITERIA: one testable statement per item, id AC-<story>.<n> (never reuse a
     removed id), in EARS form: THE <system> SHALL <response>, optionally led by
     WHEN <trigger>, WHILE <state>, IF <unwanted condition> THEN, or WHERE <feature is included>.
     A story with a WHEN criterion also gets an IF … THEN criterion for its failure path.
     OUT OF SCOPE: name what is excluded, so it is not built by accident; adding it later is a
     spec change (Changelog row, re-approval).
     OPEN QUESTIONS: where the answer is missing, write [NEEDS CLARIFICATION: <question>] and ask
     the user — never fill the gap with a guess. `bun harnessclaude/hooks/sdd.ts check <module>`
     fails while any remain.
     SPEC EDIT RULE: Any edit to this file that changes business logic, requirements, stories, or
     acceptance criteria MUST add a new row to the Changelog table above (Date, Story if
     applicable, Change summary, Reason). Once the user has approved the spec, any edit locks the
     module's code until they approve the new version (`sdd.ts approve <module>`). -->
