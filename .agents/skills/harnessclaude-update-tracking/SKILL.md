---
name: harnessclaude-update-tracking
description: Change module and feature status in progress.json through sdd.ts, which enforces the state machine
type: utility
---

# Update Tracking

Every status change goes through `sdd.ts`, run from the project root. It refuses a transition the state machine does not allow (and says which ones are), writes `progress.json` (`status`, `currentPhase`, `lastUpdated`), and appends the event to `events/events.jsonl`. **Never edit a status in progress.json by hand.**

```bash
bun harnessclaude/hooks/sdd.ts status  {module} {status}             # move a module
bun harnessclaude/hooks/sdd.ts feature {module} {feature} {status}   # add a feature (pending) or move it
```

A feature's key is its task slug — the `{slug}` of `task-{N}-{slug}` used by the session log and the review files.

## State Machine

**Modules** — a module enters progress.json as `decomposed` (decompose phase).

| From | Allowed next | Also required |
|------|--------------|---------------|
| decomposed | planned | the user approved the current spec (`sdd.ts approve`) |
| planned | implementing | |
| implementing | evaluating, blocked | |
| evaluating | implementing, completed, blocked | `completed`: every feature is `passing` |
| blocked | implementing, planned | `planned`: the current spec is approved |
| completed | planned | change request only: the spec change is in place and approved |

Code for a module is writable only while it is `planned`, `implementing`, or `evaluating` with an approved spec — the gate hook enforces that, not this table.

**Features** — a feature enters its module as `pending`.

| From | Allowed next |
|------|--------------|
| pending | in-progress |
| in-progress | evaluating, blocked |
| evaluating | passing, fixing, blocked |
| fixing | evaluating, blocked |
| blocked | in-progress |
| passing | — (final) |

A feature goes to `blocked` after its 3rd failed review, or when the developer stops after 3 failed fixes (debug skill) — then tell the user; do not retry on your own.

## When Each Change Happens

| Moment | Command |
|--------|---------|
| Decompose writes a module | `status {module} decomposed` |
| Plan finishes and the user approved the spec | `status {module} planned`, then `feature {module} {slug} pending` for every task |
| Implement starts on a planned module | `status {module} implementing` — also records `branch: feat/{module}` and the module's `repos` |
| A task starts / is committed | `feature … in-progress` / `commit {module} {repo}` per repo, then `feature … evaluating` |
| Review passes / fails | `feature … passing` / `feature … fixing` (3rd failure: `blocked`) |
| All features passing and the user agrees the module is done | `status {module} evaluating`, then `status {module} completed`; then finish its branches (commit skill, Finishing a Module) |
| Change request on a completed module | spec change approved, then `status {module} planned` |

If all features of a module are `passing`, suggest completing the module to the user.

## Commits (skill.md "Repos and Git")

`bun harnessclaude/hooks/sdd.ts commit {module} {repo} [sha]` records the module's last commit in a repo — that repo's HEAD unless a SHA is given — in `lastCommit` (a map: repo key → SHA, never a plain string) and logs the event. The repo key is a manifest `repos` key, or `default` (the repo holding the harness folder in a monorepo; the module's first repo in a workspace).

**Drift.** `sdd.ts audit` checks every `implementing` / `evaluating` module: each recorded commit must still be in the history of the module branch (`git merge-base --is-ancestor`). Later commits on the branch are normal — including harness-only commits in the monorepo layout. It warns when a recorded commit is no longer on the branch (history rewritten) or the branch is gone (merged or deleted). Report it to the user; after they agree, record the right commit with `sdd.ts commit` — in the monorepo layout the latest commit on the branch that changed files outside the harness folder: `git log -1 --format=%H feat/{module} -- ':(top,exclude)harnessclaude'`.

## Iterations (only if `iterations.enabled: true` in manifest.yaml)

When every task of the current iteration is `passing`:
1. Increment `currentIteration` in progress.json and set the iteration file (`artifacts/07-iterations/iteration-{N}.md`) status to `complete`.
2. If the next iteration is already planned, set its file to `in-progress`; otherwise suggest the plan skill to slice it from the master plan.

Iterations cannot be skipped — iteration N completes before N+1 starts.

## Check

`bun harnessclaude/hooks/sdd.ts audit` checks the whole workspace: valid statuses, the artifacts each status requires, approvals, tasks against stories, review verdicts for passing features. Run it before a phase transition when in doubt.
