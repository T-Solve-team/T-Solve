# HarnessClaude — how we build T-Solve with AI agents

HarnessClaude is the **agentic SDLC framework** this repo uses with Claude Code. It turns "ask the AI to write
some code" into a fixed process: every piece of work goes from requirements → spec → design → tasks → code →
independent review, and **hooks enforce the process** so neither a person nor an agent can skip a step.

In one sentence: **no code without an approved spec, and no "done" without evidence.**

```
discover ──► decompose ──► per module:  decomposed ─► planned ─► implementing ─► evaluating ─► completed
 (context     (split into                 (module.md)  (spec,      (code, test-     (independent
  brief)       modules)                                 design,      first, one       review +
                                                        tasks)       task at a time)  test report)
```

## Why we use it

| Without a harness | With HarnessClaude |
|---|---|
| The AI guesses missing requirements | Gaps become `[NEEDS CLARIFICATION: …]` and the AI asks |
| Code drifts away from the SRS | Every acceptance criterion has an id (`AC-001.1`); tests must name it (`sdd.ts trace`) |
| "Done" means "it compiled" | A separate reviewer agent checks the code against the spec, with fresh context |
| Anyone can edit code anytime | A hook blocks edits until the module's spec is approved by a human |
| Progress lives in someone's head | `progress.json` + `events/` record every status change |

## Setup (once per machine)

