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
│   └── src/main/resources/db/changelog/   Liquibase changelog (schema owner): master XML + SQL changesets
├── frontend/              React 19 + TypeScript + Vite + Ant Design 6 (see frontend/README.md)
├── infra/                 (planned) server compose files, main Caddyfile, monitoring, backup scripts, runbook
├── .github/               (planned) CI/CD workflows, CODEOWNERS, PR template, Dependabot
├── docker-compose.yml     PostgreSQL 16 + pgvector for local dev
├── .editorconfig          editor defaults (LF, UTF-8, indentation)
└── .env.example           names of the backend settings (copy to .env and fill in)
```

Backend architecture and coding conventions (layered, one folder per module, MapStruct, Liquibase, env-only
config): `harnessclaude/artifacts/03-modules/architecture.md`.

Project documents (SRS, design, ADRs, reports) are not in this repo; they live in the team's Google Drive.

## Prerequisites

JDK 21, Maven 3.9+, Node 24 LTS (`frontend/.nvmrc`), Docker Desktop (for PostgreSQL).

## Git hooks (one-time setup)

Every commit is checked on your machine before it is created; CI runs the same checks on the PR.

```bash
# in the repo root, once after cloning (PowerShell, Git Bash or the IntelliJ / VS Code terminal)
winget install --id Gitleaks.Gitleaks   # secret scanner; open a new terminal afterwards
npm ci                                  # installs husky, lint-staged, commitlint and turns the hooks on
cd frontend && npm ci                   # the hook uses the frontend's own Prettier and ESLint
```

| Hook | What it does |
|---|---|
| pre-commit | gitleaks scans the staged changes for secrets; ESLint `--fix` and Prettier fix the staged `frontend/` files (generated API code is skipped) and add the fixes to the commit |
| commit-msg | the message must be `type(scope): summary (KAN-123)`, e.g. `feat(review): add the reject popup (KAN-42)` |

If a hook stops your commit, read the message, fix the cause and commit again. `git commit --no-verify`
skips the hooks but not CI. Rules: `commitlint.config.mjs`, `lint-staged.config.mjs`, `.husky/`.

## Run locally

```bash
# 0. settings, once: copy the names and fill in the values (DB values = those of the db service in docker-compose.yml)
cp .env.example .env

# 1. database
docker compose up -d db

# 2. backend  (health: /actuator/health on SERVER_PORT; Liquibase applies the changelog on start)
cd backend
mvn spring-boot:run

# 3. frontend (http://localhost:5173, proxies /api -> :8080)
cd frontend
npm ci
npm run dev
```

The frontend also runs without the backend (mock mode); see `frontend/README.md`.

## AI context (Claude Code)

`/tsolve-context [topic]` loads the requirements and design (SRS, reports, plan) into Claude Code, e.g.
`/tsolve-context UC-30`. It converts your local copy of the Google Drive docs folder to Markdown in `<docs>/_context/`
(`scripts/build_context.py`) and reads the project summary in `.claude/tsolve-project.md`. One-time setup:

```bash
pip install "markitdown[docx,pdf,xlsx]"
```

Then create `.claude/settings.local.json` (git-ignored) with the path of your docs folder and restart Claude Code:

```json
{
  "env": { "TSOLVE_DOCS": "C:/path/to/SEP490_G89" },
  "permissions": { "additionalDirectories": ["C:/path/to/SEP490_G89"] }
}
```

## AI workflow (HarnessClaude)

AI-assisted work follows **HarnessClaude** (`harnessclaude/`): requirements → spec → design → tasks → code →
independent review, enforced by hooks — no code without an approved spec. Needs [Bun](https://bun.sh).
Read [harnessclaude/README.md](harnessclaude/README.md) before your first session.

## Tests

```bash
cd backend  && mvn verify          # tests + Spotless format check (mvn spotless:apply fixes formatting)
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

- **Branch:** `<type>/<JIRA-KEY>-<module>-<short-description>`, e.g. `feature/KAN-42-solution-draft-endpoint`.
  Types: `feature/`, `fix/`, `hotfix/`, `chore/`, `test/`.
- **Commit and PR title:** Conventional Commits with the Jira key, e.g. `feat(solution): add draft creation endpoint (KAN-42)`
  (checked by the commit-msg hook and in CI).
- **Merge:** squash and merge after CI is green and a CODEOWNER approves; the branch is deleted after merge.
- Sync your branch with `main` at least daily. Keep PRs under ~400 changed lines.
- Schema changes only through a new Liquibase changeset (`db/changelog/changes/NNN-<slug>.sql`, included in
  `db.changelog-master.xml`); never edit an applied changeset.
- Config files hold only `${VAR}` placeholders, never real values or defaults; values live in `.env` or the server environment.
- Never commit secrets: `.env` files are git-ignored, only `.env.example` files are committed.
- Only `PUBLISHED` solutions appear in default reuse results.

The Jira project key is `KAN` (older commits use the placeholder `TS-0`).
