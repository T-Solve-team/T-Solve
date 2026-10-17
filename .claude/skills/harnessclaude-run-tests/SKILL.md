---
name: harnessclaude-run-tests
description: Run the project's checks and tests for a feature, analyze failures, write the test report
type: utility
phases: [evaluate]
agent: reviewer
delegates:
  - verify
---

# Run Tests

Execute the target repo's own checks and test suite independently of the developer, and produce `artifacts/06-evaluation/{module}/task-{N}-{slug}-test-report.md`.

## Core Principle

Never ignore a failing test. A failure is a finding, not noise — report it with its cause. Do not edit tests or code during evaluation; the developer fixes.

## Process

1. **Find the commands** — start from the repo's `test_cmd` in `manifest.yaml` when the onboard skill recorded one; otherwise read the target repo's `package.json` scripts, `Makefile`, `pyproject.toml`, `go.mod`, `Cargo.toml`, or CI config. Prefer the project's own scripts over guessed commands. Record which you used.
2. **Pre-flight** — typecheck and lint first (e.g. `tsc --noEmit`, `go vet ./...`, `cargo check`, `ruff`/`flake8`). Compile errors make test results meaningless.
3. **Run the full suite**, not only the new tests — regressions elsewhere count.
4. **Run coverage** if the project has a coverage command. Thresholds come from the spec's non-functional requirements; if the spec is silent, report the number and ask the user whether it is acceptable instead of inventing a threshold.
5. **Analyze**:
   - Failing tests — read the full error and stack; name the likely cause.
   - Flaky tests — re-run once; pass/fail flips point to shared state, ordering, or timing.
   - Skipped tests — are skips intentional, or hiding failures?
   - Slow tests (>5s each) — note them.
6. **Map tests to the spec** — run `bun harnessclaude/hooks/sdd.ts trace {module} {task's STORY-IDs}` and paste its output; every ✗ is an UNCOVERED criterion.
7. Apply `verify` before writing any pass/fail statement.

## Report Template

```markdown
# Test Report: {module} — Task {N}: {feature}

**Date:** {date}
**Commands:** `{typecheck}` · `{lint}` · `{test}` · `{coverage}`

## Results
- Total: X · Passed: X · Failed: X · Skipped: X · Duration: Xs
- Typecheck: pass/fail · Lint: pass/fail

## Coverage
| Metric | Value | Target (from spec) |
|--------|-------|--------------------|

## Acceptance Criteria → Tests
{output of `sdd.ts trace`}

## Failures
### `{file}` — {test name}
- Error: {message}
- Cause: {analysis}
- Suggested fix: {fix}

## Notes
{flaky, skipped, slow tests; unresolved questions}
```

Keep it under ~150 lines. Include every failure verbatim enough to act on.
