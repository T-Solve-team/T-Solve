// 3.3 Issue Panel (jira-app STORY-001, STORY-008): the issue's Solution status and the way into the Solution Form.
// No Published-ticket search in Jira (spec change 2026-11-14): search is on the T-Solve web app.
import React, { useCallback, useEffect, useState } from 'react';
import { Button, Inline, SectionMessage, Spinner, Stack, Text, useProductContext } from '@forge/react';
import { requestJira, showFlag } from '@forge/bridge';
import { getIssuePanel, getReviewFeedback, openSolutionForm } from '../api/tsolveClient';
import { DONE, readIssue } from '../background/watchDone';
import { SolutionFormModal } from '../form/SolutionFormModal';
import { message } from '../messages';

export const PANEL_LABELS = {
  writeSolution: 'Write Solution',
  continueDraft: 'Continue draft',
};

export function IssuePanel() {
  const context = useProductContext();
  const issueKey = context?.extension?.issue?.key;
  const [panel, setPanel] = useState(null);
  const [issue, setIssue] = useState(null);
  const [feedback, setFeedback] = useState(null);
  const [failed, setFailed] = useState(false);
  const [form, setForm] = useState(null);

  const load = useCallback(async () => {
    if (!issueKey) {
      return;
    }
    try {
      const [state, issueRead] = await Promise.all([getIssuePanel(issueKey), readIssue(requestJira, issueKey)]);
      setPanel(state);
      setIssue(issueRead);
      setFeedback(state.ticketStatus === 'CHANGES_REQUESTED' ? await getReviewFeedback(issueKey).catch(() => null) : null);
      setFailed(false);
    } catch {
      setFailed(true);
    }
  }, [issueKey]);

  useEffect(() => {
    load();
  }, [load]);

  const openForm = async () => {
    try {
      const opened = await openSolutionForm(issueKey, { summary: issue.summary, description: issue.description });
      if (opened?.mayOpen) {
        setForm(opened);
      } else {
        load();
      }
    } catch {
      setFailed(true);
    }
  };

  if (failed) {
    return (
      <SectionMessage appearance="error">
        <Text>{message('MSG06')}</Text>
      </SectionMessage>
    );
  }
  if (!panel) {
    return <Spinner label="loading" />;
  }
  if (!panel.linked) {
    return <Text>{message('MSG62')}</Text>;
  }

  const done = issue?.category === DONE;
  return (
    <Stack space="space.200">
      {panel.ticketStatus === 'PENDING_REVIEW' && (
        <SectionMessage appearance="information">
          <Text>{message('MSG65')}</Text>
        </SectionMessage>
      )}
      {feedback && (
        <SectionMessage appearance="warning">
          <Text>{message(feedback.code, feedback.params)}</Text>
        </SectionMessage>
      )}
      {panel.hasDraft && (
        <SectionMessage appearance="information">
          <Stack space="space.100">
            <Text>{message('MSG77')}</Text>
            <Inline>
              <Button appearance="primary" onClick={openForm}>
                {PANEL_LABELS.continueDraft}
              </Button>
            </Inline>
          </Stack>
        </SectionMessage>
      )}
      {!panel.hasDraft && panel.mayOpenForm && done && (
        <Inline>
          <Button appearance="primary" onClick={openForm}>
            {PANEL_LABELS.writeSolution}
          </Button>
        </Inline>
      )}
      {form && (
        <SolutionFormModal
          issueKey={issueKey}
          summary={issue?.summary ?? ''}
          form={form}
          onDone={() => {
            setForm(null);
            showFlag({ id: `tsolve-submitted-${issueKey}`, title: message('MSG66'), type: 'success', isAutoDismiss: true });
            load();
          }}
        />
      )}
    </Stack>
  );
}

