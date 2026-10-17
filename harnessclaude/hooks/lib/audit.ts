/**
 * Workspace audit — the structural checks that do not need judgment, done by code instead of by
 * the agent reading files: core files parse, every module has the artifacts its status requires,
 * statuses are valid, specs pass `check`, approvals are current, tasks match stories, names follow
 * the conventions, and the registry lists what exists. `sdd.ts audit` prints the findings.
 */
import { readdir, stat } from "node:fs/promises";
import { join, relative } from "node:path";
import { FEATURE_STATES, MODULE_STATES, type Progress, statusOf } from "./progress";
import {
  branchExists,
  isAncestor,
  type ManifestInfo,
  readManifest,
  repoFolder,
  repoState,
  resolveCommit,
} from "./repos";
import { lintSpec, lintTasks, parseSpec, readApprovals, specPath, specState } from "./spec";
import { iterationsEnabled, manifestFields, moduleNames, moduleRepoKeys } from "./workspace";

export type Finding = { level: "fail" | "warn"; where: string; message: string };

const MODES = ["blueprint", "explore", "inherit", "undecided"];
const DIRS = [
  "00-baseline",
  "01-research",
  "02-discovery",
  "03-modules",
  "04-plans",
  "05-implementation",
  "06-evaluation",
  "_registry",
];
const PLANNED = ["planned", "implementing", "evaluating", "completed"];
const CODE_OPEN = ["planned", "implementing", "evaluating"];

async function filesUnder(dir: string): Promise<string[]> {
  try {
    const entries = await readdir(dir, { recursive: true, withFileTypes: true });
    return entries.filter((e) => e.isFile()).map((e) => join(e.parentPath, e.name));
  } catch {
    return [];
  }
}

async function isDir(path: string): Promise<boolean> {
  try {
    return (await stat(path)).isDirectory();
  } catch {
    return false;
  }
}

const text = async (path: string) => {
  const file = Bun.file(path);
  return (await file.exists()) ? file.text() : null;
};

/** `verdict:` from a review's YAML frontmatter. */
const verdictOf = (review: string) =>
  review
    .replace(/\r\n?/g, "\n")
    .match(/^---\n[\s\S]*?\n---/)?.[0]
    .match(/^verdict:\s*(\w+)/m)?.[1];

