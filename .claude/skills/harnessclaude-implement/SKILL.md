---
name: harnessclaude-implement
description: Implement a module's planned features one at a time
type: sequential
agent: developer
delegates:
  - tdd
  - verify
  - commit
  - debug
---

# Implement

Execute the implementation plan for a module, one feature at a time.

## Pre-Implementation (every session)

1. Run read-context skill to orient
2. Read tasks from the **active plan path** (see skill.md Plan Paths convention)
3. Read `progress.json` to find current feature status
4. Read `artifacts/05-implementation/{module}/task-{N}-{slug}.md` if it exists for the target feature (previous session context)
5. If iterations enabled, also read the **master plan path** for broader module context if needed
6. If the module is still `planned`: `bun harnessclaude/hooks/sdd.ts status {module} implementing` — it records the module's branch `feat/{module}` and its repos (skill.md "Repos and Git")
7. Set up the branch in every repo the module touches (`bun harnessclaude/hooks/sdd.ts repos` shows them):
   - A repo with uncommitted changes outside the harness folder → stop and ask the user what to do
   - `feat/{module}` missing → create it from the repo's `default_branch` (else the current branch); then switch to it
   - Workspace layout: the harness folder's own repo stays on its current branch
8. Pick the highest-priority feature in "pending" status

All status changes below go through `sdd.ts` (update-tracking skill) — never edit progress.json statuses by hand.

## Implementation Loop

For each feature:

1. `bun harnessclaude/hooks/sdd.ts feature {module} {slug} in-progress`
2. Implement the task with the `tdd` skill — one acceptance criterion at a time, test first.
   - Context to load: the full task from tasks.md, its STORY-IDs in spec.md, the relevant parts of design.md (including the Edge Cases & Risks table).
   - If subagents are available (Claude Code) and the task is large, dispatch one `harnessclaude-developer` subagent per task with only that context — not the conversation history. Review its diff and re-run the tests yourself before accepting it (`verify`).
3. When tests fail and the cause is not obvious → `debug` skill. Do not guess-and-patch.
4. When the spec does not say what should happen, or the user asks for different behavior → stop coding. Ask the user; the answer goes into the spec through the plan skill's Change Mode (Changelog row, `check`, user approval). Editing the spec locks this module's code until the user approves the new version — that is intended.
5. After implementation:
   - Run `bun harnessclaude/hooks/sdd.ts trace {module} {task's STORY-IDs}` — it must exit 0 (every criterion has a test); paste the output into the session log
   - Apply `verify`: full test run, typecheck/lint; record the command and result in the session log
   - (a) Commit the code in each repo the feature changed, with the `commit` skill (scoped staging, secret scan, STORY-ID in the message). In the monorepo layout that is one code commit for all repo keys
   - (b) Record each commit: `bun harnessclaude/hooks/sdd.ts commit {module} {repo}` per repo key
   - Append to the task's session log (`task-{N}-{slug}.md`): what was done, decisions made, the commit per repo (`api@abc1234, web@def5678`), any deviations from plan
   - `bun harnessclaude/hooks/sdd.ts feature {module} {slug} evaluating` (it logs the event)
   - (c) Commit the harness files (session log, `progress.json`, `events/events.jsonl`) listing the code commits in the message — `docs(checkout): task 2 session log — api@abc1234, web@def5678`. Monorepo: a second commit on the module branch, after the code commit. Workspace: in the harness repo, on its current branch
   - Never push

## Fixing Loop (feature status "fixing")

1. Read `artifacts/06-evaluation/{module}/task-{N}-{slug}-review.md` — the latest attempt.
2. Follow "Receiving This Review" in the `code-review` skill: clarify unclear items first, push back with evidence on findings that are wrong for this codebase.
3. Fix CRITICAL findings first. Use `debug` for any failure whose root cause is not proven.
4. `verify` → commit the code and `sdd.ts commit {module} {repo}` per repo → `sdd.ts feature {module} {slug} evaluating` → commit the harness files. Note the attempt number in the session log.

## Session Log

Each task gets its own session log file under `artifacts/05-implementation/{module}/`:

- **Convention:** `task-{number}-{slug}.md` where slug matches the feature key in `progress.json` (the task's `**Feature:**`)
- **Examples:** `task-1-scaffold.md`, `task-2-sync-skills-api.md`, `task-5-analytics.md`
- This allows parallel development — each developer owns their task file with no merge conflicts

Create it from `templates/session-log.md` (title, Module, Assignee, Status — plus **Iteration:** with `currentIteration` from progress.json if iterations are enabled), then append one entry per session:

```markdown
## Session {date} {time}

### Working on: {feature-name}
- Key decisions made and why
- Files created/modified
- Commit per repo (for example api@abc1234)
- Any deviations from the plan

### Next up
{next feature or "all features complete"}
```

## Rules

- One feature at a time — finish and commit before starting the next
- Always write tests before implementation (`tdd` skill)
- No "done" or "passing" claims without fresh evidence (`verify` skill)
- If blocked, `sdd.ts feature {module} {slug} blocked` and surface it to the user — don't guess
- If you need to deviate from the plan, document why in the session log
- Never modify harnessclaude/ files except session logs; statuses change through `sdd.ts`, spec changes through the plan skill's Change Mode
- If the gate blocks a write, read its reason and do what it says — never work around it
