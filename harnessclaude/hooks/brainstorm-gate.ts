/**
 * Spec gate — code outside the harnessclaude workspace is written only against an approved spec.
 *
 * A write is allowed when a module that owns the file (the `paths` globs in its module.md; a file
 * no module claims counts for every module) is `planned`, `implementing`, or `evaluating` and its
 * spec.md still matches the version the user approved (approvals.json). Writes inside the
 * workspace are always allowed so discovery and planning can produce their artifacts — except
 * approvals.json, which only `sdd.ts approve` writes after the user confirms it here, quick.json,
 * which only `sdd.ts quick` writes after the user confirms it here, and hooks/, which enforces all
 * of this. Changing the editor settings that register the hooks asks the user.
 * Files outside the project are not its code and are not gated.
 *
 * A file listed in the user's open quick change (lib/quick.ts) is writable whatever its module's
 * state — even before any module exists; the repo boundary of a workspace still applies.
 *
 * Shell commands are checked for the files they write (lib/shell-writes.ts) the same way.
 *
 * Registered for: Claude Code PreToolUse (Edit|Write|MultiEdit|NotebookEdit|Bash|PowerShell),
 *                 Cursor preToolUse (Write|Delete|Shell).
 */
import { join, relative } from "node:path";
import {
  allow,
  decide,
  folderName,
  isInside,
  normalize,
  projectRoot,
  readInput,
  shellCommand,
  targetPaths,
  workspaceDir,
} from "./lib/hook-io";
import { QUICK_FILE, QUICK_HOURS, quickArgs, quickCovers, quickFiles, readOpenQuick } from "./lib/quick";
import { readManifest, repoFolder } from "./lib/repos";
import { sddCalls, shellWriteTargets } from "./lib/shell-writes";
import { type Approval, acChanges, acMap, parseSpec, readApprovals, specHash, specPath, specState } from "./lib/spec";
import { matchesAny, modulePaths, moduleRepoKeys } from "./lib/workspace";

const CODE_OPEN = ["planned", "implementing", "evaluating"];
/** Editor settings that register the hooks: removing them turns the gate off. */
const EDITOR_SETTINGS = [".claude/settings.json", ".claude/settings.local.json", ".cursor/hooks.json"];

type Module = { status?: string; currentPhase?: string };

const input = await readInput();
const workspace = workspaceDir(import.meta.dir);
const root = projectRoot(import.meta.dir);
const base = input.cwd || root;
const WS = folderName(import.meta.dir); // the harness folder, whatever it is named

/** `quick`: name the quick change as the way out — every lock but the workspace repo boundary, which it keeps. */
function deny(reason: string, quick = true): never {
  const hint = quick
    ? ` If the change is Quick-size (no acceptance criterion changes — ${WS}/skill.md, Size the Work), open a quick change instead: \`bun ${WS}/hooks/sdd.ts quick "<what>" <file> …\`; the user confirms it.`
    : "";
  decide(input, "deny", `harnessclaude gate: ${reason}${hint} Writes inside ${WS}/ are allowed.`);
}

/** "added AC-001.3, AC-002.1; changed AC-001.1; removed AC-003.2" — at most `max` ids per kind. */
function describeChanges(changes: Record<string, string[]>, max = 6): string {
  return Object.entries(changes)
    .filter(([, ids]) => ids.length)
    .map(
      ([kind, ids]) =>
        `${kind} ${ids.slice(0, max).join(", ")}${ids.length > max ? ` and ${ids.length - max} more` : ""}`,
    )
    .join("; ");
}

/** The question the user answers before the agent's `sdd.ts approve` runs: what they approve, and what changed. */
async function approvalQuestion(name: string | undefined): Promise<string> {
  const file = name ? Bun.file(specPath(workspace, name)) : null;
  if (!name || !file || !(await file.exists())) return `harnessclaude: approve the spec of module '${name ?? "?"}'?`;
  const text = await file.text();
  const spec = parseSpec(text);
  const acs = spec.stories.reduce((n, s) => n + s.acs.length, 0);
  let since = "";
  try {
    const previous = (await readApprovals(workspace))[name];
    if (previous?.specHash === specHash(text)) since = " It is unchanged since your last approval.";
    else if (previous?.acs) {
      const changes = describeChanges(acChanges(previous.acs, acMap(spec)));
      since = ` Since your last approval: ${changes || "criteria unchanged, other text edited"} (details: \`bun ${WS}/hooks/sdd.ts diff ${name}\`).`;
    } else if (previous) since = " It changed since your last approval (that approval did not record its criteria).";
  } catch {} // unreadable approvals.json — approve reports it
  return (
    `harnessclaude: approve the spec of module '${name}' (${spec.stories.length} stories, ${acs} acceptance criteria, ` +
    `version ${specHash(text).slice(0, 8)})?${since} Approving unlocks the module's code; any later spec edit locks it again until you approve the new version.`
  );
}

