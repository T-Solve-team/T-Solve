// When the Solution Form opens by itself (AC-001.1, AC-001.5): only on a change the viewer made that moved the
// issue into the Done status category. JIRA_ISSUE_CHANGED carries no status, so it is read again (RESEARCH-003).

export const DONE = 'done';

/** The change was made by the viewer, so another person looking at the issue gets no form. */
export function isOwnChange(payload, accountId) {
  return Boolean(accountId) && (payload?.changes ?? []).some((change) => change.atlassianId === accountId);
}

/** Into Done from another category; an issue already Done when the page loaded does not open the form. */
export function movedIntoDone(previous, current) {
  return previous !== undefined && previous !== DONE && current === DONE;
}

/**
 * The issue's status category, summary and description as plain text, read as the viewer with requestJira
 * (`fetch`-like; the caller passes `requestJira` from `@forge/bridge`).
 */
export async function readIssue(requestJira, issueKey) {
  const response = await requestJira(
    `/rest/api/3/issue/${encodeURIComponent(issueKey)}?fields=status,summary,description`,
  );
  if (!response.ok) {
    throw new Error(`Issue read failed: ${response.status}`);
  }
  const issue = await response.json();
  const fields = issue.fields ?? {};
  return {
    category: fields.status?.statusCategory?.key,
    summary: fields.summary ?? '',
    description: issueText(fields.description),
  };
}

/** The text of an Atlassian Document Format value: paragraphs and list items on their own lines. */
export function adfText(node) {
  if (!node) {
    return '';
  }
  if (typeof node === 'string') {
    return node;
  }
  if (node.type === 'text') {
    return node.text ?? '';
  }
  if (node.type === 'hardBreak') {
    return '\n';
  }
  const inner = (node.content ?? []).map(adfText).join('');
  const block = ['paragraph', 'heading', 'listItem', 'codeBlock', 'blockquote'].includes(node.type);
  return block ? `${inner}\n` : inner;
}

/** {@link adfText} without the trailing line breaks of the last block. */
export function issueText(description) {
  return adfText(description).replace(/\n+$/, '');
}
