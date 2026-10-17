---
name: harnessclaude-verify
description: Evidence gate — run the command that proves a claim before stating it
type: utility
phases: [implement, evaluate]
---

# Verify

Use before ANY claim that something works, passes, is fixed, or is done — and before every status change to `evaluating`, `passing`, or `completed` in `progress.json`.

## Iron Law

```
NO COMPLETION CLAIMS WITHOUT FRESH VERIFICATION EVIDENCE
```

If the proving command was not run in this session after the last code change, the claim cannot be made.

## Gate

1. **Identify** — which command proves the claim?
2. **Run** — the full command, fresh, not a subset.
3. **Read** — the whole output: exit code, failure count, warnings.
4. **Decide** — does the output confirm the claim?
   - No → report the actual state with the evidence.
   - Yes → state the claim *with* the evidence (command + key output line).

## What Counts as Evidence

| Claim | Required evidence | Not enough |
|-------|-------------------|------------|
| Tests pass | Test run output, 0 failures | Earlier run, "should pass" |
| Compiles / bundles | Compile or bundle command exits 0 | Lint passing |
| Bug fixed | Original reproduction now passes | Code changed |
| Regression test is real | Test fails with the fix reverted, passes with it | Test passes once |
| Spec met | Each acceptance criterion checked one by one | Tests passing |
| Subagent finished | Diff shows the changes, tests re-run by you | Subagent says "done" |

## Where to Record It

- Implement: append the command and result line to the session log `artifacts/05-implementation/{module}/task-{N}-{slug}.md`.
- Evaluate: put it in the Test Report section of `task-{N}-{slug}-review.md` and in `task-{N}-{slug}-test-report.md`.

## Red Flags

"should", "probably", "seems to", "looks good", "Done!" before any command output; trusting a previous run after editing code; a partial test run presented as the full suite.
