/**
 * Spec-driven workflow commands — run by the agent or by you, from the project root:
 *
 *   bun harnessclaude/hooks/sdd.ts check   <module>                    lint spec.md and its tasks; exit 1 on errors
 *   bun harnessclaude/hooks/sdd.ts trace   <module> [STORY-…]          acceptance criteria → tests; exit 1 if any is uncovered
 *   bun harnessclaude/hooks/sdd.ts approve <module>                    record the approved spec version; unlocks code
 *   bun harnessclaude/hooks/sdd.ts diff    <module>                    criteria changed since the last approval
 *   bun harnessclaude/hooks/sdd.ts status  <module> <status>           move a module along the state machine
 *   bun harnessclaude/hooks/sdd.ts feature <module> <feature> <status> add a feature (pending) or move it
 *   bun harnessclaude/hooks/sdd.ts commit  <module> <repo> [sha]       record the module's last commit in a repo (HEAD by default)
 *   bun harnessclaude/hooks/sdd.ts repos                               each repo: folder, role, branch, uncommitted files
 *   bun harnessclaude/hooks/sdd.ts audit                               check the whole workspace; exit 1 on failures
 *   bun harnessclaude/hooks/sdd.ts quick   "<what>" <file>…            open a quick change on those files (≤ 5, 24 h)
 *   bun harnessclaude/hooks/sdd.ts quick   done                        close the open quick change
 *
 * Lives next to the hooks because it shares hooks/lib/ and `harnessclaude upgrade` refreshes it.
 * When the agent runs `approve` or opens a quick change, the gate hook asks the user to confirm first.
 */
import { appendFile, mkdir, readdir, rm } from "node:fs/promises";
import { join, relative } from "node:path";
import { auditWorkspace } from "./lib/audit";
import { folderName, projectRoot, workspaceDir } from "./lib/hook-io";
import { type Change, type Progress, setFeatureStatus, setModuleStatus } from "./lib/progress";
import { QUICK_FILE, QUICK_HOURS, quickFiles } from "./lib/quick";
import { type ManifestInfo, readManifest, repoFolder, repoState, resolveCommit } from "./lib/repos";
import {
  type Approval,
  acChanges,
  acMap,
  lintSpec,
  lintTasks,
  parseSpec,
  readApprovals,
  type Spec,
  specHash,
  specPath,
  specState,
  trace,
} from "./lib/spec";
import { moduleRepoKeys, moduleScopes, testOwners } from "./lib/workspace";

const WS = folderName(import.meta.dir); // the harness folder, whatever it is named
const USAGE = `usage: bun ${WS}/hooks/sdd.ts <command>
  check <module> · trace <module> [STORY-001 …] · approve <module> · diff <module>
  status <module> <status> · feature <module> <feature> <status> · commit <module> <repo> [sha] · repos · audit
  quick "<what>" <file>… · quick done`;
const TEST_GLOBS = ["**/*.{test,spec}.*", "**/test_*.py", "**/*_test.*"];
// ponytail: the glob walks node_modules too before this filter drops it; use `git ls-files` if it gets slow.
const notProjectTest = (path: string) => /(^|\/)(node_modules|\.git)\//.test(path) || path.startsWith(`${WS}/`);
const NAME = /^\w[\w.-]*$/;

const workspace = workspaceDir(import.meta.dir);
const root = projectRoot(import.meta.dir);
const now = () => new Date().toISOString();

function fail(message: string): never {
  console.error(message);
  process.exit(1);
}

async function loadSpec(module: string): Promise<{ text: string; spec: Spec }> {
  const file = Bun.file(specPath(workspace, module));
  if (!(await file.exists())) fail(`No spec for module '${module}' at ${relative(root, specPath(workspace, module))}.`);
  const text = await file.text();
  return { text, spec: parseSpec(text) };
}

