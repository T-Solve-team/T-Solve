---
name: commit
description: Commit one feature's changes in the target repo — scoped staging, secret scan, conventional message — and finish a completed module's branches
type: utility
phases: [implement, evaluate]
agent: developer
---

# Commit

Commit the work for one task in each repo it changed, after tests pass (`verify` done). Never push, merge, or open a PR unless the user asks — Finishing a Module below is where they choose. Repos, branches, and layouts: skill.md "Repos and Git".

## Process

1. **Check state** — for each repo the task touched: `git -C <repo> status` and `git -C <repo> diff --stat`, on the module branch `feat/{module}` (a quick change: the current branch). If the tree has changes unrelated to this task, do not commit them; ask the user how to handle them.
2. **Stage only this task's files** — the files listed in the task (a quick change: its listed files) plus the tests you wrote. Use explicit paths (`git -C <repo> add path/a path/b`), never `git add -A` / `git add .`. In the monorepo layout never stage the harness folder with the code — its files go in the separate harness commit.
3. **Scan staged changes for secrets** — see below. Any real hit blocks the commit.
4. **Write the message** — conventional format, referencing the story:

   ```
   type(scope): imperative summary under 72 chars

   STORY-012: {one line on what this enables}
   ```

   A quick change has no story: its body is `Quick change: {what}`, plus the criterion id when it fixes one (`Fixes AC-002.1`).

   Types: `feat`, `fix`, `refactor`, `test`, `perf`, `docs`, `chore`, `build`, `ci`. Present tense, no trailing period, describe *what*, not *how*. Follow the repo's existing convention if it differs (check `git log --oneline -20`). Do not add AI attribution lines unless the project asks for them.
5. **Commit**, then record it: `bun harnessclaude/hooks/sdd.ts commit {module} {repo}` per repo key, and `repo@sha` in the task's session log — the reviewer finds the commits there. A quick change records nothing per module: once its commits are in, run `bun harnessclaude/hooks/sdd.ts quick done`.
6. **Split** if the staged diff mixes unrelated concerns (e.g. a refactor plus the feature): one commit per concern, refactor first.

## Secret Scan

```bash
git diff --cached | grep -nE "AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9_]{36,}|github_pat_|sk_live_|sk-ant-|xox[baprs]-|AIza[0-9A-Za-z_-]{35}|-----BEGIN [A-Z ]*PRIVATE KEY-----|(postgres|mysql|mongodb|redis)://[^:/ ]+:[^@ ]+@"
git diff --cached --name-only | grep -E "(^|/)\.env($|\.)|\.pem$|\.key$|\.p12$|credentials\.json$|secrets\.json$" | grep -vE "\.(example|sample|template)$"
```

On a hit:
1. Stop. Show the file and line with the value redacted (first 4 chars only).
2. Unstage it: `git restore --staged <file>`.
3. Suggest moving the value to an environment variable and adding the file to `.gitignore`.
4. If the secret was ever committed before, tell the user it must be rotated — removing it from history is not enough.

## Finishing a Module

When the user agrees a module is done (`sdd.ts status {module} completed`), its code still sits on `feat/{module}` in each of its repos. Finish it with the user:

1. Commit the harness files first (the status change and its events) — in the monorepo layout on `feat/{module}`, so the branch carries them.
2. `bun harnessclaude/hooks/sdd.ts repos` — the module's repos, their branches, and uncommitted files. A repo with uncommitted changes: stop and ask the user.
3. Ask the user once how to finish; they may answer per repo. The monorepo layout has one branch for all repo keys, so one answer. The harness repo of a workspace has no module branch — nothing to finish there.
   - **Merge** (deletes the merged branch): `git -C <repo> switch <default_branch>`, then `git -C <repo> merge --no-ff --no-commit feat/{module}` — the merge stays uncommitted while you run the repo's tests (`test_cmd`, run-tests skill). They pass: `git -C <repo> commit --no-edit`, then `git -C <repo> branch -d feat/{module}`. A conflict or a failing test: `git -C <repo> merge --abort`, which leaves `default_branch` as it was and keeps the branch; stop and tell the user — never resolve it on your own.
   - **Pull request**: `git -C <repo> push -u origin feat/{module}`, then from the repo folder `gh pr create --base <default_branch>` with a title and a body listing the module's stories and review files. No `remote` or no `gh`: tell the user what is left for them to do.
   - **Keep**: leave the branch; the user merges it later.
4. Report per repo: the merge commit, the PR URL, or "kept on `feat/{module}`".

## Never Without Explicit User Approval

`git push --force`, `git reset --hard`, rewriting published history, committing to the repo's default branch (`default_branch` in manifest.yaml, else `main`/`master`) when the repo uses feature branches, deleting branches.