export async function auditWorkspace(workspace: string): Promise<Finding[]> {
  const findings: Finding[] = [];
  const fail = (where: string, message: string) => findings.push({ level: "fail", where, message });
  const warn = (where: string, message: string) => findings.push({ level: "warn", where, message });
  const artifacts = join(workspace, "artifacts");
  const rel = (path: string) => relative(workspace, path).replace(/\\/g, "/");

  // Core files
  const manifestText = await text(join(workspace, "manifest.yaml"));
  const manifest = manifestText === null ? {} : manifestFields(manifestText);
  if (manifestText === null) fail("manifest.yaml", "missing");
  else {
    for (const key of ["project", "mode", "language"]) if (!manifest[key]) fail("manifest.yaml", `no '${key}' field`);
    if (manifest.mode && !MODES.includes(manifest.mode)) {
      fail("manifest.yaml", `mode '${manifest.mode}' is not one of ${MODES.join(", ")}`);
    }
  }
  const iterations = manifestText !== null && iterationsEnabled(manifestText);

  // Repos (manifest v2): every tracked repo is where the manifest says it is
  let info: ManifestInfo | null = null;
  if (manifestText !== null) {
    try {
      info = await readManifest(workspace);
    } catch (err) {
      fail("manifest.yaml", `not valid YAML: ${err instanceof Error ? err.message : err}`);
    }
  }
  if (info) {
    if (!info.marker)
      warn(
        "manifest.yaml",
        "no `harnessclaude:` marker — run `harnessclaude upgrade` (adds it, the layout, and a repos map)",
      );
    if (!info.layout) warn("manifest.yaml", "no valid `layout: monorepo | workspace` — run `harnessclaude upgrade`");
    for (const repo of info.repos) {
      const s = repoState(workspace, repoFolder(workspace, info, repo.key) ?? join(workspace, repo.path));
      if (s.missing) {
        fail(
          `repo ${repo.key}`,
          `missing at ${repo.path}${repo.remote ? " — run `harnessclaude init` to clone it" : " and it has no remote to clone it from"}`,
        );
      } else if (!s.isRepo && info.layout === "workspace")
        warn(`repo ${repo.key}`, `${repo.path} is not a git repository`);
    }
  }

  let progress: Progress | null = null;
  try {
    progress = JSON.parse((await text(join(workspace, "progress.json"))) ?? "");
    if (typeof progress?.modules !== "object" || progress.modules === null) {
      fail("progress.json", "no 'modules' object");
      progress = null;
    }
  } catch {
    fail("progress.json", "missing or not valid JSON");
  }
  let approvals = {};
  try {
    approvals = await readApprovals(workspace);
  } catch {
    fail("approvals.json", "not valid JSON — the gate cannot tell which specs are approved");
  }

  for (const dir of [...DIRS, ...(iterations ? ["07-iterations"] : [])]) {
    if (!(await isDir(join(artifacts, dir)))) fail(`artifacts/${dir}/`, "missing");
  }

  // Iterations
  const current = progress?.currentIteration;
  if (iterations && progress) {
    if (typeof current !== "number")
      fail("progress.json", "iterations are enabled but there is no numeric 'currentIteration'");
    else if (!(await Bun.file(join(artifacts, "07-iterations", `iteration-${current}.md`)).exists())) {
      fail(`artifacts/07-iterations/iteration-${current}.md`, "missing for the current iteration");
    }
  }

  /** A recorded commit that is no longer on the module's branch, or a branch that is gone. */
  function drift(
    where: string,
    module: string,
    branch: string,
    last: Record<string, string>,
    repos: string[],
    m: ManifestInfo,
  ) {
    for (const [key, sha] of Object.entries(last)) {
      const folder = repoFolder(workspace, m, key, repos);
      if (!folder || !repoState(workspace, folder).isRepo) continue; // a missing repo is reported above
      const fix = `check it, then \`sdd.ts commit ${module} ${key}\``;
      if (!branchExists(folder, branch)) warn(where, `branch ${branch} not found in ${key} (merged or deleted?)`);
      else if (!resolveCommit(folder, sha))
        warn(where, `recorded commit ${sha.slice(0, 7)} no longer exists in ${key} — ${fix}`);
      else if (!isAncestor(folder, sha, branch)) {
        warn(where, `recorded commit ${sha.slice(0, 7)} is not on ${branch} in ${key} (history rewritten?) — ${fix}`);
      }
    }
  }

  if (progress && ((progress.version as number | undefined) ?? 1) < 2) {
    warn("progress.json", "version 1 — run `harnessclaude upgrade` (lastCommit per repo)");
  }

  // Modules
  const modules = progress?.modules ?? {};
  for (const [name, entry] of Object.entries(modules)) {
    const where = `module ${name}`;
    const status = statusOf(entry);
    if (!MODULE_STATES.includes(status)) {
      fail(where, `status '${status}' is not one of ${MODULE_STATES.join(", ")}`);
      continue;
    }
    const features = entry.features ?? {};
    for (const [feature, state] of Object.entries(features)) {
      if (!FEATURE_STATES.includes(state)) fail(where, `feature '${feature}' has status '${state}'`);
    }

    const moduleDir = join(artifacts, "03-modules", name);
    if (!(await Bun.file(join(moduleDir, "module.md")).exists())) fail(where, "no 03-modules/{module}/module.md");
    if (info) {
      const known = new Set(["default", ...info.repos.map((r) => r.key)]);
      for (const key of await moduleRepoKeys(workspace, name)) {
        if (!known.has(key)) fail(where, `module.md lists repo '${key}', which manifest.yaml repos does not have`);
      }
    }
    if (typeof entry.lastCommit === "string") {
      fail(where, "lastCommit is a plain string (progress.json version 1) — run `harnessclaude upgrade`");
    } else if (info && entry.branch && entry.lastCommit && (status === "implementing" || status === "evaluating")) {
      drift(where, name, entry.branch, entry.lastCommit, entry.repos ?? [], info);
    }
    if (!PLANNED.includes(status) && status !== "blocked") continue;

    const specText = await text(specPath(workspace, name));
    if (specText === null) {
      fail(where, `is ${status} but has no spec.md`);
      continue;
    }
    const spec = parseSpec(specText);
    const lint = lintSpec(spec);
    if (lint.errors.length) fail(where, `spec has ${lint.errors.length} error(s) — run \`sdd.ts check ${name}\``);
    const state = await specState(workspace, name, approvals);
    if (CODE_OPEN.includes(status) && state !== "approved") {
      fail(
        where,
        `is ${status} but its spec is ${state === "changed" ? "changed since approval" : "not approved"} — its code is locked`,
      );
    }
    if (status === "completed" && state === "changed") {
      warn(where, "spec changed after completion — reopen the module with a change request");
    }

    const plans = join(artifacts, "04-plans", name);
    if (!(await Bun.file(join(plans, "design.md")).exists())) fail(where, "no 04-plans/{module}/design.md");
    const active = iterations && typeof current === "number" ? join(plans, `iteration-${current}`) : plans;
    if (!(await Bun.file(join(active, "tasks.md")).exists())) fail(where, `no ${rel(join(active, "tasks.md"))}`);
    const tasks = (await filesUnder(plans)).filter((f) => f.endsWith("tasks.md"));
    const tasksLint = lintTasks(spec, await Promise.all(tasks.map((f) => Bun.file(f).text())));
    for (const e of tasksLint.errors) fail(where, e);
    for (const w of tasksLint.warnings) warn(where, w);

    if (status !== "planned" && status !== "blocked" && Object.keys(features).length === 0) {
      fail(
        where,
        `is ${status} but has no features — add each task with \`sdd.ts feature ${name} <task-slug> pending\``,
      );
    }
    const reviews = await filesUnder(join(artifacts, "06-evaluation", name));
    for (const [feature, state] of Object.entries(features)) {
      if (state !== "passing") continue;
      const passed = reviews.filter((f) => f.endsWith(`-${feature}-review.md`));
      const verdicts = await Promise.all(passed.map(async (f) => verdictOf(await Bun.file(f).text())));
      if (!verdicts.includes("pass"))
        warn(where, `feature '${feature}' is passing but no review of it has verdict: pass`);
    }
  }

  // Module folders nobody tracks
  for (const name of await moduleNames(workspace)) {
    if (!(name in modules))
      warn(`module ${name}`, `03-modules/${name}/ is not in progress.json — \`sdd.ts status ${name} decomposed\``);
  }

  // Naming conventions
  for (const file of await filesUnder(join(artifacts, "05-implementation"))) {
    const name = file.split(/[\\/]/).pop() ?? "";
    if (!/^task-\d+-[\w.-]+\.md$/.test(name)) warn(rel(file), "session logs are named task-{N}-{slug}.md");
  }
  for (const file of await filesUnder(join(artifacts, "06-evaluation"))) {
    const name = file.split(/[\\/]/).pop() ?? "";
    if (!/^(task-\d+-[\w.-]+-(review|test-report)|security-review)\.md$/.test(name)) {
      warn(rel(file), "evaluation files are named task-{N}-{slug}-review.md / -test-report.md");
    } else if (name.endsWith("-review.md") && name !== "security-review.md") {
      const verdict = verdictOf(await Bun.file(file).text());
      if (verdict !== "pass" && verdict !== "fail") warn(rel(file), "no `verdict: pass | fail` in its frontmatter");
    }
  }

  // Registry
  const index = await text(join(artifacts, "_registry", "artifact-index.md"));
  if (index === null) warn("artifacts/_registry/artifact-index.md", "missing");
  else {
    const listed = new Set(
      index
        .split(/\r?\n/)
        .filter((l) => l.trim().startsWith("|"))
        .map(
          (l) =>
            l
              .trim()
              .replace(/^\||\|$/g, "")
              .split("|")
              .pop()
              ?.trim()
              .replace(/`/g, "") ?? "",
        )
        .filter((p) => /\.\w+$/.test(p))
        .map((p) => (p.startsWith("artifacts/") ? p : `artifacts/${p}`)),
    );
    for (const path of listed) {
      if (!(await Bun.file(join(workspace, path)).exists())) warn(path, "listed in artifact-index.md but missing");
    }
    for (const dir of ["01-research", "02-discovery", "03-modules", "04-plans", "07-iterations"]) {
      for (const file of await filesUnder(join(artifacts, dir))) {
        if (!listed.has(rel(file))) warn(rel(file), "not listed in artifacts/_registry/artifact-index.md");
      }
    }
  }
  return findings;
}