async function loadApprovals(): Promise<Record<string, Approval>> {
  try {
    return await readApprovals(workspace);
  } catch {
    fail(`${WS}/approvals.json is not valid JSON — fix or delete it, then try again.`);
  }
}

async function logEvent(event: Record<string, unknown>) {
  await mkdir(join(workspace, "events"), { recursive: true });
  await appendFile(join(workspace, "events", "events.jsonl"), `${JSON.stringify({ ts: now(), ...event })}\n`);
}

/** Every tasks.md of the module's plan, iteration folders included. */
async function tasksFiles(module: string): Promise<string[]> {
  const plans = join(workspace, "artifacts", "04-plans", module);
  try {
    const entries = await readdir(plans, { recursive: true, withFileTypes: true });
    const files = entries.filter((e) => e.isFile() && e.name === "tasks.md").map((e) => join(e.parentPath, e.name));
    return Promise.all(files.map((f) => Bun.file(f).text()));
  } catch {
    return [];
  }
}

async function check(module: string, spec: Spec): Promise<boolean> {
  const { errors, warnings } = lintSpec(spec);
  const tasks = await tasksFiles(module);
  const tasksLint = tasks.length ? lintTasks(spec, tasks) : { errors: [], warnings: [] };
  for (const e of errors) console.log(`  ✗ ${e}`);
  for (const e of tasksLint.errors) console.log(`  ✗ ${e}`);
  for (const w of [...warnings, ...tasksLint.warnings]) console.log(`  ! ${w}`);
  const count = errors.length + tasksLint.errors.length;
  const notes = warnings.length + tasksLint.warnings.length;
  const acs = spec.stories.reduce((n, s) => n + s.acs.length, 0);
  console.log(
    `spec ${module}: ${spec.stories.length} stories, ${acs} acceptance criteria — ${count ? `${count} error(s)` : "OK"}${notes ? `, ${notes} warning(s)` : ""}`,
  );
  return count === 0;
}

async function traceTests(module: string, spec: Spec, stories: string[]): Promise<boolean> {
  const missing = stories.filter((id) => !spec.stories.some((s) => s.id === id));
  if (missing.length) fail(`Unknown stories in ${module}: ${missing.join(", ")}`);

  // A test counts for this module only if the module may own it (its `paths`, or no module claims it).
  const scopes = await moduleScopes(workspace);
  const mine = scopes.find((s) => s.name === module);
  const tests: Parameters<typeof trace>[1] = [];
  const seen = new Set<string>();
  for (const pattern of TEST_GLOBS) {
    for await (const found of new Bun.Glob(pattern).scan({ cwd: root, onlyFiles: true })) {
      const path = found.replace(/\\/g, "/");
      if (notProjectTest(path) || seen.has(path)) continue;
      seen.add(path);
      const owners = testOwners(path, scopes);
      if (!owners.includes(module)) continue;
      const others = scopes.filter((s) => s.name !== module && owners.includes(s.name));
      tests.push({ path, text: await Bun.file(join(root, found)).text(), others });
    }
  }

  const result = trace(spec, tests, stories);
  const globs = mine?.globs ?? [];
  for (const [id, files] of Object.entries(result.covered)) {
    if (files.length) {
      console.log(`  ✓ ${id}  ${files.join(", ")}`);
    } else if (result.ambiguous[id]) {
      console.log(
        `  ✗ ${id}  ambiguous: ${result.ambiguous[id].join(", ")} — another module's spec has the same id; give each module \`paths\` in its module.md (its code and its tests) so trace knows whose test it is`,
      );
    } else {
      const where = globs.length
        ? ` under this module's paths (${globs.join(", ")}) — tests elsewhere? add their folder to paths`
        : "";
      console.log(`  ✗ ${id}  no test${where} mentions it`);
    }
  }
  if (result.unknown.length) console.log(`  ! ids in tests but not in the spec: ${result.unknown.join(", ")}`);
  const total = Object.keys(result.covered).length;
  const scope = stories.length ? ` (${stories.join(", ")})` : "";
  console.log(`trace ${module}${scope}: ${total - result.uncovered.length}/${total} acceptance criteria have a test`);
  return result.uncovered.length === 0;
}

