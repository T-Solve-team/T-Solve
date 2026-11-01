---
name: chatbot
owner: Trần Trung Hiếu (web app, done) · Trần Hải Anh (backend)
repos: [default]
dependencies: [search, wiki, ai, project-integration]
complexity: medium
paths:
  - backend/src/main/java/vn/tsolve/chatbot/**
  - backend/src/test/java/vn/tsolve/chatbot/**
  - backend/src/main/resources/db/changelog/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - frontend/src/features/chatbot/**
  - docs/bug-log.md
---

# Module: chatbot

**Owner:** Trần Trung Hiếu (web app, done) · Trần Hải Anh (backend)

## Description
Knowledge Chatbot (UC-51–53, UC-58; KAN-48): answers with citations from the Solutions of all Published tickets (without ticket details) and the Wiki documents the user may see; history for the session only; ratings (GBR-36). The backend package `chatbot` is proposed and built later; until then MSW mocks serve the contract (KAN-48). The web folder `frontend/src/features/chatbot/` moved from `fe-foundation` into these paths with the plan (KAN-69).

## Responsibilities
- Questions and answers with citations; MSG109 when no source is found.
- Session conversations, cleared at logout (UC-02).
- Helpful / Not helpful on an answer; knowledge gaps.
- Classify a question before searching (GBR-39): off-topic and prompt-injection questions get their own MSG (KAN-243).
- UC-58 Create a Jira issue when no answer is found (GBR-40): only to support Departments (e.g. HR, IT Support),
  issue type Task, created with the Workspace's Jira service account, the asking Employee as reporter
  (KAN-241 API, KAN-242 web form).
- Screen 12.1.

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| Chatbot | REST | Questions, answers, ratings |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| search | Retrieval | Sources for answers |
| wiki | Wiki documents | Sources for answers |
| project-integration | Tracker client | Create the Jira issue of UC-58 with the Workspace service account |

## Target Repos
- default: backend/ (`chatbot`, proposed), frontend/ (`chatbot`).

## Related Artifacts
- Spec: `artifacts/03-modules/chatbot/spec.md`
- Design: `artifacts/04-plans/chatbot/design.md` · Tasks: `artifacts/04-plans/chatbot/iteration-1/tasks.md`
- Map: `artifacts/03-modules/module-map.md`
- Stories: KAN-48, KAN-69…73, KAN-75, KAN-76, KAN-178…183, KAN-199, KAN-241, KAN-242, KAN-243