/** The question the user answers before the agent's `sdd.ts quick` opens a quick change. */
async function quickQuestion(what: string, files: string[]): Promise<string> {
  const open = await readOpenQuick(workspace);
  return (
    `harnessclaude: open a quick change "${what}" for ${QUICK_HOURS} hours? It unlocks only: ${files.join(", ")}. ` +
    "A quick change must not change any acceptance criterion — behavior changes go through the spec. " +
    `Close it with \`sdd.ts quick done\`.${open ? ` It replaces the open quick change "${open.what}".` : ""}`
  );
}

// Questions for the user; asked once nothing is denied. Spec approval and quick changes are always confirmed by a person.
const command = shellCommand(input);
const questions: string[] = [];
for (const call of command ? sddCalls(command) : []) {
  if (/[$`]/.test(call.sub)) {
    decide(
      input,
      "deny",
      "harnessclaude gate: write the `sdd.ts` subcommand out — not from a variable — so the gate can tell what it does.",
    );
  }
  if (call.sub === "approve") questions.push(await approvalQuestion(call.args[0]));
  if (call.sub !== "quick") continue;
  // The user must approve exactly what sdd.ts records, so only the plain form is run past them.
  const args = quickArgs(command);
  if (!args) {
    decide(
      input,
      "deny",
      `harnessclaude gate: run \`bun ${WS}/hooks/sdd.ts quick "<what>" <file> …\` (or \`… quick done\`) as a command of its own, with the description quoted and exact file paths with forward slashes — no variables, globs, pipes, or chained commands — so the user approves exactly what is recorded.`,
    );
  }
  if (!(args.length === 1 && args[0] === "done")) {
    const opened = quickFiles(args, root, base, workspace);
    if ("error" in opened) decide(input, "deny", `harnessclaude gate: ${opened.error}`);
    questions.push(await quickQuestion(opened.what, opened.files)); // the files as sdd.ts records them
  }
}

function done(): never {
  if (questions.length) decide(input, "ask", questions.join(" "));
  allow();
}

const paths = command ? shellWriteTargets(command) : targetPaths(input);
// Nothing written (or an unknown tool shape) → fail open rather than block discovery.
if (paths.length === 0) done();

const ws = normalize(workspace, base);
const targets = paths.map((p) => normalize(p, base));
/** The write hits `protectedPath`: the path itself, something inside it, or a folder that holds it. */
const hits = (protectedPath: string) => targets.some((t) => isInside(t, protectedPath) || isInside(protectedPath, t));

if (hits(normalize(join(workspace, "approvals.json"), base))) {
  decide(
    input,
    "deny",
    `harnessclaude gate: approvals.json records what the user approved — only \`bun ${WS}/hooks/sdd.ts approve <module>\` writes it, after the user confirms.`,
  );
}
if (hits(normalize(join(workspace, QUICK_FILE), base))) {
  decide(
    input,
    "deny",
    `harnessclaude gate: ${QUICK_FILE} records the quick change the user approved — only \`bun ${WS}/hooks/sdd.ts quick\` writes it, after the user confirms.`,
  );
}
if (hits(normalize(join(workspace, "hooks"), base))) {
  decide(
    input,
    "deny",
    `harnessclaude gate: ${WS}/hooks/ enforces the workflow, so the agent never changes it. If a hook is wrong, tell the user; framework files are refreshed by \`harnessclaude upgrade\`.`,
  );
}
const settings = EDITOR_SETTINGS.map((f) => normalize(join(root, f), base)).filter(hits);
if (settings.length) {
  questions.push(
    `harnessclaude: this changes editor settings that register the harnessclaude hooks (${settings.map((s) => relative(normalize(root, base), s)).join(", ")}). Approve only if you asked for it — removing the hooks turns the spec gate off.`,
  );
}

const project = normalize(root, base);
const code = targets.filter((p) => isInside(p, project) && !isInside(p, ws) && !settings.includes(p));
if (code.length === 0) done();

const progressFile = Bun.file(join(workspace, "progress.json"));
if (!(await progressFile.exists())) done(); // workspace not initialized — nothing to enforce

let modules: Record<string, Module> = {};
let approvals: Record<string, Approval> = {};
try {
  modules = (await progressFile.json()).modules ?? {};
  approvals = await readApprovals(workspace);
} catch {
  console.error("harnessclaude gate: progress.json or approvals.json is not valid JSON — gate skipped");
  done();
}
const names = Object.keys(modules);

