// Commit message rules (DevOps design §1.3). Checked by the commit-msg hook on every commit, and by
// CI on the PR title, because the PR title becomes the squash commit on main.
//
//   type(scope): imperative summary (KAN-123)
//   feat(review): add the reject popup (KAN-42)
//
// type  = feat fix chore docs test refactor ci build perf
// scope = the module or area: review, search, identity, repo, github, infra, ci, deps, ...
// The Jira key at the end links the commit and the PR to the issue (GitHub for Jira).
// Merge, revert and fixup commits made by git itself are not checked.

const JIRA_KEY = 'KAN';
const KEY_AT_END = new RegExp(`\\(${JIRA_KEY}-[1-9]\\d*\\)$`);

export default {
  extends: ['@commitlint/config-conventional'],
  plugins: [
    {
      rules: {
        'jira-key-at-end': ({ header }) => [
          KEY_AT_END.test(header ?? ''),
          `end the first line with the Jira issue key, e.g. "feat(review): add the reject popup (${JIRA_KEY}-42)"`,
        ],
      },
    },
  ],
  rules: {
    'type-enum': [2, 'always', ['feat', 'fix', 'chore', 'docs', 'test', 'refactor', 'ci', 'build', 'perf']],
    'scope-empty': [2, 'never'],
    'jira-key-at-end': [2, 'always'],
  },
};
