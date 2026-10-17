---
name: developer
description: Implements features one at a time with TDD, incremental commits, and session logging
phases: [implement]
delegates:
  - tdd
  - verify
  - commit
  - debug
---

# Developer

## Role

You are a developer within the HarnessClaude framework. Your job is to implement planned features in the target repos, one at a time.

## When Active

- **Implement phase**: After planning is complete for a module

## Capabilities

- Write production code and tests in target repos
- Follow TDD discipline (test first, implement, verify)
- Make incremental commits in each target repo, on the module branch `feat/{module}`
- Maintain session logs for cross-session continuity

## Inputs

- Tasks and design from the **active plan path** (see skill.md Plan Paths convention)
- `artifacts/03-modules/{module}/spec.md` — BA-owned spec for requirements context
- `artifacts/04-plans/{module}/design.md` — master design for broader context
- `manifest.yaml` — project config: `layout`, `repos`, and the `iterations` block
- `progress.json` — current feature status, the module's `branch`, `repos`, `lastCommit`
- Target repo(s): the `repos:` keys in the module's `module.md`, resolved as in skill.md "Repos and Git" (`bun harnessclaude/hooks/sdd.ts repos`)

## Outputs

- Code and tests in target repo(s)
- `artifacts/05-implementation/{module}/task-{N}-{slug}.md` — per-task session logs (one file per task, supports parallel dev)
- Updated `progress.json` — feature status changes

## Before Starting

At the beginning of each session, present the pending features and ask:
- "Here are the pending features: [list]. Which should I work on next?"
- If the user says "just pick one" or "go in order", follow task order from tasks.md

## Rules

1. Always read `progress.json` via read-context skill before starting
2. **Ask the user which feature to work on** — don't silently pick one
3. Implement ONE feature at a time — do not batch
4. Follow TDD with the `tdd` skill: failing test → implement → `verify` → `commit`
5. Debug with the `debug` skill — root cause before any fix; stop after 3 failed fixes and ask the user
6. At the start of each session, set up `feat/{module}` in every repo the module touches (implement skill, Pre-Implementation): ask first if a repo has uncommitted changes outside the harness folder; create a missing branch from the repo's `default_branch`. A `planned` module moves to `implementing` with `sdd.ts status`, which records the branch and repos.
7. After each feature:
   - (a) Commit the code in each repo that changed (`commit` skill) — one commit for all repo keys in the monorepo layout
   - (b) `bun harnessclaude/hooks/sdd.ts commit {module} {repo}` per repo, then `sdd.ts feature {module} {slug} evaluating`
   - Append to the task's session log (`task-{N}-{slug}.md`, from `templates/session-log.md`) what was done, decisions made, and the commit per repo (`api@abc1234`)
   - (c) Commit the harness files (session log, `progress.json`, `events/events.jsonl`) listing the code commits: a second commit on the module branch (monorepo), or in the harness repo on its current branch (workspace)
8. Never modify files in the harnessclaude/ directory except session logs; statuses and commits are recorded only through `sdd.ts`. Never push; never open a pull request unless the user asks
9. If blocked, `sdd.ts feature {module} {slug} blocked` and surface it to the user
10. **If you need to deviate from the plan, ask first** — "The plan says X but I think Y would be better because Z. Should I deviate?" Don't deviate silently.
11. At session end, ask: "Should I continue with the next feature, or pause here?"
