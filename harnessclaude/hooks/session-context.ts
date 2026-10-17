/**
 * Session context — injects the workspace state so the agent orients itself without
 * having to remember to read progress.json.
 *
 *   SessionStart (Claude Code) / sessionStart (Cursor): project, mode, every module's
 *     status, next phase, and whether its spec is approved.
 *   UserPromptSubmit (Claude Code): one line, to keep the state fresh on every turn.
 */
import { join } from "node:path";
import { folderName, isCursor, readInput, workspaceDir } from "./lib/hook-io";
import { type QuickChange, readOpenQuick } from "./lib/quick";
import { type Approval, readApprovals, type SpecState, specState } from "./lib/spec";
import { manifestFields } from "./lib/workspace";

const NEXT: Record<string, string> = {
  decomposed: "plan",
  planned: "implement",
  implementing: "implement",
  evaluating: "evaluate",
  completed: "done",
  blocked: "resolve blocker",
};

/** Statuses where spec approval decides what happens next (it gates the module's code). */
const SPEC_GATED = ["decomposed", "planned", "implementing", "evaluating"];
const SPEC_LABEL: Record<SpecState, string> = {
  approved: "spec ✓ approved",
  "not-approved": "spec ✗ not approved",
  changed: "spec ✗ changed since approval",
  "no-spec": "",
};

type Module = { status?: string; currentPhase?: string; features?: Record<string, string> };

export function moduleLine(name: string, m: Module, spec?: SpecState): string {
  const status = m.status ?? m.currentPhase ?? "unknown";
  const features = Object.values(m.features ?? {});
  const passing = features.filter((f) => f === "passing").length;
  const progress = features.length ? ` ${passing}/${features.length} features passing` : "";
  const label = spec && SPEC_LABEL[spec] ? ` · ${SPEC_LABEL[spec]}` : "";
  return `${name}: ${status} → ${NEXT[status] ?? "?"}${progress}${label}`;
}

export function fullContext(
  manifest: Record<string, string>,
  modules: Record<string, Module>,
  specs: Record<string, SpecState> = {},
  folder = "harnessclaude",
  quick: QuickChange | null = null,
): string {
  const names = Object.keys(modules);
  const layout = manifest.layout ? ` · layout: ${manifest.layout}` : "";
  const lines = [
    `[harnessclaude] project: ${manifest.project ?? "?"} · mode: ${manifest.mode ?? "?"} · language: ${manifest.language ?? "English"}${layout}`,
    names.length
      ? `Modules:\n${names.map((n) => `- ${moduleLine(n, modules[n], specs[n])}`).join("\n")}`
      : "No modules yet → start with the discover phase.",
  ];
  if (quick) {
    lines.push(
      `Quick change open: "${quick.what}" — ${quick.files.join(", ")}, until ${quick.expiresAt}. Only these files are unlocked by it; close it with \`bun ${folder}/hooks/sdd.ts quick done\` after committing.`,
    );
  }
  lines.push(
    `Before acting, read ${folder}/skill.md and follow its dispatch rules. Code outside ${folder}/ is blocked until its module is \`planned\` and the user has approved its spec (\`bun ${folder}/hooks/sdd.ts approve <module>\`) — or, for a Quick-size fix that changes no acceptance criterion, until the user allows a quick change on its files (\`bun ${folder}/hooks/sdd.ts quick "<what>" <file> …\`).`,
  );
  if (manifest.language && !/^english$/i.test(manifest.language)) {
    lines.push(`Respond to the user in ${manifest.language}.`);
  }
  return lines.join("\n");
}

export function briefContext(
  modules: Record<string, Module>,
  specs: Record<string, SpecState> = {},
  folder = "harnessclaude",
  quick: QuickChange | null = null,
): string {
  const names = Object.keys(modules);
  const open = quick ? ` · quick change "${quick.what}" open (${quick.files.length} files)` : "";
  if (!names.length) return `[harnessclaude] no modules yet → discover phase (${folder}/skill.md)${open}`;
  return `[harnessclaude] ${names.map((n) => moduleLine(n, modules[n], specs[n])).join(" · ")}${open}`;
}

if (import.meta.main) {
  const input = await readInput();
  const workspace = workspaceDir(import.meta.dir);
  let modules: Record<string, Module> = {};
  let manifest: Record<string, string> = {};
  try {
    modules = (await Bun.file(join(workspace, "progress.json")).json()).modules ?? {};
    manifest = manifestFields(await Bun.file(join(workspace, "manifest.yaml")).text());
  } catch {
    process.exit(0); // not initialized or unreadable — inject nothing
  }

  let approvals: Record<string, Approval> = {};
  try {
    approvals = await readApprovals(workspace);
  } catch {} // unreadable approvals — show modules without spec status
  const specs: Record<string, SpecState> = {};
  for (const [name, m] of Object.entries(modules)) {
    if (SPEC_GATED.includes(m.status ?? m.currentPhase ?? ""))
      specs[name] = await specState(workspace, name, approvals);
  }

  const brief = input.hook_event_name === "UserPromptSubmit";
  const folder = folderName(import.meta.dir);
  const quick = await readOpenQuick(workspace);
  const text = brief
    ? briefContext(modules, specs, folder, quick)
    : fullContext(manifest, modules, specs, folder, quick);
  console.log(isCursor(input) ? JSON.stringify({ additional_context: text }) : text);
}
