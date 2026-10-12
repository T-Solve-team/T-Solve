// Runs Prettier or ESLint from frontend/node_modules, inside frontend/, on the files lint-staged
// passes. Running inside frontend/ means the frontend's own configs and ignore files apply exactly as
// in `npm run format:check` and `npm run lint`. Usage (from lint-staged.config.mjs):
//   node scripts/hooks/frontend-tool.mjs <prettier|eslint> [flags...] -- <files...>
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const FRONTEND = path.join(ROOT, 'frontend');
const ENTRY = {
  prettier: 'node_modules/prettier/bin/prettier.cjs',
  eslint: 'node_modules/eslint/bin/eslint.js',
};

const [tool, ...rest] = process.argv.slice(2);
const split = rest.indexOf('--');
if (!ENTRY[tool] || split === -1) {
  console.error('Usage: node scripts/hooks/frontend-tool.mjs <prettier|eslint> [flags...] -- <files...>');
  process.exit(2);
}
const flags = rest.slice(0, split);
const files = rest.slice(split + 1).map((f) => path.relative(FRONTEND, path.resolve(ROOT, f)));

const entry = path.join(FRONTEND, ENTRY[tool]);
if (!existsSync(entry)) {
  console.error(`${tool} is not installed in frontend/. Run "npm ci" inside frontend/ once, then commit again.`);
  process.exit(1);
}

const result = spawnSync(process.execPath, [entry, ...flags, ...files], { cwd: FRONTEND, stdio: 'inherit' });
process.exit(result.status ?? 1);
