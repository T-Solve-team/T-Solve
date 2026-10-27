---
name: ai
owner: Trần Hải Anh
repos: [default]
dependencies: [platform, be-foundation]
complexity: medium
paths:
  - backend/src/main/java/vn/tsolve/ai/**
  - backend/src/test/java/vn/tsolve/ai/**
  - frontend/openapi/**
  - frontend/src/shared/api/generated/**
  - docs/bug-log.md
---

# Module: ai

**Owner:** Trần Hải Anh

## Description
The AI gateway that every AI feature goes through (GBR-12). It holds the clients of the two models, both on Ollama
on the T-Solve AI VPS (D41): a chat LLM and bge-m3 embeddings (1024 dimensions) for everything (D3); OpenRouter and
every third-party AI service are not used. The AI VPS is reachable only from the application VPS over a private,
encrypted link (`OLLAMA_BASE_URL`). Only PII-masked text is sent to the models. AI is optional: a failure is
reported to the caller, who falls back, and never breaks the system. The module also serves the AI draft and AI
rewrite of a Solution (UC-19) used by the Solution Form, and the model benchmark. It stores no Workspace data; the
callers (`search`, `wiki`, `triage`, `chatbot`) keep their own Workspace-scoped data. The backend package `ai` is
*proposed* (`ai/client/` for the Ollama clients).

## Responsibilities
- PII masking of every text before it goes to a model (KAN-138, GBR-12).
- `LlmClient` and `EmbeddingClient` with their Ollama implementations; timeouts and failure reporting (KAN-139).
- AI draft Solution and AI rewrite Solution endpoints (UC-19; the writer reads and clicks Finish themselves)
  (KAN-142, KAN-143).
- The benchmark of the models on CPU; its result goes to the team notes (KAN-140, assignee Đặng Nhật Minh).

## Interfaces

### Exposed
| Interface | Type | Description |
|-----------|------|-------------|
| LlmClient | Java (`ai/client`) | Chat completion on the Ollama LLM |
| EmbeddingClient | Java (`ai/client`) | bge-m3 embeddings, 1024 dimensions |
| PII masking | Service interface | Mask personal data in a text before any model call |
| AI draft / rewrite | REST | Draft or rewrite a Solution for the Solution Form (UC-19) |

### Consumed
| Dependency | Interface | Description |
|------------|-----------|-------------|
| platform | TenantContext, deployment | The Workspace of a draft / rewrite request; `OLLAMA_BASE_URL` to the AI VPS |
| be-foundation | Roles, errors | `@PreAuthorize` on the draft / rewrite endpoints; `ProblemDetail` errors |

## Target Repos
- default: backend/ (`ai`, proposed).

## Related Artifacts
- Spec: `artifacts/03-modules/ai/spec.md` (written when the module is planned)
- Map: `artifacts/03-modules/module-map.md`
- Stories: KAN-138, KAN-139, KAN-140, KAN-142, KAN-143
