/**
 * Files a shell command writes, read from the command line — enough for the gate to treat
 * `cat > src/a.ts <<EOF`, `sed -i … src/a.ts`, `rm src/a.ts`, or `Set-Content src/a.ts` like a Write.
 * Works for bash and PowerShell command lines (the editors' Bash, PowerShell, and Shell tools).
 *
 * ponytail: best-effort reading, not a shell parser. Misses writes made inside a program
 * (`python -c`, `node -e`, a script file), through variables or `cd`, `find -delete`, `git checkout`,
 * and commands not listed below. Upgrade path: OS-level sandboxing of the agent's shell.
 */

export type Token = { kind: "word" | "op"; text: string };

// Longest first, so `>>` is not read as two `>`.
const OPERATORS = [
  "&>>",
  "<<<",
  "<<-",
  "&&",
  "||",
  ";;",
  "&>",
  ">>",
  ">|",
  ">&",
  "<<",
  "<&",
  "<>",
  "|",
  "&",
  ";",
  ">",
  "<",
  "(",
  ")",
];
const WRITE_REDIRECTS = new Set([">", ">>", ">|", "&>", "&>>", ">&", "<>"]);
const READ_REDIRECTS = new Set(["<", "<<", "<<-", "<<<", "<&"]);
// A backslash escapes only these; elsewhere it is a Windows path separator.
const ESCAPABLE = new Set([..." \t\n\"'`;&|<>()$\\"]);

/** Words and operators of a command line: quotes and escapes honored, comments and heredoc bodies dropped. */
export function tokenize(command: string): Token[] {
  const tokens: Token[] = [];
  const heredocs: string[] = []; // delimiters whose bodies start after the current line
  let word = "";
  let inWord = false; // quotes can start an empty word
  let delimiterNext = false;
  const endWord = () => {
    if (!inWord) return;
    tokens.push({ kind: "word", text: word });
    if (delimiterNext) heredocs.push(word);
    delimiterNext = false;
    word = "";
    inWord = false;
  };

  let i = 0;
  while (i < command.length) {
    const c = command[i];
    if (c === "\n") {
      endWord();
      tokens.push({ kind: "op", text: ";" });
      i++;
      for (const delimiter of heredocs.splice(0)) {
        while (i < command.length) {
          const eol = command.indexOf("\n", i);
          const line = command.slice(i, eol === -1 ? undefined : eol);
          i = eol === -1 ? command.length : eol + 1;
          if (line.trim() === delimiter) break;
        }
      }
    } else if (c === " " || c === "\t" || c === "\r") {
      endWord();
      i++;
    } else if (c === "#" && !inWord) {
      const eol = command.indexOf("\n", i);
      i = eol === -1 ? command.length : eol;
    } else if (c === "'") {
      const close = command.indexOf("'", i + 1);
      word += command.slice(i + 1, close === -1 ? undefined : close);
      inWord = true;
      i = close === -1 ? command.length : close + 1;
    } else if (c === '"') {
      inWord = true;
      i++;
      while (i < command.length && command[i] !== '"') {
        if (command[i] === "\\" && (command[i + 1] === '"' || command[i + 1] === "\\")) i++;
        word += command[i++];
      }
      i++;
    } else if (c === "\\" && ESCAPABLE.has(command[i + 1])) {
      if (command[i + 1] !== "\n") {
        word += command[i + 1];
        inWord = true;
      }
      i += 2;
    } else {
      const op = OPERATORS.find((o) => command.startsWith(o, i));
      if (!op) {
        word += c;
        inWord = true;
        i++;
        continue;
      }
      // `2>file`, `*>file`: the descriptor belongs to the redirect, not to the command's words.
      if (inWord && /^(\d+|\*)$/.test(word) && /[<>]/.test(op[0])) {
        word = "";
        inWord = false;
      }
      endWord();
      tokens.push({ kind: "op", text: op });
      delimiterNext = op === "<<" || op === "<<-";
      i += op.length;
    }
  }
  endWord();
  return tokens;
}

/** Arguments that are not options; options in `takesValue` consume the next argument. */
function operands(args: string[], takesValue: string[] = []): string[] {
  const out: string[] = [];
  for (let i = 0; i < args.length; i++) {
    if (args[i] === "--") return [...out, ...args.slice(i + 1)];
    if (args[i].startsWith("-") && args[i].length > 1) {
      if (takesValue.includes(args[i])) i++;
      continue;
    }
    out.push(args[i]);
  }
  return out;
}

/** `cp -t dir …` / `--target-directory=dir`. */
function targetDirectory(args: string[]): string | undefined {
  const at = args.indexOf("-t");
  if (at !== -1) return args[at + 1];
  return args.find((a) => a.startsWith("--target-directory="))?.slice("--target-directory=".length);
}

/** PowerShell cmdlets (and aliases) that write: which of their arguments name the written files. */
const POWERSHELL: Record<string, "path" | "destination" | "both"> = {
  "set-content": "path",
  "add-content": "path",
  "clear-content": "path",
  "out-file": "path",
  "tee-object": "path",
  "new-item": "path",
  "remove-item": "path",
  "rename-item": "path",
  "copy-item": "destination",
  "move-item": "both",
  ac: "path",
  ni: "path",
  ri: "path",
  del: "path",
  erase: "path",
  ren: "path",
  rni: "path",
  cpi: "destination",
  copy: "destination",
  mi: "both",
  move: "both",
};
const POWERSHELL_SWITCHES = new Set([
  "force",
  "recurse",
  "append",
  "nonewline",
  "whatif",
  "confirm",
  "passthru",
  "noclobber",
]);

