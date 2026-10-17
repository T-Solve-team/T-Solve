/**
 * Workspace layout — modules, the code they own, manifest fields, and plan paths. Shared by the
 * gate, session-context, and sdd.ts.
 */
import { readdir } from "node:fs/promises";
import { join } from "node:path";
import { parseSpec, specPath } from "./spec";

/** Case-folded on Windows, where paths compare case-insensitively. */
export const fold = (s: string) => (process.platform === "win32" ? s.toLowerCase() : s);

/** Top-level `key: value` pairs from manifest.yaml — enough for project/mode/language. */
export function manifestFields(yaml: string): Record<string, string> {
  const fields: Record<string, string> = {};
  for (const line of yaml.split(/\r?\n/)) {
    const m = line.match(/^([a-z_]+):\s*([^#]*?)\s*(?:#.*)?$/i);
    if (m?.[2]) fields[m[1]] = m[2];
  }
  return fields;
}

/** manifest.yaml has `iterations:` with `enabled: true` under it. */
export function iterationsEnabled(yaml: string): boolean {
  return /^iterations:\s*\n(?:[ \t]+.*\n)*?[ \t]+enabled:\s*true\b/m.test(yaml.replace(/\r\n?/g, "\n"));
}

/** A list field of a module.md's frontmatter — inline `[a, b]` or a `- item` list. */
export function frontmatterList(moduleMd: string, key: string): string[] {
  const front = moduleMd.replace(/\r\n?/g, "\n").match(/^---\n([\s\S]*?)\n---/)?.[1] ?? "";
  const lines = front.split("\n").map((l) => l.replace(/\s+#.*$/, ""));
  const at = lines.findIndex((l) => l.startsWith(`${key}:`));
  if (at === -1) return [];
  const inline = lines[at].slice(key.length + 1).match(/^\s*\[(.*)\]\s*$/);
  const items = inline ? inline[1].split(",") : [];
  if (!inline) {
    for (const line of lines.slice(at + 1)) {
      const item = line.match(/^\s+-\s*(.*)$/);
      if (!item) break;
      items.push(item[1]);
    }
  }
  // Template placeholders like {repo keys …} are not values.
  return items.map((s) => s.trim().replace(/^["']|["']$/g, "")).filter((s) => s && !s.startsWith("{"));
}

/** `paths` globs from a module.md: the code (and tests) the module owns. */
export const parseModulePaths = (moduleMd: string) => frontmatterList(moduleMd, "paths");

async function moduleList(workspace: string, name: string, key: string): Promise<string[]> {
  const file = Bun.file(join(workspace, "artifacts", "03-modules", name, "module.md"));
  return (await file.exists()) ? frontmatterList(await file.text(), key) : [];
}

export const modulePaths = (workspace: string, name: string) => moduleList(workspace, name, "paths");

/** The manifest repo keys a module's module.md lists. */
export const moduleRepoKeys = (workspace: string, name: string) => moduleList(workspace, name, "repos");

/** Does a project-relative path (forward slashes) match any of the globs? */
export function matchesAny(globs: string[], rel: string): boolean {
  return globs.some((g) => new Bun.Glob(fold(g)).match(fold(rel)));
}

/** Every module folder under artifacts/03-modules/. */
export async function moduleNames(workspace: string): Promise<string[]> {
  try {
    const entries = await readdir(join(workspace, "artifacts", "03-modules"), { withFileTypes: true });
    return entries.filter((e) => e.isDirectory()).map((e) => e.name);
  } catch {
    return [];
  }
}

/** A module as trace sees it: the files it owns and the acceptance-criterion ids its spec defines. */
export interface ModuleScope {
  name: string;
  globs: string[];
  acIds: Set<string>;
}

export async function moduleScopes(workspace: string): Promise<ModuleScope[]> {
  const scopes: ModuleScope[] = [];
  for (const name of await moduleNames(workspace)) {
    const spec = Bun.file(specPath(workspace, name));
    const acIds = (await spec.exists())
      ? new Set(parseSpec(await spec.text()).stories.flatMap((s) => s.acs.map((a) => a.id)))
      : new Set<string>();
    scopes.push({ name, globs: await modulePaths(workspace, name), acIds });
  }
  return scopes;
}

/**
 * Modules a test file may belong to: those whose `paths` claim it; a file no module claims belongs
 * to every module that has no `paths`. Stricter than the gate on purpose — coverage must not be
 * credited to a module from another module's tests.
 */
export function testOwners(rel: string, modules: ModuleScope[]): string[] {
  const claimed = modules.filter((m) => matchesAny(m.globs, rel));
  return (claimed.length ? claimed : modules.filter((m) => m.globs.length === 0)).map((m) => m.name);
}
