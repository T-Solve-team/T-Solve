// jira:issueViewBackgroundScript: opens the Solution Form by itself when the viewer moves the issue into Done
// (AC-001.1, AC-001.5). Board drags never reach it; the Issue Panel button covers them (AC-001.4, RESEARCH-003).
import React, { useEffect, useRef, useState } from 'react';
import { useProductContext } from '@forge/react';
import { events, requestJira, showFlag } from '@forge/bridge';
import { openSolutionForm } from '../api/tsolveClient';
import { SolutionFormModal } from '../form/SolutionFormModal';
import { message } from '../messages';
import { isOwnChange, movedIntoDone, readIssue } from './watchDone';

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
    readIssue(requestJira, issueKey).then(
      (issue) => {
        lastCategory.current = issue.category;
      },
      () => {},
    );
    const onChange = async (payload) => {
      if (!isOwnChange(payload, accountId)) {
        return;
      }
      try {
        const issue = await readIssue(requestJira, issueKey);
        const previous = lastCategory.current;
        lastCategory.current = issue.category;
        if (!movedIntoDone(previous, issue.category)) {
          return;
        }
        const form = await openSolutionForm(issueKey, { summary: issue.summary, description: issue.description });
        if (active && form?.mayOpen) {
          setOpen({ form, summary: issue.summary });
        }
      } catch {
        // No form when the issue or T-Solve cannot be read: the panel button stays the way in (AC-001.4).
      }
    };
    const subscription = events.on('JIRA_ISSUE_CHANGED', onChange);
    return () => {
      active = false;
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
      onDone={() => {
        setOpen(null);
        // The background script itself is invisible, so MSG66 is a Jira flag (AC-004.2).
        showFlag({ id: `tsolve-submitted-${issueKey}`, title: message('MSG66'), type: 'success', isAutoDismiss: true });
      }}
    />
  );
}