async function approve(module: string, text: string, spec: Spec): Promise<boolean> {
  if (!(await check(module, spec))) fail(`Not approved: fix the errors above in the spec of '${module}' first.`);
  const approvals = await loadApprovals();
  const hash = specHash(text);
  approvals[module] = { specHash: hash, approvedAt: now(), by: "user", acs: acMap(spec) };
  await Bun.write(join(workspace, "approvals.json"), `${JSON.stringify(approvals, null, 2)}\n`);
  await logEvent({ type: "spec-approved", module, specHash: hash });
  console.log(
    `approved ${module} (spec version ${hash.slice(0, 8)}) — its code unlocks once the module is planned; editing the spec locks it again.`,
  );
  return true;
}

async function diff(module: string, text: string, spec: Spec): Promise<boolean> {
  const approval = (await loadApprovals())[module];
  const current = acMap(spec);
  if (!approval) {
    console.log(`${module}: not approved yet — ${Object.keys(current).length} acceptance criteria to review.`);
    return true;
  }
  if (approval.specHash === specHash(text)) {
    console.log(`${module}: the spec is unchanged since it was approved (${approval.approvedAt}).`);
    return true;
  }
  if (!approval.acs) {
    console.log(`${module}: the spec changed since it was approved, but that approval did not record its criteria.`);
    return true;
  }
  const { added, changed, removed } = acChanges(approval.acs, current);
  for (const id of added) console.log(`  + ${id}: ${current[id]}`);
  for (const id of changed) console.log(`  ~ ${id}: ${approval.acs[id]}\n      → ${current[id]}`);
  for (const id of removed) console.log(`  - ${id}: ${approval.acs[id]}`);
  const same = !added.length && !changed.length && !removed.length;
  console.log(
    `${module}: changed since approval — ${same ? "criteria unchanged (other text edited)" : `${added.length} added, ${changed.length} changed, ${removed.length} removed`}`,
  );
  return true;
}

async function readProgress(): Promise<Progress> {
  try {
    return JSON.parse(await Bun.file(join(workspace, "progress.json")).text());
  } catch {
    fail(`${WS}/progress.json is missing or not valid JSON — run \`harnessclaude init\`, or fix it by hand.`);
  }
}

/** Write the changed progress.json and log the event; a no-op change writes nothing. */
async function applyChange(progress: Progress, change: Change, label: string, event: Record<string, unknown>) {
  if ("error" in change) fail(change.error);
  if (change.from === change.to) {
    console.log(`${label}: already ${change.to}`);
    return true;
  }
  await Bun.write(join(workspace, "progress.json"), `${JSON.stringify(progress, null, 2)}\n`);
  await logEvent({ ...event, from: change.from || null, to: change.to });
  console.log(`${label}: ${change.from || "(new)"} → ${change.to}`);
  return true;
}

async function status(module: string, to: string): Promise<boolean> {
  const progress = await readProgress();
  const change = setModuleStatus(progress, module, to, now()); // in memory until applyChange writes it
  // Entering `planned` needs the user's approval of the current spec — it is what unlocks code.
  if (!("error" in change) && to === "planned" && change.from !== to) {
    const state = await specState(workspace, module, await loadApprovals());
    if (state !== "approved") {
      fail(
        state === "no-spec"
          ? `module '${module}' has no spec — write artifacts/03-modules/${module}/spec.md and get it approved before it is planned`
          : `the spec of module '${module}' is ${state === "changed" ? "changed since its approval" : "not approved"} — the user approves it first (\`sdd.ts approve ${module}\`)`,
      );
    }
  }
  // Entering `implementing` fixes the module's branch and repos (skill.md "Repos and Git").
  if (!("error" in change) && to === "implementing" && progress.modules) {
    const entry = progress.modules[module];
    entry.branch ??= `feat/${module}`;
    if (!entry.repos?.length) {
      const keys = await moduleRepoKeys(workspace, module);
      entry.repos = keys.length ? keys : ["default"];
    }
    if (!entry.lastCommit || typeof entry.lastCommit !== "object") entry.lastCommit = {};
    if (change.from !== to) console.log(`module ${module}: branch ${entry.branch} in ${entry.repos.join(", ")}`);
  }
  return applyChange(progress, change, `module ${module}`, { type: "module-status", module });
}

