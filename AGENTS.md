<!-- harnessclaude:start -->
## HarnessClaude

This project uses the HarnessClaude agentic SDLC framework. Its workspace is `harnessclaude/`.

**Every session:** read `harnessclaude/skill.md` (the operating manual), then `harnessclaude/manifest.yaml` and `harnessclaude/progress.json`, before taking any action. A hook injects the current state at session start.

**Phases cannot be skipped.** The project goes discover → decompose; each module then moves `decomposed → planned → implementing → evaluating → completed`. Dispatch by the module's status:
- No module yet, or a new feature ("let's build X") → discover skill first, then decompose
- `decomposed` → plan · `planned` or later → implement / evaluate
- Never jump from `decomposed` straight to implementing
- Change statuses only with `bun harnessclaude/hooks/sdd.ts status|feature …` — it enforces the allowed transitions

**Spec is the contract:** every acceptance criterion in `spec.md` has an id (`AC-001.1`) and is written in EARS form. Never fill a gap with a guess — write `[NEEDS CLARIFICATION: <question>]` and ask the user. A changed requirement goes into the spec first, then into code.

**Code gate:** a module's code can be written only while the module is `planned`, `implementing`, or `evaluating` and the user has approved its current spec (`bun harnessclaude/hooks/sdd.ts approve <module>` — they confirm in the permission prompt), or while the file is in the user's open quick change. Editing an approved spec locks the code again; a `completed` module is reopened by a change request. A hook enforces this. If blocked, read the reason and do what it says — do not work around the gate.

**Size the work first:** a fix that changes no acceptance criterion (a typo, copy, a config value, a bug against an existing criterion) is a quick change — `bun harnessclaude/hooks/sdd.ts quick "<what>" <file> …` opens those exact files once the user confirms; see "Size the Work" in `harnessclaude/skill.md`.

**Repos and git:** write outside `harnessclaude/` only in the module's repos, on the branch `feat/<module>` — see "Repos and Git" in `harnessclaude/skill.md`. Never push, merge, or open a pull request unless the user asks or chooses it when a module is finished (commit skill, Finishing a Module). Never clone (`harnessclaude init` clones missing repos).

**Ask before writing:** resolve open decisions with the user one question at a time (2–3 options with a recommendation) before writing specs, designs, or code. Never write first and ask for changes after.

Specs, designs, and plans live in `harnessclaude/artifacts/`, not in `docs/` or other plan folders.
<!-- harnessclaude:end -->
