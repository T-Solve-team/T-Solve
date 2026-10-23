// npm run messages:report3 — copies Report3's system messages into src/shared/messages/report3-messages.json,
// the reference the catalog test checks every message against (guideline 06 §6). Reads the team docs folder:
// $TSOLVE_DOCS/Report3/System_Messages.md, or the path given as the first argument.
// Run it again when Report3 changes; the catalog test then shows each message whose text changed.
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const docs = process.env.TSOLVE_DOCS;
const source = process.argv[2] ?? (docs && path.join(docs, 'Report3', 'System_Messages.md'));
if (!source) {
  console.error('Set TSOLVE_DOCS to the team docs folder, or pass the path of System_Messages.md.');
  process.exit(1);
}
const target = new URL('../src/shared/messages/report3-messages.json', import.meta.url);

let markdown;
try {
  markdown = await readFile(source, 'utf8');
} catch {
  console.error(`Could not read ${source}.`);
  process.exit(1);
}

// Table rows: | # | Message code | Message type | Context | Content |
const messages = {};
for (const line of markdown.split(/\r?\n/)) {
  const cells = line.split('|').map((cell) => cell.trim());
  const [, number, code, type, context, ...content] = cells;
  if (!/^\d+$/.test(number ?? '') || !/^MSG\d+$/.test(code ?? '')) continue;
  messages[code] = { type, context, text: content.slice(0, -1).join('|').trim() };
}

const count = Object.keys(messages).length;
if (count === 0) {
  console.error(`No message rows found in ${source}.`);
  process.exit(1);
}
await writeFile(target, `${JSON.stringify(messages, null, 2)}\n`);
console.log(`Saved ${count} Report3 messages → src/shared/messages/report3-messages.json`);
