/**
 * The repos a workspace tracks — manifest.yaml `layout` and `repos` — and the git facts sdd.ts
 * needs about them: folder, current branch, uncommitted files, whether a commit is on a branch.
 */
import { existsSync } from "node:fs";
import { basename, dirname, join, relative, resolve } from "node:path";

export type Layout = "monorepo" | "workspace";

export interface Repo {
  key: string;
  path: string;
  remote?: string;
  default_branch?: string;
  role?: string;
  stack?: string;
  test_cmd?: string;
}

export interface ManifestInfo {
  /** Has the `harnessclaude:` marker with a version (manifest v2). */
  marker: boolean;
  layout: Layout | null;
  repos: Repo[];
}

/** Repos from a `repos` map, or a v1 list (keyed by folder name; `..` is `default`). */
export function normalizeRepos(value: unknown): Repo[] {
  const out: Repo[] = [];
  if (Array.isArray(value)) {
    for (const item of value) {
      const fields = typeof item === "object" && item ? (item as Record<string, string>) : {};
      const path = typeof item === "string" ? item : fields.path;
      if (typeof path === "string" && path)
        out.push({ ...fields, key: basename(path) === ".." ? "default" : basename(path), path });
    }
  } else if (value && typeof value === "object") {
    for (const [key, entry] of Object.entries(value as Record<string, unknown>)) {
      if (typeof entry === "string") out.push({ key, path: entry });
      else if (entry && typeof entry === "object" && typeof (entry as Repo).path === "string")
        out.push({ ...(entry as Repo), key });
    }
  }
  return out;
}

/** manifest.yaml as sdd.ts needs it. Throws when the file is missing or not valid YAML. */
export async function readManifest(workspace: string): Promise<ManifestInfo> {
  const data = Bun.YAML.parse(await Bun.file(join(workspace, "manifest.yaml")).text()) as Record<string, any> | null;
  const marker = data?.harnessclaude?.version;
  return {
    marker: (typeof marker === "string" && marker !== "") || typeof marker === "number",
    layout: data?.layout === "monorepo" || data?.layout === "workspace" ? data.layout : null,
    repos: normalizeRepos(data?.repos),
  };
}

/** Run git without ever waiting for a password. */
export function git(cwd: string, ...args: string[]): { ok: boolean; out: string } {
  try {
    const p = Bun.spawnSync(["git", ...args], { cwd, env: { ...process.env, GIT_TERMINAL_PROMPT: "0" } });
    return { ok: p.exitCode === 0, out: p.stdout.toString().trim() };
  } catch {
    return { ok: false, out: "" };
  }
}

/** The git repository that holds the harness folder (monorepo layout); null when there is none. */
export function enclosingRepo(workspace: string): string | null {
  const top = git(workspace, "rev-parse", "--show-toplevel");
  return top.ok && top.out ? resolve(top.out) : null;
}

/**
 * The folder of a repo key. `default` is the repo holding the harness folder in a monorepo, and
 * the module's first repo in a workspace (where the harness folder is its own repo, holding no code).
 */
export function repoFolder(
  workspace: string,
  info: ManifestInfo,
  key: string,
  moduleRepos: string[] = [],
): string | null {
  if (key === "default") {
    if (info.layout !== "workspace") return enclosingRepo(workspace) ?? dirname(workspace);
    const first = moduleRepos.find((k) => k !== "default");
    return first ? repoFolder(workspace, info, first) : null;
  }
  const repo = info.repos.find((r) => r.key === key);
  return repo ? resolve(workspace, repo.path) : null;
}

export interface RepoState {
  folder: string;
  missing: boolean;
  isRepo: boolean;
  branch: string;
  /** Changed files, not counting the harness folder when it lives in this repo. */
  dirty: number;
}

export function repoState(workspace: string, folder: string): RepoState {
  if (!existsSync(folder)) return { folder, missing: true, isRepo: false, branch: "", dirty: 0 };
  const inside = git(folder, "rev-parse", "--is-inside-work-tree");
  if (!inside.ok) return { folder, missing: false, isRepo: false, branch: "", dirty: 0 };
  const branch =
    git(folder, "branch", "--show-current").out ||
    `detached HEAD at ${git(folder, "rev-parse", "--short", "HEAD").out}`;
  const harness = relative(folder, workspace).replace(/\\/g, "/");
  const exclude = harness && !harness.startsWith("..") ? [`:(top,exclude)${harness}`] : [];
  const status = git(folder, "status", "--porcelain", "--", ".", ...exclude);
  return { folder, missing: false, isRepo: true, branch, dirty: status.out ? status.out.split("\n").length : 0 };
}

/** Full SHA of a commit-ish in a repo, or null when it is not a commit there. */
export function resolveCommit(folder: string, rev: string): string | null {
  const r = git(folder, "rev-parse", "--verify", "--quiet", `${rev}^{commit}`);
  return r.ok && r.out ? r.out : null;
}

export const branchExists = (folder: string, branch: string) =>
  git(folder, "rev-parse", "--verify", "--quiet", `refs/heads/${branch}`).ok;

/** Is `sha` in the history of `branch`? */
export const isAncestor = (folder: string, sha: string, branch: string) =>
  git(folder, "merge-base", "--is-ancestor", sha, branch).ok;
