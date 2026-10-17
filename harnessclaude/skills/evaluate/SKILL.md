---
name: evaluate
description: Independently verify implementations against specs
type: sequential
agent: reviewer
delegates:
  - code-review
  - run-tests
  - scout
  - security-review
  - verify
---

# Evaluate

Independently verify that a feature implementation meets its spec.

## Process

1. Read the spec: `artifacts/03-modules/{module}/spec.md` (always — see skill.md Plan Paths). For the Edge Cases & Risks table, read `design.md` from the **active plan path**; if iterations are enabled, use the **master plan path** for full module context.
2. Read the code in target repo(s) for the feature being evaluated — the commits per repo (`api@abc1234`) are in the session log `artifacts/05-implementation/{module}/task-{N}-{slug}.md` and in progress.json `lastCommit`; the repo folders come from `bun harnessclaude/hooks/sdd.ts repos`
3. Read progress.json to confirm feature is in "evaluating" status
4. Check previous review attempts if they exist (task-{N}-{slug}-review.md)

**Independence:** if subagents are available (Claude Code), dispatch the `harnessclaude-reviewer` subagent with only the spec path, the design path, and the commit range per repo (`api: base..head`) — not the implementation conversation. Otherwise review inline and judge only from artifacts and code, never from the developer's reasoning.

## Evaluation Pipeline

1. `run-tests` — typecheck, lint, full suite, coverage → `task-{N}-{slug}-test-report.md`
   - Traceability: `bun harnessclaude/hooks/sdd.ts trace {module} {task's STORY-IDs}` → the review's Traceability section. A criterion without a test is a MISSING finding.
2. `scout` (impact mode) — for multi-file features or shared-code changes
3. `code-review` — Stage 1 spec compliance, then Stage 2 checklist
4. `security-review` — only if the module handles auth, payments, personal data, uploads, or user-supplied URLs/HTML
5. `verify` — before writing the verdict

The pipeline fills the four criteria below. Run through each criterion:

1. **Functionality**: Does the code do what the spec says?
   - Test each user story / acceptance criterion
   - Check edge cases mentioned in the spec
2. **Completeness**: Are all spec requirements addressed?
   - Cross-reference spec checklist against implementation
3. **Correctness**: Do tests pass?
   - Run the test suite
   - Check test coverage for the feature
4. **Integration**: Does it work with module interfaces?
   - Verify exposed interfaces match the spec's interface contracts

## Output

Each task gets its own evaluation files using a per-task naming convention:

- **Review:** `artifacts/06-evaluation/{module}/task-{N}-{slug}-review.md`
- **Test report:** `artifacts/06-evaluation/{module}/task-{N}-{slug}-test-report.md`

### Naming Convention

- Format: `task-{number}-{slug}-review.md` and `task-{number}-{slug}-test-report.md`
- The slug matches the feature key in `progress.json`
- Examples: `task-1-scaffold-review.md`, `task-2-sync-skills-api-review.md`
- This allows parallel evaluation with no conflicts

### Review Content

Use the review template `templates/review.md` exactly. Its YAML frontmatter carries the machine-read fields — `module`, `feature` (the feature key), `task`, `verdict: pass | fail`, `attempt`, `date`, `reviewer`, and `iteration` (with `currentIteration`) when iterations are enabled; `sdd.ts audit` reads `verdict`. The body has Summary, Criteria (Functionality / Completeness / Correctness / Integration), Traceability (the `sdd.ts trace` output), Issues (file:line, description, suggested fix), and Test Report.

Each new attempt is a new file version: keep `attempt` counting 1, 2, 3.

### Test Report

The `task-{N}-{slug}-test-report.md` captures test execution output and coverage data (run-tests skill).

## After Review

Status changes go through `sdd.ts` (update-tracking skill):

- **Pass**: `bun harnessclaude/hooks/sdd.ts feature {module} {slug} passing`
- **Fail (attempt < 3)**: `sdd.ts feature {module} {slug} fixing` — the developer reads the review and fixes
- **Fail (attempt 3)**: `sdd.ts feature {module} {slug} blocked`, and tell the user — a third failure needs a human decision
- **All features of the module passing**: suggest completing the module (`sdd.ts status {module} evaluating`, then `completed`) once the user agrees, then finish its branches with the user (commit skill, Finishing a Module) — the main session does this, not the reviewer subagent

## Rules

- Check against the SPEC, not the design or tasks — you are independent
- Run all tests before writing the review
- Be specific in failure reports — file paths, line numbers, expected vs actual
- Do not rubber-stamp — your independence is the framework's quality guarantee
- Never edit code or tests during evaluation — findings go in the review, the developer fixes
- Any CRITICAL code-review finding, Critical/High security finding, failing test, or acceptance criterion without a test → verdict fail
- The spec turns out wrong or incomplete → say so in the review and verdict fail; the fix is a spec change (plan skill, Change Mode), not a code workaround