function powershellTargets(kind: "path" | "destination" | "both", args: string[]): string[] {
  const named: Record<string, string[]> = {};
  const positional: string[] = [];
  const list = (s: string) => s.split(",").filter(Boolean);
  for (let i = 0; i < args.length; i++) {
    const param = args[i].match(/^-([a-z]+)(?::(.+))?$/i);
    if (!param) {
      positional.push(...list(args[i]));
      continue;
    }
    const key = param[1].toLowerCase();
    if (param[2] !== undefined) named[key] = list(param[2]);
    else if (!POWERSHELL_SWITCHES.has(key) && i + 1 < args.length) named[key] = list(args[++i]);
  }
  const path = named.path ?? named.literalpath ?? named.filepath ?? positional.slice(0, 1);
  const destination = named.destination ?? positional.slice(1, 2);
  return kind === "path" ? path : kind === "destination" ? destination : [...path, ...destination];
}

// Words that start a command line without being the command.
const PREFIXES = new Set([
  "sudo",
  "command",
  "builtin",
  "env",
  "nohup",
  "time",
  "exec",
  "if",
  "then",
  "elif",
  "else",
  "while",
  "until",
  "do",
  "!",
  "{",
]);

/** Files one simple command (no operators) writes. */
function commandTargets(words: string[]): string[] {
  let at = 0;
  while (at < words.length && (PREFIXES.has(words[at]) || /^\w+=/.test(words[at]))) at++;
  if (at >= words.length) return [];
  const name = (words[at].replace(/\\/g, "/").split("/").pop() ?? "").toLowerCase().replace(/\.exe$/, "");
  const args = words.slice(at + 1);
  if (POWERSHELL[name]) return powershellTargets(POWERSHELL[name], args);

  switch (name) {
    case "tee":
    case "rm":
    case "unlink":
    case "touch":
    case "shred":
      return operands(args);
    case "truncate":
      return operands(args, ["-s", "-r"]);
    case "mv": {
      const dir = targetDirectory(args);
      const files = operands(args, ["-t", "-S"]);
      return dir ? [...files, dir] : files; // the sources disappear too
    }
    case "cp":
    case "install":
    case "ln": {
      const dir = targetDirectory(args);
      if (dir) return [dir];
      const files = operands(args, ["-t", "-S", "-m", "-o", "-g"]);
      return files.length > 1 ? files.slice(-1) : [];
    }
    case "sed":
    case "perl": {
      if (!args.some((a) => /^-[a-zA-Z]*i/.test(a) || a.startsWith("--in-place"))) return [];
      const scriptOptions = name === "sed" ? ["-e", "-f", "--expression", "--file"] : ["-e", "-E"];
      const files = operands(args, [...scriptOptions, ...(name === "sed" ? ["-l"] : ["-M", "-I"])]);
      // Without -e/-f the first operand is the script itself.
      return args.some((a) => scriptOptions.includes(a)) ? files : files.slice(1);
    }
    case "dd":
      return args.filter((a) => a.startsWith("of=")).map((a) => a.slice("of=".length));
  }
  return [];
}

/**
 * Every `sdd.ts <subcommand> <args…>` the command line runs, read by words rather than text so a
 * quoted, escaped, or differently cased spelling (`"quick"`, `qu\ick`, `SDD.ts`, `sdd`) is still
 * found. `sub` is lower-cased with backslashes removed; a subcommand from a variable keeps its `$`.
 */
export function sddCalls(command: string): { sub: string; args: string[] }[] {
  const calls: { sub: string; args: string[] }[] = [];
  const scan = (words: string[]) => {
    const at = words.findIndex((w) => /^sdd(\.ts)?[. ]*$/i.test(w.replace(/\\/g, "/").split("/").pop() ?? ""));
    const rest = at === -1 ? [] : words.slice(at + 1);
    const sub = rest.findIndex((w) => !w.startsWith("-"));
    if (sub !== -1) calls.push({ sub: rest[sub].replace(/\\/g, "").toLowerCase(), args: rest.slice(sub + 1) });
  };
  let words: string[] = [];
  for (const { kind, text } of tokenize(command)) {
    if (kind === "word") words.push(text);
    else {
      scan(words);
      words = [];
    }
  }
  scan(words);
  return calls;
}

/**
 * Paths that cannot be resolved (variables, substitutions) or are not files (stdout, the null device).
 * An NTFS stream name (`::$DATA`) is not a variable: normalize() reads it as the file itself.
 */
const unusable = (path: string) =>
  path === "" ||
  path === "-" ||
  /[$`]/.test(path.replace(/::\$\w+/g, "")) ||
  /^nul$/i.test(path) ||
  path.startsWith("/dev/");

/** The files a shell command line writes, as written in the command (relative or absolute). */
export function shellWriteTargets(command: string): string[] {
  const targets: string[] = [];
  let words: string[] = [];
  const tokens = tokenize(command);
  for (let i = 0; i < tokens.length; i++) {
    const { kind, text } = tokens[i];
    if (kind === "word") {
      words.push(text);
      continue;
    }
    const next = tokens[i + 1]?.kind === "word" ? tokens[i + 1].text : undefined;
    if (WRITE_REDIRECTS.has(text) || READ_REDIRECTS.has(text)) {
      if (next === undefined) continue;
      i++;
      // `2>&1`, `>&-` duplicate or close a descriptor; they write no file.
      if (WRITE_REDIRECTS.has(text) && !(text.endsWith("&") && /^(\d+|-)$/.test(next))) targets.push(next);
      continue;
    }
    targets.push(...commandTargets(words));
    words = [];
  }
  targets.push(...commandTargets(words));
  return [...new Set(targets.filter((t) => !unusable(t)))];
}