async function loadManifest(): Promise<ManifestInfo> {
  try {
    return await readManifest(workspace);
  } catch (err) {
    fail(`${WS}/manifest.yaml is missing or not valid YAML: ${err instanceof Error ? err.message : err}`);
  }
}

/** Record the module's last commit in a repo: the SHA given, or that repo's HEAD. */
async function commit(module: string, repo: string, rev = "HEAD"): Promise<boolean> {
  const progress = await readProgress();
  const entry = progress.modules?.[module];
  if (!entry) fail(`module '${module}' is not in progress.json`);
  const info = await loadManifest();
  const moduleRepos = entry.repos?.length ? entry.repos : await moduleRepoKeys(workspace, module);
  const folder = repoFolder(workspace, info, repo, moduleRepos);
  if (!folder) {
    const known = ["default", ...info.repos.map((r) => r.key)].join(", ");
    fail(`unknown repo '${repo}' — use a manifest.yaml repos key: ${known}`);
  }
  const state = repoState(workspace, folder);
  if (state.missing) fail(`repo '${repo}' is missing at ${folder} — run \`harnessclaude init\` to clone it`);
  if (!state.isRepo) fail(`repo '${repo}' at ${folder} is not a git repository`);
  const sha = resolveCommit(folder, rev);
  if (!sha) fail(`'${rev}' is not a commit in repo '${repo}' (${folder})`);
  entry.lastCommit = {
    ...(entry.lastCommit && typeof entry.lastCommit === "object" ? entry.lastCommit : {}),
    [repo]: sha,
  };
  entry.lastUpdated = now();
  await Bun.write(join(workspace, "progress.json"), `${JSON.stringify(progress, null, 2)}\n`);
  await logEvent({ type: "commit-recorded", module, repo, sha });
  console.log(`module ${module}: lastCommit.${repo} = ${sha.slice(0, 7)} (${state.branch})`);
  return true;
}

/** Each repo the workspace tracks — and the harness repo in the workspace layout. */
async function repos(): Promise<boolean> {
  const info = await loadManifest();
  const list = info.repos.length ? info.repos : [{ key: "default", path: ".." }];
  const rows: string[][] = [["repo", "folder", "role", "branch", "status"]];
  for (const repo of list) {
    const folder = repoFolder(workspace, info, repo.key) ?? join(workspace, repo.path);
    const s = repoState(workspace, folder);
    const status = s.missing
      ? "missing — run `harnessclaude init`"
      : !s.isRepo
        ? "not a git repo"
        : s.dirty
          ? `${s.dirty} uncommitted`
          : "clean";
    rows.push([repo.key, relative(root, folder).replace(/\\/g, "/") || ".", repo.role ?? "", s.branch, status]);
  }
  if (info.layout === "workspace") {
    const s = repoState(workspace, workspace);
    rows.push([
      `(${WS})`,
      WS,
      "harness",
      s.branch,
      !s.isRepo ? "not a git repo" : s.dirty ? `${s.dirty} uncommitted` : "clean",
    ]);
  }
  const widths = rows[0].map((_, i) => Math.max(...rows.map((r) => r[i].length)));
  for (const r of rows)
    console.log(
      r
        .map((cell, i) => cell.padEnd(widths[i]))
        .join("  ")
        .trimEnd(),
    );
  console.log(`layout: ${info.layout ?? "unknown — run `harnessclaude upgrade`"}`);
  return true;
}

