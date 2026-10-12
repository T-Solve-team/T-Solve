// Pre-commit formatting and linting of staged files (guideline 11 §5), run by .husky/pre-commit.
// Frontend: ESLint --fix on code files, then Prettier on every file Prettier knows. Both run inside
// frontend/ with its own configs and ignore files (scripts/hooks/frontend-tool.mjs). Fixed files are
// added back to the commit automatically. Generated API code is never touched.
// Backend: Spotless is added here once the backend has the Spotless plugin.

const GENERATED = '/frontend/src/shared/api/generated/';
const CODE = /\.(ts|tsx|js|mjs|cjs)$/;

const run = (tool, flags, files) =>
  `node scripts/hooks/frontend-tool.mjs ${tool} ${flags} -- ${files.map((f) => `"${f}"`).join(' ')}`;

export default {
  'frontend/**': (staged) => {
    const files = staged.filter((f) => !f.replaceAll('\\', '/').includes(GENERATED));
    const code = files.filter((f) => CODE.test(f));
    const tasks = [];
    if (code.length) tasks.push(run('eslint', '--fix --no-warn-ignored', code));
    if (files.length) tasks.push(run('prettier', '--write --ignore-unknown', files));
    return tasks;
  },
};
