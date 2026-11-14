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
 * How long after the move into Done a freshly loaded script still opens the form (BUG-214). Jira reloads the
 * background script after a transition, so the new script finds the issue already Done and no event comes.
 */
export const RECENT_MS = 60_000;

/**
 * The issue's status category, summary and description as plain text, read as the viewer with requestJira
 * (`fetch`-like; the caller passes `requestJira` from `@forge/bridge`). With `changelog`, also when the status
 * category last changed and who made the newest status change (Jira lists the changelog newest first).
 */
export async function readIssue(requestJira, issueKey, { changelog = false } = {}) {
  const fields = changelog ? 'status,summary,description,statuscategorychangedate' : 'status,summary,description';
  const response = await requestJira(
    `/rest/api/3/issue/${encodeURIComponent(issueKey)}?fields=${fields}${changelog ? '&expand=changelog' : ''}`,
  );
  if (!response.ok) {
    throw new Error(`Issue read failed: ${response.status}`);
  }
  const issue = await response.json();
  const read = {
    category: issue.fields?.status?.statusCategory?.key,
    summary: issue.fields?.summary ?? '',
    description: issueText(issue.fields?.description),
  };
  if (!changelog) {
    return read;
  }
  const statusChange = (issue.changelog?.histories ?? []).find((history) =>
    (history.items ?? []).some((item) => item.field === 'status'),
  );
  return {
    ...read,
    statusChangedAt: timeOf(issue.fields?.statuscategorychangedate),
    lastStatusChange: statusChange
      ? { accountId: statusChange.author?.accountId, at: timeOf(statusChange.created) }
      : null,
  };
}

/**
 * The script was (re)loaded just after the viewer moved the issue into Done (BUG-214): the status category changed
 * within {@link RECENT_MS} and the newest status change is the viewer's. An issue Done for longer stays closed
 * (AC-001.5); someone else's move opens nothing, as for the event.
 */
export function recentlyMovedIntoDone(issue, accountId, now = Date.now()) {
  const recent = (time) => Number.isFinite(time) && Math.abs(now - time) <= RECENT_MS;
  return (
    Boolean(accountId) &&
    issue?.category === DONE &&
    recent(issue.statusChangedAt) &&
    issue.lastStatusChange?.accountId === accountId &&
    recent(issue.lastStatusChange.at)
  );
}

function timeOf(value) {
  const time = value ? Date.parse(value) : NaN;
  return Number.isNaN(time) ? undefined : time;
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