1. Install [Bun](https://bun.sh) — the hooks and the `sdd.ts` tool run on it:
   ```bash
   powershell -c "irm bun.sh/install.ps1 | iex"
   ```
2. Install [Claude Code](https://docs.anthropic.com/claude-code) and open it in the repo root.
3. Check the workspace:
   ```bash
   bun harnessclaude/hooks/sdd.ts audit
   ```
   It must print `audit: OK`.

The hooks are registered in `.claude/settings.json` (committed), so they turn on automatically when Claude Code
opens this repo. Each session starts with a short status line, e.g. `[harnessclaude] project: T-Solve · mode: blueprint`.

## The phases

| Phase | What happens | Output (in `artifacts/`) |
|---|---|---|
| **discover** | Read the baseline docs (SRS, reports) and write a context brief | `02-discovery/` |
| **decompose** | Split the system into bounded modules with interfaces and dependencies | `03-modules/{module}/module.md`, module map |
| **plan** | Per module: spec (user stories + EARS acceptance criteria), design, task list | `03-modules/{module}/spec.md`, `04-plans/{module}/` |
| **approve** | A human reads the spec and approves it — this unlocks the code | `approvals.json` (written only by `sdd.ts approve`) |
| **implement** | One task at a time, test-first (TDD), one commit per feature | code + `05-implementation/` session logs |
| **evaluate** | An independent reviewer agent checks spec compliance, quality, tests | `06-evaluation/` review + test report |

**Phases cannot be skipped.** A module never jumps from `decomposed` straight to implementing.

### Modes

`manifest.yaml` sets the mode, which changes how discover works:

- `blueprint` — **ours.** We already have docs (SRS, design); put them in `artifacts/00-baseline/`.
- `explore` — only an idea; discover runs a research/ideation conversation.
- `inherit` — an existing codebase; discover maps it first.

### Iterations

Iterations are on (`iterations.enabled: true`, 2 weeks, demo on Friday). The current one is
`progress.json → currentIteration`, its plan is `artifacts/07-iterations/iteration-{N}.md`, and each module's
tasks for that iteration live in `04-plans/{module}/iteration-{N}/`.

## The code gate

`hooks/brainstorm-gate.ts` runs before every Edit/Write/shell command. Code **outside** `harnessclaude/` can be
written only when:

- the module is `planned`, `implementing`, or `evaluating`, **and**
- a human approved its current spec (`bun harnessclaude/hooks/sdd.ts approve <module>`).

Editing an approved spec locks the code again until it is re-approved. If the gate blocks you, read the
reason it prints — it says what to do. Do not work around it.

## Size the work

Not every change needs the full flow. Decide the size first:

| Size | When | Flow |
|---|---|---|
| **Quick** | No acceptance criterion changes: typo, copy, styling, config value, a bug against an existing criterion | `sdd.ts quick "<what>" <file>…` (≤ 5 files, 24 h) → fix → commit → `sdd.ts quick done` |
| **Change** | Adds or edits criteria of an existing module | plan skill, Change Mode: spec first, re-approve, then code |
| **Module** | A new module or feature area | plan → approve → implement → evaluate |

## Talking to it

You don't need to remember skill names — say what you want in Claude Code:

| You say | It runs |
|---|---|
| "status" / "where are we?" | read-context |
| "start" / "let's build X" | discover |
| "break it into modules" | decompose |
| "let's work on `search`" | the skill that matches the module's status |
| "fix this typo in …" | a quick change |
| "this test fails" | debug (root cause first) |
| "security check" | security-review |
| "audit" | audit-workspace |
| "onboard me" | onboard (checks your setup, explains the project) |

## `sdd.ts` cheat sheet

Run from the repo root.

| Command | Use it to |
|---|---|
| `bun harnessclaude/hooks/sdd.ts audit` | Check the whole workspace (structure, statuses, approvals, naming) |
| `… check <module>` | Validate a spec after editing it |
| `… approve <module>` | Approve a spec (you confirm in the permission prompt) — unlocks code |
| `… diff <module>` | See which criteria changed since the last approval |
| `… trace <module> [STORY-…]` | Check every criterion has a test naming its id |
| `… status <module> <status>` | Move a module (enforces the state machine) |
| `… feature <module> <feature> <status>` | Move a feature: `pending → in-progress → evaluating → passing` (`fixing` after a failed review) |
| `… quick "<what>" <file>…` / `… quick done` | Open / close a quick change |
| `… repos` | List repos with branch and uncommitted files |

Never edit statuses in `progress.json`, `approvals.json`, or `quick.json` by hand — always go through `sdd.ts`.

## Folder layout

```
harnessclaude/
├── skill.md            operating manual the agent reads every session
├── manifest.yaml       project, mode, layout, repos, iterations
├── progress.json       modules, features, statuses, current iteration
├── approvals.json      approved spec versions (created by sdd.ts approve)
├── artifacts/
│   ├── 00-baseline/        input docs (SRS, design) — blueprint mode reads these
│   ├── 01-research/        research spikes
│   ├── 02-discovery/       context brief
│   ├── 03-modules/         module.md + spec.md per module (the contract)
│   ├── 04-plans/           design.md + tasks.md per module (and per iteration)
│   ├── 05-implementation/  session logs  task-{N}-{slug}.md
│   ├── 06-evaluation/      task-{N}-{slug}-review.md / -test-report.md
│   ├── 07-iterations/      iteration-{N}.md
│   └── _registry/          artifact-index.md — every artifact listed here
├── skills/             one folder per skill (discover, plan, implement, evaluate, tdd, …)
├── agents/             role prompts: architect, planner, developer, reviewer, researcher, …
├── templates/          templates for spec, design, tasks, review, iteration, …
├── hooks/              session-context, brainstorm-gate (code gate), privacy-block, sdd.ts
├── events/events.jsonl log of every status change
└── FEEDBACK.md         framework friction notes
```

`.claude/skills/harnessclaude-*` and `.claude/agents/harnessclaude-*` expose the same skills and agents to Claude Code.

## Git

This is a monorepo layout: code and `harnessclaude/` share one repository.

- A module works on the branch `feat/<module>`; `sdd.ts status <module> implementing` records it.
- Per feature: commit the code first, then the harness files (artifacts, `progress.json`) as a second commit on
  the same branch.
- The agent never pushes, merges, or opens a PR unless you ask. Our normal rules still apply — see
  [Branching and conventions](../README.md#branching-and-conventions) (Jira key in commits, PR + CODEOWNER review).

## Rules of thumb

- **Spec is the contract.** A changed requirement goes into the spec first, then into code.
- **Never guess.** Missing info → `[NEEDS CLARIFICATION: …]` and ask.
- **Evidence before "done".** Claims like "tests pass" need fresh command output.
- **Don't fight the hooks.** If something is blocked, the message tells you the legitimate way through.
- Hit friction with the framework? Add a note to `FEEDBACK.md`.
