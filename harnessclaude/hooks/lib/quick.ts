/**
 * Quick changes — a small fix the user approved for a few exact files, without a spec round.
 *
 * `sdd.ts quick "<what>" <file>…` records one open quick change in quick.json after the user
 * confirms it in the gate's prompt; the gate then allows writes to exactly those files until
 * `sdd.ts quick done` or the change expires. Shared by the gate, session-context, and sdd.ts.
 */
import { statSync } from "node:fs";
import { join, relative } from "node:path";
import { isInside, normalize, resolveReal } from "./hook-io";
import { fold } from "./workspace";

export const QUICK_FILE = "quick.json";
export const QUICK_MAX_FILES = 5;
export const QUICK_HOURS = 24;

export interface QuickChange {
  what: string;
  files: string[];
  approvedAt: string;
  expiresAt: string;
  by: "user";
}

// Only spaces and tabs separate arguments (a newline starts another command; bash does not split
// on Unicode spaces), and no curly quotes anywhere: PowerShell treats them as quotes, bash does not.
const PLAIN = /^[ \t]*(?:&[ \t]*)?bun(?:\.exe)?[ \t]+["']?[^\s"'`$;&|<>()‘-‟]*sdd\.ts["']?[ \t]+quick(?=[ \t]|$)/;
/**
 * One argument: double-quoted without expansions, single-quoted, or a plain word — no backslash
 * (bash strips it) and no leading `-` (PowerShell splits `-x:path`).
 */
const ARG = /[ \t]+(?:"([^"$`\\‘-‟]*)"|'([^'‘-‟]*)'|((?!-)[\w./:+-]+))(?=[ \t]|$)/y;

/**
 * Arguments after `sdd.ts quick` when the command is that call alone with plain arguments; null
 * otherwise. Only then are the arguments the shell passes the ones the user was shown.
 */
export function quickArgs(command: string): string[] | null {
  const head = command.match(PLAIN);
  if (!head) return null;
  const args: string[] = [];
  let at = head[0].length;
  while (true) {
    ARG.lastIndex = at;
    const m = ARG.exec(command);
    if (!m) break;
    args.push(m[1] ?? m[2] ?? m[3]);
    at = ARG.lastIndex;
  }
  return /^[ \t]*$/.test(command.slice(at)) ? args : null; // anything left (globs, $, pipes, &&, newline) → not plain
}

/** `args` = [what, ...files] → the files as project-relative, `/`-separated paths in their real case. */
export function quickFiles(
  args: string[],
  root: string,
  cwd: string,
  workspace: string,
): { what: string; files: string[] } | { error: string } {
  const [what = "", ...given] = args;
  if (!what.trim() || !given.length) {
    return { error: 'usage: sdd.ts quick "<what>" <file> … (1–5 exact files) · sdd.ts quick done' };
  }
  const project = normalize(root, cwd);
  const ws = normalize(workspace, cwd);
  const files: string[] = [];
  for (const file of given) {
    if (/[*?[\]{}]/.test(file)) return { error: `${file}: list exact files, not globs` };
    const abs = normalize(file, cwd);
    if (!isInside(abs, project) || abs === project) return { error: `${file} is outside the project` };
    if (isInside(abs, ws))
      return { error: `${file} is inside the harness folder — its files never need a quick change` };
    // A folder would let a shell command delete or move everything in it.
    if (statSync(abs, { throwIfNoEntry: false })?.isDirectory())
      return { error: `${file} is a folder — list exact files` };
    const rel = relative(resolveReal(root, cwd), resolveReal(file, cwd)).replace(/\\/g, "/");
    if (!files.some((f) => fold(f) === fold(rel))) files.push(rel);
  }
  if (files.length > QUICK_MAX_FILES) {
    return {
      error: `a quick change opens at most ${QUICK_MAX_FILES} files — more than that goes through the spec (plan skill, Change Mode)`,
    };
  }
  return { what, files };
}

/**
 * The open quick change; null when quick.json is missing, unreadable, malformed, expired, or set to
 * expire later than any quick change can (`sdd.ts quick` never writes that).
 */
export async function readOpenQuick(workspace: string, now = Date.now()): Promise<QuickChange | null> {
  try {
    const quick = await Bun.file(join(workspace, QUICK_FILE)).json();
    const expires = Date.parse(quick?.expiresAt);
    const valid =
      typeof quick?.what === "string" &&
      Array.isArray(quick.files) &&
      quick.files.every((f: unknown) => typeof f === "string") &&
      expires > now &&
      expires <= now + QUICK_HOURS * 3600 * 1000;
    return valid ? quick : null;
  } catch {
    return null;
  }
}

/** Does the quick change list this project-relative path? */
export function quickCovers(quick: QuickChange, rel: string): boolean {
  return quick.files.some((f) => fold(f) === fold(rel));
}
