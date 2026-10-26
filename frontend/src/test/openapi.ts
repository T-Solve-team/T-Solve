// TEST ONLY — reads frontend/openapi/tsolve-api.yaml as text, so contract tests need no YAML library.
// Enough for the block-style YAML this contract uses: a key's block is its line plus every deeper-indented line.
import contractRaw from '../../openapi/tsolve-api.yaml?raw';

export const CONTRACT: string = contractRaw;

const indentOf = (line: string) => line.length - line.trimStart().length;
const unquote = (key: string) => key.trim().replace(/^['"]|['"]$/g, '');

/** The keys at exactly `indent` spaces inside `text`, each with its block (header line included). */
export function childBlocks(text: string, indent: number): Map<string, string> {
  const blocks = new Map<string, string>();
  let name: string | null = null;
  let body: string[] = [];
  const flush = () => {
    if (name !== null) blocks.set(name, body.join('\n'));
    name = null;
    body = [];
  };
  for (const line of text.split(/\r?\n/)) {
    if (line.trim() === '' || line.trimStart().startsWith('#')) {
      if (name !== null) body.push(line);
      continue;
    }
    const depth = indentOf(line);
    if (depth < indent) {
      flush();
      continue;
    }
    if (depth === indent) {
      flush();
      const key = /^\s*([^:\s-][^:]*):/.exec(line)?.[1];
      if (key !== undefined) {
        name = unquote(key);
        body = [line];
      }
      continue;
    }
    if (name !== null) body.push(line);
  }
  flush();
  return blocks;
}

/** Every path of the contract → its block (`  /api/v1/me:` …). */
export function contractPaths(): Map<string, string> {
  return childBlocks(childBlocks(CONTRACT, 0).get('paths') ?? '', 2);
}

/** Every component → its block, keyed `schemas/Name`, `parameters/Name`, `responses/Name`. */
export function contractComponents(): Map<string, string> {
  const result = new Map<string, string>();
  const sections = childBlocks(childBlocks(CONTRACT, 0).get('components') ?? '', 2);
  for (const [section, text] of sections) {
    for (const [name, block] of childBlocks(text, 4)) result.set(`${section}/${name}`, block);
  }
  return result;
}

/** The components a block refers to (`$ref: '#/components/schemas/X'` → `schemas/X`). */
export function refsIn(text: string): string[] {
  return [...text.matchAll(/\$ref:\s*['"]?#\/components\/(\w+)\/([\w.-]+)/g)].map(
    (m) => `${m[1]}/${m[2]}`,
  );
}

/** `texts` plus every component they refer to, directly or through other components. */
export function withReferencedComponents(texts: readonly string[]): string[] {
  const components = contractComponents();
  const seen = new Set<string>();
  const queue = texts.flatMap(refsIn);
  const result = [...texts];
  while (queue.length > 0) {
    const ref = queue.shift() ?? '';
    if (seen.has(ref)) continue;
    seen.add(ref);
    const block = components.get(ref);
    if (block === undefined) continue;
    result.push(block);
    queue.push(...refsIn(block));
  }
  return result;
}

/**
 * The list item (`- name: <name>` and its deeper lines, or a one-line `- { name: <name>, … }`) of the parameter
 * `name` in `text` or in a parameter component it refers to; undefined when there is none.
 */
export function parameterNamed(text: string, name: string): string | undefined {
  const lines = text.split(/\r?\n/);
  const header = new RegExp(`^(\\s*)-\\s+name:\\s*['"]?${name}['"]?\\s*$`);
  const flow = new RegExp(`^\\s*-\\s*\\{[^}]*\\bname:\\s*['"]?${name}['"]?\\s*[,}]`);
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i] ?? '';
    if (flow.test(line)) return line;
    const match = header.exec(line);
    if (!match) continue;
    const dash = match[1]?.length ?? 0;
    const item = [line];
    for (const next of lines.slice(i + 1)) {
      if (next.trim() !== '' && indentOf(next) <= dash) break;
      item.push(next);
    }
    return item.join('\n');
  }
  const components = contractComponents();
  for (const ref of refsIn(text)) {
    if (!ref.startsWith('parameters/')) continue;
    const block = components.get(ref) ?? '';
    if (new RegExp(`^\\s*name:\\s*['"]?${name}['"]?\\s*$`, 'm').test(block)) return block;
  }
  return undefined;
}

/**
 * The names a block declares — mapping keys and `name:` values — without the free text of `description`,
 * `summary`, `title` and `example` (and their folded continuation lines).
 */
export function declaredNames(text: string): string[] {
  const names: string[] = [];
  let skipDeeperThan: number | null = null;
  for (const line of text.split(/\r?\n/)) {
    if (line.trim() === '') continue;
    const depth = indentOf(line);
    if (skipDeeperThan !== null) {
      if (depth > skipDeeperThan) continue;
      skipDeeperThan = null;
    }
    if (/^\s*(?:-\s*)?(?:description|summary|title|example|examples):/.test(line)) {
      skipDeeperThan = depth;
      continue;
    }
    for (const m of line.matchAll(/(?:^|[\s{,-])['"]?([A-Za-z_][\w-]*)['"]?\s*:(?=\s|$)/g)) {
      if (m[1] !== undefined) names.push(m[1]);
    }
    for (const m of line.matchAll(/\bname:\s*['"]?([\w-]+)/g)) {
      if (m[1] !== undefined) names.push(m[1]);
    }
  }
  return names;
}
