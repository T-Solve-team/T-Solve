/**
 * Shared I/O for harnessclaude hooks — runs under Claude Code, Cursor, and Codex.
 *
 * Input:  one JSON object on stdin.
 *   Claude Code: { session_id, cwd, hook_event_name: "PreToolUse", tool_name, tool_input }
 *   Cursor:      { cursor_version, workspace_roots, hook_event_name: "preToolUse" | "beforeReadFile"
 *                  | "sessionStart", tool_name?, tool_input?, file_path?, command? }
 *   Codex:       Claude Code's shape; edits come as tool_name "apply_patch" with the patch in
 *                tool_input.command. Codex hooks are registered with a `--codex` argument.
 * Output: allow = exit 0 with no output. deny/ask = JSON in the shape each editor reads, exit 0.
 * A crash exits non-zero, which every editor treats as non-blocking (fail-open).
 */
import { realpathSync } from "node:fs";
import { basename, dirname, isAbsolute, join, resolve } from "node:path";

export type HookInput = Record<string, any>;

export async function readInput(): Promise<HookInput> {
  try {
    const text = await Bun.stdin.text();
    return text.trim() ? JSON.parse(text) : {};
  } catch {
    return {};
  }
}

export function isCursor(input: HookInput): boolean {
  return "cursor_version" in input || /^[a-z]/.test(input.hook_event_name ?? "");
}

/** Codex hooks are registered with `--codex`: Codex sends no field that tells it apart from Claude Code. */
export function isCodex(): boolean {
  return process.argv.includes("--codex");
}

const isPatch = (input: HookInput) => input.tool_name === "apply_patch";

/** The files a Codex patch adds, updates, deletes, or moves to. */
export function patchPaths(patch: string): string[] {
  return [...patch.matchAll(/^\*\*\* (?:(?:Add|Update|Delete) File|Move to): (.+?)\s*$/gm)].map((m) => m[1]);
}

/** The harnessclaude workspace directory: the parent of the hooks/ directory. */
export function workspaceDir(hooksDir: string): string {
  return dirname(hooksDir);
}

/** The harness folder's name — `harnessclaude` unless the user chose another. */
export function folderName(hooksDir: string): string {
  return basename(workspaceDir(hooksDir));
}

/** Project root: the directory that contains the workspace. */
export function projectRoot(hooksDir: string): string {
  return dirname(workspaceDir(hooksDir));
}

/** File paths the tool call targets, from whichever field the editor uses. */
export function targetPaths(input: HookInput): string[] {
  const sources = [input.tool_input ?? {}, input];
  const keys = ["file_path", "path", "notebook_path", "target_file"];
  const paths: string[] = isPatch(input) ? patchPaths(input.tool_input?.command ?? "") : [];
  for (const source of sources) {
    for (const key of keys) {
      if (typeof source[key] === "string" && source[key]) paths.push(source[key]);
    }
  }
  return [...new Set(paths)];
}

/** The shell command the tool runs; a Codex patch is not one. */
export function shellCommand(input: HookInput): string {
  if (isPatch(input)) return "";
  return input.tool_input?.command ?? input.command ?? "";
}

/**
 * The real path of `path`, which need not exist yet: its longest existing ancestor is resolved by
 * the OS (symlinks, junctions, Windows 8.3 short names like RUNNER~1, macOS /var → /private/var)
 * and the rest is appended. Two spellings of the same file must compare equal, or the gate could
 * be passed by spelling a path differently.
 */
function realPath(abs: string): string {
  const rest: string[] = [];
  let head = abs;
  while (true) {
    try {
      return join(realpathSync.native(head), ...rest);
    } catch {}
    const up = dirname(head);
    if (up === head) return abs;
    rest.unshift(basename(head));
    head = up;
  }
}

/**
 * Windows drops trailing dots and spaces from each path segment (`quick.json.` opens `quick.json`)
 * and opens a segment's default NTFS stream as the segment itself (`quick.json::$DATA`,
 * `hooks::$INDEX_ALLOCATION`), so both are dropped here too; a segment of only dots or spaces is
 * kept as it is.
 */
const win32Segments = (abs: string) =>
  abs
    .split("\\")
    .map((s) => s.replace(/::\$\w+$/, "").replace(/[. ]+$/, "") || s)
    .join("\\");

/** Absolute, real path, as Windows would open it there, in its real case — for showing a path. */
export function resolveReal(path: string, base: string): string {
  const resolved = isAbsolute(path) ? resolve(path) : resolve(base, path);
  return realPath(process.platform === "win32" ? win32Segments(resolved) : resolved);
}

/** Absolute, real, normalized path; case-folded on Windows so comparisons are reliable. */
export function normalize(path: string, base: string): string {
  const abs = resolveReal(path, base);
  return process.platform === "win32" ? abs.toLowerCase() : abs;
}

export function isInside(child: string, parent: string): boolean {
  const sep = process.platform === "win32" ? "\\" : "/";
  return child === parent || child.startsWith(parent.endsWith(sep) ? parent : parent + sep);
}

export function allow(): never {
  process.exit(0);
}

/** Block ("deny") or hand the decision to the user ("ask") with a reason the agent can act on. */
export function decide(input: HookInput, decision: "deny" | "ask", reason: string): never {
  // Codex runs the tool anyway when a hook answers "ask", so the user confirms by running it themselves.
  if (decision === "ask" && isCodex()) {
    decision = "deny";
    reason += ` [Codex cannot ask for confirmation from a hook, so this was blocked. Show the user this message; if they approve, they run the command themselves in their own terminal — do not retry it or work around it.]`;
  }
  if (isCursor(input)) {
    console.log(JSON.stringify({ permission: decision, user_message: reason, agent_message: reason }));
  } else {
    console.log(
      JSON.stringify({
        hookSpecificOutput: {
          hookEventName: input.hook_event_name ?? "PreToolUse",
          permissionDecision: decision,
          permissionDecisionReason: reason,
        },
      }),
    );
  }
  process.exit(0);
}
