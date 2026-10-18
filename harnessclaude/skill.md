---
name: harnessclaude
description: Agentic SDLC framework — orchestrates AI agents through structured phases
---

# HarnessClaude

You are operating within the HarnessClaude agentic SDLC framework.

All paths in this file and in skills/, agents/, templates/ are relative to the `harnessclaude/` workspace directory unless they name a target repo.

## On Session Start

1. Read `manifest.yaml` to understand the project
2. Read `progress.json` to understand current state
3. Read `artifacts/_registry/artifact-index.md` for artifact inventory
4. **Scan `artifacts/00-baseline/`** for documents (PDFs, docs, markdown, etc.)
   - If docs found and mode is `undecided` → auto-recommend `blueprint` mode
   - If docs found and discovery hasn't started → suggest starting discover phase
   - If new docs added since last session → flag them and ask about re-discovery
5. **If mode is "undecided" and no baseline docs found:** Help the user choose a mode. Ask them:
   - What do you want to build? (get a brief description)
   - Do you already have documentation or specs for it?
   - Do you have an existing codebase to build on?
   Based on their answers, recommend a mode and update `manifest.yaml`:
   - Has docs/specs → tell them to drop docs in `artifacts/00-baseline/` then `blueprint`
   - Starting from scratch, just an idea → `explore`
   - Has existing code → `inherit`
6. Suggest next action based on current state

## Available Actions

| Intent | Skill | Agent |
|--------|-------|-------|
| Design a feature | discover/ | Researcher |
| Start a new project | discover/ | Researcher |
| Research a topic | research/ | Researcher |
| Break into modules | decompose/ | Architect |
| Extract stories from docs | extract-stories/ | Researcher |
| Plan a module | plan/ | Planner |
| Implement a module | implement/ | Developer |
| Evaluate a module | evaluate/ | Reviewer |
| Check project status | read-context/ | — |
| Search artifacts | search-artifact/ | — |
| Draw a UI wireframe | generate-mockup/ | — |
| Debug a failure | debug/ | Developer |
| Security review | security-review/ | Reviewer |
| Onboard to the project / check the setup | onboard/ | — |
| Map an existing codebase | scout/ | Researcher |
| Audit the workspace | audit-workspace/ | — |
| E2E tests from an Excel test report | — (agent file `agents/qa-engineer.md`) | QA Engineer |

## Supporting Skills

Phase skills call these; read them when the phase skill points to them.

| Skill | Used in | Purpose |
|-------|---------|---------|
| scout/ | discover (inherit), evaluate | Codebase map; impact of a change |
| edge-cases/ | plan, evaluate | Edge cases across 12 dimensions into design.md |
| risk-review/ | plan (high-risk modules) | Five-persona GO / CAUTION / STOP on a design |
| tdd/ | implement | Failing test first, minimal code, refactor |
| debug/ | implement, evaluate (fixing) | Root cause before fix; stop after 3 failed fixes |
| commit/ | implement | Scoped staging, secret scan, conventional message |
| run-tests/ | evaluate | Typecheck, lint, full suite, coverage, test report |
| code-review/ | evaluate | Spec compliance, then critical/informational checklist |
| security-review/ | evaluate (sensitive modules) | Secrets, dependency audit, STRIDE + OWASP |
| verify/ | implement, evaluate | No pass/done claim without fresh command output |
| update-tracking/ | every phase | Status changes through `sdd.ts status` / `sdd.ts feature` |

## Spec Tools

The spec (`artifacts/03-modules/{module}/spec.md`) is the contract: every acceptance criterion has an id (`AC-001.1`) and is written in EARS form. These checks ship with the workspace — run them from the project root; they print what is wrong and exit 1 on failure.

| Command | When |
|---------|------|
| `bun harnessclaude/hooks/sdd.ts check {module}` | After writing or editing a spec, and before asking for approval |
| `bun harnessclaude/hooks/sdd.ts approve {module}` | When the user agrees the spec is right — they confirm in the permission prompt. Unlocks the module's code; any later spec edit locks it again |
| `bun harnessclaude/hooks/sdd.ts trace {module} [STORY-…]` | Before a feature moves to `evaluating`, and in every evaluation — each criterion must have a test that names its id. With several modules, give each `paths` (code and tests) in module.md: ids restart per module |
| `bun harnessclaude/hooks/sdd.ts diff {module}` | Before asking for re-approval — the criteria added, reworded, and removed since the last approval |
| `bun harnessclaude/hooks/sdd.ts status {module} {status}` · `feature {module} {feature} {status}` | Every status change — never edit statuses in progress.json by hand (update-tracking/ skill) |
| `bun harnessclaude/hooks/sdd.ts audit` | Session start (read-context) and before phase transitions — the whole workspace, by code (audit-workspace/ skill) |
| `bun harnessclaude/hooks/sdd.ts quick "<what>" <file>…` | A Quick-size change (Size the Work) — opens those exact files (≤ 5, 24 h); the user confirms in the permission prompt |
| `bun harnessclaude/hooks/sdd.ts quick done` | After the quick change is committed — closes it |

