/**
 * Privacy guard — asks the user before the agent reads or edits secrets
 * (.env files, private keys, credential files). Uses the editor's native "ask"
 * permission prompt, so approval happens in the UI, not in the conversation.
 *
 * Registered for: Claude Code PreToolUse (Read|Edit|Write|MultiEdit|Grep|Bash|PowerShell),
 *                 Cursor beforeReadFile and preToolUse (Shell).
 */
import { basename } from "node:path";
import { allow, decide, readInput, shellCommand, targetPaths } from "./lib/hook-io";

const SAFE = /\.(example|sample|template|dist)$/i;
const SENSITIVE = [
  /^\.env(\..+)?$/i, // .env, .env.local, .env.production
  /\.(pem|key|p12|pfx|jks|keystore)$/i, // private keys and keystores
  /^id_(rsa|dsa|ecdsa|ed25519)(\.pub)?$/i, // SSH keys
  /^(credentials|secrets?)(\.[a-z0-9]+)?\.(json|ya?ml|toml|txt)$/i, // credentials.json, secrets.yaml
  /^credentials$/i, // ~/.aws/credentials
  /^\.(netrc|pgpass)$/i,
];

export function isSensitive(path: string): boolean {
  const name = basename(path.replace(/\\/g, "/"));
  return !SAFE.test(name) && SENSITIVE.some((p) => p.test(name));
}

/** Path-like tokens in a shell command that name a sensitive file. */
export function sensitiveInCommand(command: string): string[] {
  return command
    .split(/[\s;|&<>()'"`=]+/)
    .filter(Boolean)
    .filter(isSensitive);
}

if (import.meta.main) {
  const input = await readInput();
  const hits = [...targetPaths(input).filter(isSensitive), ...sensitiveInCommand(shellCommand(input))];
  if (hits.length === 0) allow();

  decide(
    input,
    "ask",
    `harnessclaude privacy: this touches a sensitive file (${[...new Set(hits)].join(", ")}). ` +
      "Approve only if the task needs it. Never echo secret values into chat, logs, or artifacts.",
  );
}
