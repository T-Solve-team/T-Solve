# Root Cause Tracing

Bugs often surface deep in a call chain. Fixing where the error appears treats the symptom; trace backward to the original trigger and fix there.

## Process

1. **Symptom** — what exactly failed? (`git init failed in packages/core`)
2. **Immediate cause** — which line produced it? (`execFile('git', ['init'], { cwd: projectDir })`)
3. **Who called it, with what?** — walk up the stack one level at a time and note the value at each step.
4. **Find the origin** — keep going until you reach where the bad value was *created* (`projectDir = ''` because a test read `tempDir` before setup ran; empty `cwd` fell back to the source directory).
5. **Fix at the origin**, then add guards along the path (below).

## When You Cannot Trace by Reading

Add temporary instrumentation right before the failing operation:

```ts
console.error("DEBUG gitInit", { directory, cwd: process.cwd(), stack: new Error().stack });
```

Use `console.error` in tests (loggers may be silenced), run, filter output for the tag, and read the stack for the test file and line that triggered it. Remove the instrumentation after.

**Test pollution** (a file or state appears but you do not know which test causes it): run test files one at a time and check for the artifact after each; the first file that leaves it is the polluter.

## Defense in Depth — after the root fix

One check can be bypassed by another code path or a refactor. Add a guard at each layer the bad data passed through:

| Layer | Purpose | Example |
|-------|---------|---------|
| Entry point | Reject invalid input at the boundary | throw if `workingDirectory` is empty or not a directory |
| Business logic | Assert the operation makes sense | throw if `projectDir` missing when creating a workspace |
| Environment guard | Refuse dangerous actions in the wrong context | in tests, refuse `git init` outside the temp dir |
| Instrumentation | Leave evidence for next time | log context before the risky operation |

Each guard gets a test. Record the guards in the debug entry of the session log.
