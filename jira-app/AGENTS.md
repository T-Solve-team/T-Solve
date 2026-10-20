# T-Solve Jira App — guide for developers and AI coding agents

> **Read this file first.** It is the entry point for every person and AI agent working in `jira-app/`.
> `CLAUDE.md` only imports this file, so Claude Code, Codex and Cursor read the same text.
> Repo-wide rules (branch names, commit messages, git hooks, CI) are in the root `README.md` and apply here too.

## What this folder is

The **T-Solve Jira App**: the screens Staff use inside Jira, built on **Atlassian Forge** with **UI Kit**
(Atlassian's own components, so the app looks like Jira). It is a separate app from the T-Solve web app in
`frontend/` and shares only the API contract (`frontend/openapi/tsolve-api.yaml`) with it.
Screens and texts come from the SRS (Jira App screens, MSG catalog); use the glossary words exactly
(Ticket, Solution, Published ticket, Project Manager, …), as in `frontend/AGENTS.md` rule 1.

## Status

The folder is prepared, but the Forge app itself is **not created yet**. The Jira App owner does the
one-time setup below; after that, this section is replaced by the real commands and module list.

## One-time setup (Jira App owner)

Needs Node 22 or 24 (the repo uses 24, see `frontend/.nvmrc`) and an Atlassian account.

1. Install the Forge CLI: `npm install -g @forge/cli`, then `forge --version` (14.x).
   If it fails with `spawn ts-node ENOENT`, clear `NODE_ENV` (PowerShell: `$env:NODE_ENV=""`) and retry.
2. Create an API token at https://id.atlassian.com/manage-profile/security/api-tokens →
   **Create API token with scopes** → app **Forge**. Run `forge login` and enter your email and the token.
   The token stays in your computer's keychain: never put it in a file, the repo or a chat.
3. From the **repo root**, create the app into this folder:

   ```bash
   forge create -d jira-app tsolve-jira-app
   ```

   Choose **UI Kit** → **Jira** → the issue panel template (`jira-issue-panel`).
   `forge create` registers the app (its id goes into `manifest.yml`; the id is not a secret) and runs
   `npm install`. If it stops because a file already exists in `jira-app/`, run it again with
   `-d ../tsolve-jira-tmp`, copy everything from that folder into `jira-app/` (keep this `AGENTS.md` and
   `CLAUDE.md`), and delete the temporary folder.
4. Check it: `cd jira-app && forge lint` reports no problems; `git status` shows only new files in `jira-app/`.
5. Open a PR with the generated files. DevOps then adds the Jira App to CI and Dependabot.

## Rules

1. **UI Kit only** (`@forge/react` components). No Custom UI, no `@atlaskit` packages, no hand-written HTML.
2. **No secrets in the repo.** Tokens, keys and backend credentials are Forge variables:
   `forge variables set --encrypt <NAME> <value>` per environment. `manifest.yml` holds no secrets.
3. **Ask for the fewest permissions.** Every scope or egress URL added to `manifest.yml` is reviewed in the PR;
   say why it is needed.
4. **Never log ticket content or personal data** (issue text, names, emails). Log ids, status codes and error
   types only — the same rule as the backend.
5. **Talking to the T-Solve backend** goes through one module (one place for the base URL and error
   handling), using the endpoints in the OpenAPI spec. Never call the backend from several places.
6. **Before you say "done":** `forge lint` passes, and the change works in `forge tunnel` on the development site.

## Commands (after setup)

| Command | What it does |
|---|---|
| `forge tunnel` | Runs your local code against the development site while you work |
| `forge deploy` | Uploads the app to the development environment |
| `forge install` | Installs the app on a Jira site (once per site) |
| `forge lint` | Checks `manifest.yml` and the code |
| `forge logs` | Shows the app's logs |