Approvals live in `approvals.json` and the open quick change in `quick.json`; never edit either file yourself.

## Size the Work

When a request changes code, decide its size first and say it in one plain line; the user can override it.

| Size | When | Description lives in | Then |
|------|------|----------------------|------|
| Quick | No acceptance criterion changes: a typo, copy, styling, a config value, a bug that breaks an existing criterion | the request | the quick change below |
| Change | Adds or edits criteria of an existing module | the spec (Light depth) | plan/ skill, Change Mode |
| Module | A new module, or a Normal/Deep spec | spec, design, tasks | plan/ → implement/ → evaluate/ |

A quick change, in order:
1. `bun harnessclaude/hooks/sdd.ts quick "<what>" <file>…` — every file you will touch, tests included: exact paths with forward slashes, at most 5, as a command of its own. The user confirms it in the permission prompt; until then the files stay locked.
2. A bug against an existing criterion: write the failing test first, named with its id (tdd/ skill). Anything else: the existing tests are the check.
3. verify/ → commit/ (on the current branch) → `bun harnessclaude/hooks/sdd.ts quick done`.

If it grows — it needs a file you did not list, or it would change what a criterion says — stop, say so, and step up a size: a new quick change (the user confirms again) or Change Mode.

## How to dispatch

- User says "let's build X" or describes a new feature → discover/ skill (ideate or the explore design conversation)
- User says "what's next?" → read `progress.json` → suggest action
- User says "extract stories" or "break down the PRD" or "create stories from docs" → extract-stories/ skill
- User says "let's work on {module}" → check module phase → dispatch appropriate skill
- User says "I need to research X" → research/ skill
- User says "status" or "where are we?" → read-context/ skill
- User is new to the project, says "onboard", or asks "is my workspace set up?" / "what is this project?" → onboard/ skill
- User says "start" or "init" → discover/ skill
- User asks about an artifact → search-artifact/ skill
- User asks for a small fix or tweak → Size the Work (a quick change when no acceptance criterion changes)
- User asks to change how an existing module behaves → plan/ skill, Change Mode (spec first, then code)
- User reports a bug or failing tests → debug/ skill (root cause first)
- User asks for a security check → security-review/ skill
- User says "audit" or "check the workspace" → audit-workspace/ skill
- User wants E2E tests from an Excel test report ("convert test cases", "excel-tc-to-e2e") → follow `agents/qa-engineer.md`; it needs the `excel-tc-to-e2e` skill installed in `.claude/skills/` or `.cursor/skills/` — if it is missing, tell the user

## Artifact Ownership

| Artifact | Owner | Location |
|----------|-------|----------|
| Spec (requirements, stories) | BA | `artifacts/03-modules/{module}/spec.md` |
| Module definition | Discover phase | `artifacts/03-modules/{module}/module.md` |
| Design (architecture) | Dev | `artifacts/04-plans/{module}/design.md` |
| Tasks (implementation) | Dev | `artifacts/04-plans/{module}/[iteration-{N}/]tasks.md` |

## Plan Paths

All skills that read or write plan artifacts MUST resolve paths using this convention:

**Spec path (BA-owned, always the same):**
- `artifacts/03-modules/{module}/spec.md` — requirements, user stories (STORY-IDs), acceptance criteria
- BA updates this file and adds a row to the Changelog table
- Devs read this but never write to it

**If `manifest.yaml` has `iterations.enabled: true`:**
- Read `currentIteration` from `progress.json`
- **Active plan path:** `artifacts/04-plans/{module}/iteration-{N}/` — design + tasks for current iteration
- **Master plan path:** `artifacts/04-plans/{module}/` — full module design
- The plan skill writes to BOTH master and active paths (it is the only skill that does this)