async function feature(module: string, name: string, to: string): Promise<boolean> {
  if (!NAME.test(name)) fail(`Invalid feature name '${name}' — use the task slug (letters, digits, - . _).`);
  const progress = await readProgress();
  const change = setFeatureStatus(progress, module, name, to, now());
  return applyChange(progress, change, `feature ${module}/${name}`, { type: "feature-status", module, feature: name });
}

/**
 * Open a quick change on exact files, or close the open one (`done`). The gate asks the user
 * before an agent opens one; closing only narrows what is writable, so it needs no confirmation.
 */
async function quick(args: string[]): Promise<boolean> {
  const file = join(workspace, QUICK_FILE);
  if (args.length === 1 && args[0] === "done") {
    if (!(await Bun.file(file).exists())) {
      console.log("no quick change is open");
      return true;
    }
    const closed = await Bun.file(file)
      .json()
      .catch(() => ({}));
    await rm(file, { force: true });
    await logEvent({ type: "quick-closed", what: closed.what ?? null, files: closed.files ?? [] });
    console.log(`closed quick change "${closed.what ?? "?"}"`);
    return true;
  }
  const opened = quickFiles(args, root, process.cwd(), workspace);
  if ("error" in opened) fail(opened.error);
  const at = new Date();
  const expiresAt = new Date(at.getTime() + QUICK_HOURS * 3600 * 1000).toISOString();
  const record = { ...opened, approvedAt: at.toISOString(), expiresAt, by: "user" };
  await Bun.write(file, `${JSON.stringify(record, null, 2)}\n`);
  await logEvent({ type: "quick-opened", what: opened.what, files: opened.files });
  console.log(
    `quick change "${opened.what}" open until ${expiresAt}: ${opened.files.join(", ")} — close it with \`bun ${WS}/hooks/sdd.ts quick done\` after committing.`,
  );
  return true;
}

async function audit(): Promise<boolean> {
  const findings = await auditWorkspace(workspace);
  const fails = findings.filter((f) => f.level === "fail");
  const warns = findings.filter((f) => f.level === "warn");
  for (const f of fails) console.log(`  ✗ ${f.where}: ${f.message}`);
  for (const f of warns) console.log(`  ! ${f.where}: ${f.message}`);
  console.log(
    `audit: ${fails.length ? `${fails.length} failure(s)` : "OK"}${warns.length ? `, ${warns.length} warning(s)` : ""}`,
  );
  return fails.length === 0;
}

const [command, module, ...rest] = process.argv.slice(2);
if (command === "audit") process.exit((await audit()) ? 0 : 1);
if (command === "repos") process.exit((await repos()) ? 0 : 1);
if (command === "quick") process.exit((await quick(process.argv.slice(3))) ? 0 : 1);
if (!["check", "trace", "approve", "diff", "status", "feature", "commit"].includes(command) || !module) fail(USAGE);
if (!NAME.test(module)) fail(`Invalid module name '${module}'.`);

let ok: boolean;
if (command === "commit") {
  if (!rest[0] || !NAME.test(rest[0])) fail(USAGE);
  ok = await commit(module, rest[0], rest[1]);
} else if (command === "status") {
  if (!rest[0]) fail(USAGE);
  ok = await status(module, rest[0]);
} else if (command === "feature") {
  if (!rest[0] || !rest[1]) fail(USAGE);
  ok = await feature(module, rest[0], rest[1]);
} else {
  const { text, spec } = await loadSpec(module);
  ok =
    command === "check"
      ? await check(module, spec)
      : command === "trace"
        ? await traceTests(module, spec, rest)
        : command === "approve"
          ? await approve(module, text, spec)
          : await diff(module, text, spec);
}
process.exit(ok ? 0 : 1);
