// jira:issueViewBackgroundScript: opens the Solution Form by itself when the viewer moves the issue into Done
// (AC-001.1, AC-001.5). Board drags never reach it; the Issue Panel button covers them (AC-001.4, RESEARCH-003).
// Jira neither sends JIRA_ISSUE_CHANGED reliably nor always keeps this script after a transition (BUG-212), so a move
// is caught three ways: the event, on load an issue the viewer moved into Done moments ago, and a status check every
// POLL_MS while the issue view is visible.
import React, { useEffect, useRef, useState } from 'react';
import { useProductContext } from '@forge/react';
import { events, requestJira, showFlag } from '@forge/bridge';
import { openSolutionForm } from '../api/tsolveClient';
import { doneFlag, SolutionFormModal } from '../form/SolutionFormModal';
import { isOwnChange, movedIntoDone, readIssue, recentlyMovedIntoDone } from './watchDone';

/** How often the visible issue view re-reads the status (BUG-212): one small Jira read as the viewer. */
export const POLL_MS = 4000;

export function BackgroundApp() {
  const context = useProductContext();
  const issueKey = context?.extension?.issue?.key;
  const accountId = context?.accountId;
  const lastCategory = useRef(undefined);
  const [open, setOpen] = useState(null);

  useEffect(() => {
    if (!issueKey) {
      return undefined;
    }
    let active = true;
    let asked = false;
    // One ask per load: the event and the load check can both see the same move.
    const offerForm = async (issue) => {
      if (asked) {
        return;
      }
      asked = true;
      const form = await openSolutionForm(issueKey, { summary: issue.summary, description: issue.description });
      if (active && form?.mayOpen) {
        setOpen({ form, summary: issue.summary });
      }
    };
    readIssue(requestJira, issueKey, { changelog: true })
      .then((issue) => {
        lastCategory.current = issue.category;
        return recentlyMovedIntoDone(issue, accountId) ? offerForm(issue) : undefined;
      })
      .catch(() => {
        // No form when the issue or T-Solve cannot be read: the panel button stays the way in (AC-001.4).
      });
    // A move into Done seen by the check: the changelog tells whether it was the viewer's (as for the event).
    const check = async () => {
      if (asked || (typeof document !== 'undefined' && document.hidden)) {
        return;
      }
      try {
        const issue = await readIssue(requestJira, issueKey);
        const previous = lastCategory.current;
        lastCategory.current = issue.category;
        if (movedIntoDone(previous, issue.category)) {
          const read = await readIssue(requestJira, issueKey, { changelog: true });
          if (recentlyMovedIntoDone(read, accountId)) {
            await offerForm(read);
          }
        }
      } catch {
        // Try again at the next check.
      }
    };
    const timer = setInterval(check, POLL_MS);
    const onChange = async (payload) => {
      if (!isOwnChange(payload, accountId)) {
        return;
      }
      try {
        const issue = await readIssue(requestJira, issueKey);
        const previous = lastCategory.current;
        lastCategory.current = issue.category;
        if (movedIntoDone(previous, issue.category)) {
          await offerForm(issue);
        }
      } catch {
        // As above: the panel button stays the way in (AC-001.4).
      }
    };
    const subscription = events.on('JIRA_ISSUE_CHANGED', onChange);
    return () => {
      active = false;
      clearInterval(timer);
      Promise.resolve(subscription)
        .then((handle) => handle?.unsubscribe?.())
        .catch(() => {});
    };
  }, [issueKey, accountId]);

  if (!open) {
    return null;
  }
  return (
    <SolutionFormModal
      issueKey={issueKey}
      summary={open.summary}
      form={open.form}
      onDone={(outcome) => {
        setOpen(null);
        // The background script itself is invisible, so MSG66 or "Saved as draft" is a Jira flag (AC-004.2, AC-005.1).
        showFlag(doneFlag(issueKey, outcome));
      }}
    />
  );
}
