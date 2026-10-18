---
name: onboard
description: Onboard a developer to this harnessclaude workspace — analyze each repo (stack, test command, default branch, role), check repo health, and brief them on the project state. Use when someone is new to the project, says "onboard", or asks "is my workspace set up?" or "what is this project?".
type: utility
---

# Onboard

Run this after `harnessclaude init`, and whenever someone new joins. This skill reads and reports. It writes only the `repos` fields of `manifest.yaml` that the user approves. It never clones, pulls, stashes, switches branches, or writes code.

## 0. Find the harness folder

1. The harness folder is the folder whose `manifest.yaml` has a top-level `harnessclaude:` marker — the current folder or one of its direct child folders. Its name can be anything (`harnessclaude/` here).
2. If no folder has the marker, stop and tell the user to run `harnessclaude init` (an older workspace without the marker: `harnessclaude upgrade`).
3. Read `manifest.yaml`, `progress.json`, and the "Repos and Git" section of `skill.md`. Follow that section for every repo path and git command.

## 1. Analyze repos

`bun harnessclaude/hooks/sdd.ts repos` lists each repo with its folder, role, branch, and uncommitted files. Then, for each entry in `repos`:

1. Find the repo folder: `harnessclaude/<path>`. If it does not exist, skip it; step 2 reports it.
2. Read the build files that exist: `package.json` and its lock file, `go.mod`, `pom.xml`, `build.gradle` or `build.gradle.kts`, `pyproject.toml`, `requirements.txt`, `Cargo.toml`, `composer.json`, `Gemfile`, `*.csproj`.
3. Decide:
   - `stack` — a short kebab-case name: `node-express`, `react-vite`, `nextjs`, `go`, `java-spring`, `python-fastapi`, …
   - `test_cmd` — the command that runs the tests, from the package scripts or the build tool: `bun test`, `pnpm test`, `go test ./...`, `mvn test`, `pytest`. Not sure → leave it empty.
   - `default_branch` — `git -C <repo> symbolic-ref --short refs/remotes/origin/HEAD` without the `origin/` prefix. If that fails, use the current branch (detached HEAD: see below); use `main` only when no branch can be found, and say so in the table.
   - `role` — `frontend` for a UI app (React, Vue, Angular, Svelte, Next.js, Nuxt, a Vite web app), `backend` for an API or a service, `e2e` for a repo that holds mainly end-to-end tests, `other` for the rest. Keep an existing `role`; if it looks wrong, ask.
   - Current branch — `git -C <repo> branch --show-current`. Empty output means a detached HEAD: report "detached HEAD at <short sha>" (`git -C <repo> rev-parse --short HEAD`).
4. Show one table: repo, stack, test_cmd, default_branch, role. Mark the values that would change.
5. Ask: "Write these values into manifest.yaml?"
6. Only after the user agrees, change only these fields of the `repos` entries, in this order: `path`, `remote`, `default_branch`, `role`, `stack`, `test_cmd`. Keep every other line and comment of `manifest.yaml`:

   ```yaml
   repos:
     api:
       path: ../api
       remote: git@github.com:acme/api.git
       default_branch: main
       role: backend
       stack: node-express
       test_cmd: bun test
   ```

**No repos listed:** say "No repos listed in manifest.yaml." In the monorepo layout the repo that holds the harness folder is the only repo, with the key `default` (skill.md "Repos and Git") — analyze it and offer to record it as `default: { path: .. }`. In the workspace layout suggest adding the code repos (`api: { path: ../api }`). Then continue with steps 2–4.

## 2. Health check

Check each repo in `repos`. In the workspace layout also check the harness folder, because it is its own repo.

| Check | How | Report |
|---|---|---|
| Missing | the folder does not exist | "missing — run `harnessclaude init` to clone it" when the repo has a `remote`, else "missing, no remote — clone it by hand" |
| Not a repo | no `.git` | "not a git repo" |
| Uncommitted changes | `git -C <repo> status --porcelain` prints lines | "uncommitted changes (N files)" |
| Branch | only when `default_branch` is known: the current branch differs; on a detached HEAD report "detached HEAD at <short sha>" | "on `<branch>`, default is `<default_branch>`". If a module in progress.json uses this branch (`modules.*.branch`), add "(module `<module>`)" and do not show it as a problem |
| Workspace | `bun harnessclaude/hooks/sdd.ts audit` | "audit OK", or its failures (missing repos, commit drift, unapproved specs …) |

The harness folder has no `default_branch`: report only its current branch. Do not fetch. Do not fix anything. Report only.

## 3. Briefing

Read, if they exist: `artifacts/00-baseline/concept-brief.md`, `artifacts/02-discovery/context-brief.md`, `artifacts/03-modules/architecture.md`, `artifacts/_registry/artifact-index.md`, `progress.json`.

Write a short briefing a newcomer can take in at a glance:
- What the project is, in one or two sentences.
- The current phase and each module's status, with spec approved or not and features passing.
- For each active module: its branch and its repos.
- The next step, by the "How to dispatch" section of `skill.md`.

**Empty project** — no artifacts besides the registry and no modules in progress.json: the briefing is only "No artifacts yet. Start with `ideate` (you have a raw idea) or `discover` (you know what to build, or the code already exists)."

## Output

```markdown
## Workspace
Layout: {monorepo | workspace} · harness folder: {folder name}

| Repo | Stack | Role | Branch | Status |
|------|-------|------|--------|--------|

## Project
{briefing}

## Next step
{one line}
```

## Rules

- Ask before you write; one question at a time.
- Never clone, pull, push, stash, or switch branches.
- Never write code.
