import { expect, test } from "bun:test";
import { cp, mkdtemp, rm } from "node:fs/promises";
import { join, resolve, sep } from "node:path";
import { tmpdir } from "node:os";
import { parseSpec, trace } from "./spec";

const text = "### STORY-002: Roles\n- AC-002.1: THE backend SHALL define roles.\n- AC-002.2: THE backend SHALL combine roles.\n";

test("normalizes consecutive Java ids and deduplicates dotted ids_BUG_093", () => {
  const result = trace(parseSpec(text), [{ path: "RolesTest.java", text: "check_AC_002_1_AC_002_2() AC-002.1" }]);
  expect(result.uncovered).toEqual([]);
  expect(result.covered["AC-002.1"]).toEqual(["RolesTest.java"]);
});

test("retains ownership and unknown-id checks_BUG_093", () => {
  const result = trace(parseSpec(text), [{ path: "OtherIT.java", text: "check_AC_002_1_AC_999_8()",
    others: [{ name: "other", acIds: new Set(["AC-002.1"]) }] }]);
  expect(result.uncovered).toEqual(["AC-002.1", "AC-002.2"]);
  expect(result.ambiguous["AC-002.1"]).toEqual(["OtherIT.java (also other)"]);
  expect(result.unknown).toEqual(["AC-999.8"]);
});

test("ignores malformed ids and respects story selection_BUG_093", () => {
  const spec = parseSpec(text + "### STORY-003: Session\n- AC-003.1: THE backend SHALL return the user.\n");
  const result = trace(spec, [{ path: "roles.test.ts", text: "prefixAC_002_1 AC_002_1suffix AC_02_1 AC_003_1 AC-002.2" }], ["STORY-002"]);
  expect(result.uncovered).toEqual(["AC-002.1"]);
  expect(result.covered["AC-003.1"]).toBeUndefined();
});

test("CLI discovers Java Test and IT, failing when an AC has no test_BUG_093", async () => {
  const root = await mkdtemp(join(tmpdir(), "harness-BUG-093-"));
  try {
    const workspace = join(root, "harnessclaude");
    await cp(resolve(import.meta.dir, ".."), join(workspace, "hooks"), {
      recursive: true, filter: source => !source.endsWith(".test.ts")
    });
    const moduleDir = join(workspace, "artifacts/03-modules/roles");
    await Bun.write(join(moduleDir, "module.md"), "---\npaths:\n  - backend/src/test/**\n---\n");
    await Bun.write(join(moduleDir, "spec.md"), text);
    await Bun.write(join(root, "backend/src/test/RolesTest.java"), "check_AC_002_1()");
    await Bun.write(join(root, "backend/src/test/RolesIT.java"), "check_AC_002_2()");
    const run = async () => {
      const child = Bun.spawn([process.execPath, join(workspace, "hooks/sdd.ts"), "trace", "roles", "STORY-002"],
        { cwd: root, stdout: "pipe", stderr: "pipe" });
      const [out, err, code] = await Promise.all([
        new Response(child.stdout).text(), new Response(child.stderr).text(), child.exited
      ]);
      return { out, err, code };
    };
    const covered = await run();
    expect(covered.err).toBe("");
    expect(covered.code).toBe(0);
    expect(covered.out).toContain("2/2 acceptance criteria have a test");
    await Bun.write(join(moduleDir, "spec.md"), text + "- AC-002.3: THE backend SHALL deny access.\n");
    const missing = await run();
    expect(missing.code).toBe(1);
    expect(missing.out).toContain("2/3 acceptance criteria have a test");
  } finally {
    if (!resolve(root).startsWith(resolve(tmpdir()) + sep)) throw new Error("Unsafe fixture cleanup path");
    await rm(root, { recursive: true, force: true });
  }
});