**If no `iterations` block in manifest.yaml:**
- **Active plan path:** `artifacts/04-plans/{module}/` — contains design.md, tasks.md
- **Master plan path:** (same as active)

When a skill says "read the spec", it means from `artifacts/03-modules/{module}/spec.md`.
When a skill says "read tasks" or "read design", it means from the **active plan path**.
When a skill says "read the master plan" or needs full module context, it means from the **master plan path**.

## Status Flow

Modules: `decomposed → planned → implementing → evaluating → completed` (implementing and evaluating can go to `blocked`); a module enters progress.json in the decompose phase. Features: `pending → in-progress → evaluating → passing`, with `fixing` after a failed review. Full transition table: update-tracking/ skill. Phases cannot be skipped — a module's code can be written only while the module is `planned`, `implementing`, or `evaluating` **and** the user has approved its current spec, or while the file is listed in the user's open quick change. A `completed` module is reopened by a change request (plan/ skill, Change Mode). A hook enforces this.

## Subagents (Claude Code)

When the Agent/Task tool is available, use these instead of role-playing in the main conversation:

| Subagent | Use for |
|----------|---------|
| harnessclaude-reviewer | evaluate — independent review with only spec, design, and commit range as context |
| harnessclaude-developer | implement — one task per subagent with only that task's context |
| harnessclaude-researcher | research, scout (codebase map of large repos) |

Always verify a subagent's result yourself (diff + fresh test run) before updating progress.json. Without subagents (Cursor, Codex), follow the same skills inline.

## Repos and Git

`manifest.yaml` has `layout` (`monorepo` or `workspace`) and a `repos` map. Each repo has a `path` relative to this folder (for example `../api`) and can have `remote`, `default_branch`, `role` (`frontend`, `backend`, `e2e`, `other`), `stack`, and `test_cmd` — the onboard/ skill fills them in. `bun harnessclaude/hooks/sdd.ts repos` lists every repo with its folder, branch, and uncommitted files.

- **Repo folder:** `harnessclaude/<path>`. Run git as `git -C <repo folder> …`.
- **Workspace layout:** each repo is its own git repository, and this folder is one more. Code is committed in the code repos; artifacts, session logs, `progress.json`, and `events/` in this folder's repo, on its current branch — never create a module branch here.
- **Monorepo layout:** the repos and this folder share one git repository and one module branch. Commit the code first — one commit for all repo keys, staging only paths outside this folder (`git add -- . ':(exclude)harnessclaude'`) — then the harness files as a second commit on the same branch. Record that one SHA under each repo key the feature changed.
- **Module repos:** the `repos:` keys in the module's `module.md`. `sdd.ts status <module> implementing` copies them to progress.json (`default` when there are none).
- **`default`:** the repo that holds this folder (monorepo). In a workspace this folder holds no code: ask the user which repo the module changes, and add it to `module.md` (and to `manifest.yaml` `repos` if it is missing).
- **Branch:** a module uses `feat/<module>` in every repo it touches; `sdd.ts status <module> implementing` records it. Create a missing branch from the repo's `default_branch` (else the current branch). If a repo has uncommitted changes outside this folder, stop and ask the user first.
- **Commits:** after each feature's code commit, `bun harnessclaude/hooks/sdd.ts commit <module> <repo>` records that repo's HEAD in `lastCommit`. `sdd.ts audit` warns when a recorded commit is no longer on the module branch (drift) or the branch is gone.
- **Finish:** a `completed` module's branches are merged, pushed for a pull request, or kept — as the user chooses (commit/ skill, Finishing a Module).
- Never push, merge, or open pull requests otherwise, unless the user asks. Never clone — `harnessclaude init` clones missing repos.

## Rules

- The session hook shows each module's status; read progress.json when you need feature status, branches, or recorded commits
- Write outside harnessclaude/ only in the module's repos (Repos and Git): code only in implement (fixing included) for modules whose approved spec allows it, the files of an open quick change (Size the Work), E2E tests by qa-engineer under `$E2E` for such a module, or when the user explicitly asks
- Never fill a spec gap with a guess: write `[NEEDS CLARIFICATION: <question>]` in the spec and ask the user
- Fix the spec, not the code: a changed requirement goes into the spec first (Changelog row, `check`, user approval), then into code
- Change module and feature statuses only with `sdd.ts status` / `sdd.ts feature` (update-tracking/ skill) — it enforces the state machine and logs the event
- Append to FEEDBACK.md when you encounter framework friction
- Validate artifacts before phase transitions (validate-artifact/ skill)