// The repos manifest.yaml tracks; without a readable manifest the repo checks are skipped.
const info = await readManifest(workspace).catch(() => null);
const folderOf = (key: string) => {
  const dir = info ? repoFolder(workspace, info, key) : null;
  return dir ? normalize(dir, base) : null;
};
const relOf = (file: string) => relative(project, file).replace(/\\/g, "/");

// Workspace layout: code lives in the repos — a file outside every tracked repo belongs to no module.
if (info?.layout === "workspace" && info.repos.length) {
  const repos = info.repos.map((r) => folderOf(r.key)).filter((d): d is string => d !== null);
  for (const file of code) {
    if (!repos.some((d) => isInside(file, d))) {
      deny(
        `${relOf(file)} is outside every repo in ${WS}/manifest.yaml — in a workspace, code is written only inside the repos.`,
        false,
      );
    }
  }
}

// Files of the user's open quick change are writable whatever their module's state.
const quick = await readOpenQuick(workspace);
const gated = quick ? code.filter((file) => !quickCovers(quick, relOf(file))) : code;
if (gated.length === 0) done();
if (names.length === 0) deny(`no modules yet. Run the discover phase first (read ${WS}/skill.md).`);

/** The folders of the repos a module.md lists; null when it lists none (or only `default`, the whole repo). */
async function moduleRepoDirs(name: string): Promise<string[] | null> {
  const keys = (await moduleRepoKeys(workspace, name)).filter((k) => k !== "default");
  if (!info || !keys.length) return null;
  return keys.map(folderOf).filter((d): d is string => d !== null);
}

/**
 * Modules that own the file: those whose `paths` claim it; else those whose `repos` hold it, plus
 * modules that list no repos. `claimed` when that is not every module.
 */
async function ownersOf(file: string): Promise<{ owners: string[]; claimed: boolean }> {
  const rel = relOf(file);
  const byPaths: string[] = [];
  for (const name of names) {
    if (matchesAny(await modulePaths(workspace, name), rel)) byPaths.push(name);
  }
  if (byPaths.length) return { owners: byPaths, claimed: true };
  const owners: string[] = [];
  for (const name of names) {
    const dirs = await moduleRepoDirs(name);
    if (!dirs || dirs.some((d) => isInside(file, d))) owners.push(name);
  }
  if (!owners.length) {
    deny(
      `${rel} is in no module's repos — add its repo to a module's \`repos:\` (or the folder to its \`paths\`) in module.md.`,
    );
  }
  return { owners, claimed: owners.length < names.length };
}

/** Why a module keeps its code locked, ranked by how close it is to unlocking (lower = closer); null = unlocked. */
async function lockOf(name: string, claimed: boolean): Promise<[number, string] | null> {
  const status = modules[name].status ?? modules[name].currentPhase ?? "";
  if (CODE_OPEN.includes(status)) {
    const state = await specState(workspace, name, approvals);
    const check = `Run \`bun ${WS}/hooks/sdd.ts check ${name}\`, then`;
    if (state === "approved") return null;
    if (state === "changed") {
      return [
        1,
        `the spec of module '${name}' changed after the user approved it. ${check} ask the user to approve the new version (\`sdd.ts approve ${name}\`).`,
      ];
    }
    if (state === "not-approved")
      return [
        2,
        `the spec of module '${name}' is not approved yet. ${check} \`sdd.ts approve ${name}\` — the user confirms it.`,
      ];
    return [
      2,
      `module '${name}' has no spec (artifacts/03-modules/${name}/spec.md). Write it and get it approved first.`,
    ];
  }
  if (status === "completed") {
    return [
      3,
      `module '${name}' is completed. Changes go through a change request (plan skill, Change Mode): update the spec or add a task, get it approved, then move the module back to 'planned'.`,
    ];
  }
  if (status === "blocked")
    return [
      3,
      `module '${name}' is blocked — resolve the blocker with the user first (update-tracking: blocked → implementing).`,
    ];
  return [
    4,
    claimed
      ? `module '${name}' is '${status || "unknown"}' — run the plan skill on it before writing its code (it must reach 'planned').`
      : "no module has reached 'planned'. Run the plan skill on a module before writing code.",
  ];
}

for (const file of gated) {
  const { owners, claimed } = await ownersOf(file);
  const locks: [number, string][] = [];
  for (const name of owners) {
    const lock = await lockOf(name, claimed);
    if (!lock) break; // one owning module allows it
    locks.push(lock);
  }
  if (locks.length === owners.length) deny(locks.sort((a, b) => a[0] - b[0])[0][1]);
}
done();
