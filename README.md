# T-Solve - Ticket Solution Repository Application

Knowledge layer that sits next to a ticket tracker (Jira, ...): it imports **resolved tickets**, turns valuable resolutions into
**reviewed solutions**, and lets handlers **search and reuse** them. T-Solve owns the *solution* lifecycle; the source tracker owns the *ticket* lifecycle.

```
Resolved Ticket -> Knowledge Candidate -> Review -> Published Solution -> Search / Reuse -> Feedback
```

## Repository layout

```
t-solve/
├── backend/               Spring Boot 3 (Java 21, Maven) - modular monolith
│   └── src/main/java/vn/tsolve/
│       ├── ticket/        resolved-ticket snapshots + idempotent import
│       ├── knowledge/     solution + lifecycle (DRAFT -> ... -> ARCHIVED)
│       ├── identity/      (planned) login, RBAC/ABAC
│       ├── workspace/     (planned) workspaces and taxonomy
│       ├── search/        (planned) keyword + semantic search
│       ├── analytics/     (planned) dashboard, reuse metrics, audit
│       └── common/        config, error handling
│   └── src/main/resources/db/migration/   Flyway migrations (schema owner)
├── frontend/              React 19 + TypeScript + Vite + Ant Design 6 (see frontend/README.md)
├── infra/                 (planned) server compose files, main Caddyfile, monitoring, backup scripts, runbook
├── .github/               (planned) CI/CD workflows, CODEOWNERS, PR template, Dependabot
├── docker-compose.yml     PostgreSQL 16 + pgvector for local dev
├── .editorconfig          editor defaults (LF, UTF-8, indentation)
└── .env.example           local backend settings (copy to .env)
```

Project documents (SRS, design, ADRs, reports) are not in this repo; they live in the team's Google Drive.

## Prerequisites

JDK 21, Maven 3.9+, Node 24 LTS (`frontend/.nvmrc`), Docker Desktop (for PostgreSQL).

## Run locally

```bash
# 1. database
docker compose up -d db

# 2. backend  (http://localhost:8080, health: /actuator/health)
cd backend
mvn spring-boot:run

# 3. frontend (http://localhost:5173, proxies /api -> :8080)
cd frontend
npm ci
npm run dev
```

The frontend also runs without the backend (mock mode); see `frontend/README.md`.

## Tests

```bash
cd backend  && mvn test
cd frontend && npm run format:check && npm run lint && npm run typecheck && npm run test && npm run build
```

## API (current skeleton)

| Method | Path | Description |
|---|---|---|
| POST | `/api/tickets/import` | Import a resolved ticket snapshot (idempotent on `source` + `externalId`) |
| GET | `/api/tickets`, `/api/tickets/{id}` | List / read ticket snapshots |
| POST | `/api/solutions` | Create a DRAFT solution |
| PUT | `/api/solutions/{id}` | Edit a DRAFT solution |
| POST | `/api/solutions/{id}/transition?to=IN_REVIEW` | Move through the lifecycle (illegal moves return 409) |
| GET | `/api/solutions[?status=]` | List solutions; **PUBLISHED by default** |

## Branching and conventions

Trunk-based: `main` is always deployable and protected; every change goes through a short-lived branch and a pull request.

- **Branch:** `<type>/<JIRA-KEY>-<module>-<short-description>`, e.g. `feature/TS-42-solution-draft-endpoint`.
  Types: `feature/`, `fix/`, `hotfix/`, `chore/`, `test/`.
- **Commit and PR title:** Conventional Commits with the Jira key, e.g. `feat(solution): add draft creation endpoint (TS-42)`.
- **Merge:** squash and merge after CI is green and a CODEOWNER approves; the branch is deleted after merge.
- Sync your branch with `main` at least daily. Keep PRs under ~400 changed lines.
- Schema changes only through new Flyway migrations; never edit a merged migration.
- Never commit secrets: `.env` files are git-ignored, only `.env.example` files are committed.
- Only `PUBLISHED` solutions appear in default reuse results.

`TS` is a placeholder until the real Jira project key exists (`TS-0` = no issue yet).
