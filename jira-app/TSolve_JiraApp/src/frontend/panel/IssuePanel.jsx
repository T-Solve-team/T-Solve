// 3.3 Issue Panel (jira-app STORY-001, STORY-008, STORY-012): the issue's Solution status (Pending review, Changes
// requested, Rejected, Published) and the way into the Solution Form, including "Edit and resubmit" for the writer's
// ticket in Changes requested.
// No Published-ticket search in Jira (spec change 2026-11-14): search is on the T-Solve web app.
import React, { useCallback, useEffect, useState } from 'react';
import { Button, Inline, SectionMessage, Spinner, Stack, Text, useProductContext } from '@forge/react';
import { events, requestJira, showFlag } from '@forge/bridge';
import { getIssuePanel, getReviewFeedback, openSolutionForm } from '../api/tsolveClient';
import { DONE, readIssue } from '../background/watchDone';
import { doneFlag, SolutionFormModal } from '../form/SolutionFormModal';
import { message } from '../messages';

export const PANEL_LABELS = {
  writeSolution: 'Write Solution',
  continueDraft: 'Continue draft',
  // A Jira App label, not an SRS message (AC-012.1 — user, 2026-11-14).
  editAndResubmit: 'Edit and resubmit',
  // A Jira App text, not an SRS message (AC-008.10 — user, 2026-11-15).
  published: 'Solution approved and published.',
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
      // A change request (MSG64, AC-008.7) or a Reject reason (AC-008.9) on the viewer's ticket.
      const reviewed = state.ticketStatus === 'CHANGES_REQUESTED' || state.ticketStatus === 'REJECTED';
      setFeedback(reviewed ? await getReviewFeedback(issueKey).catch(() => null) : null);
      setFailed(false);
    } catch {
      setFailed(true);
    }
  }, [issueKey]);

  useEffect(() => {
    load();
  }, [load]);

  // The status read on load goes stale when the issue moves (BUG-231): reload when Jira reports a change.
  useEffect(() => {
    const subscription = events.on('JIRA_ISSUE_CHANGED', () => load());
    return () => {
      Promise.resolve(subscription)
        .then((handle) => handle?.unsubscribe?.())
        .catch(() => {});
    };
  }, [load]);

  const openForm = async () => {
    try {
      // The button may be stale (BUG-231): Write Solution only while the issue is still Done (AC-001.4).
      const current = await readIssue(requestJira, issueKey);
      if (!panel.hasDraft && current.category !== DONE) {
        setIssue(current);
        return;
      }
      const opened = await openSolutionForm(issueKey, { summary: current.summary, description: current.description });
      if (opened?.mayOpen) {
        setForm(opened);
      } else {
        load();
      }
    } catch {
      setFailed(true);
    }
  };

  // The writer's ticket in Changes requested: the form opens with the Draft or the ticket's Solution (AC-012.2).
  const openResubmission = async () => {
    try {
      const current = await readIssue(requestJira, issueKey);
      const opened = await openSolutionForm(issueKey, {
        summary: current.summary,
        description: current.description,
        resubmit: true,
      });
      if (opened?.mayOpen) {
        setForm({ ...opened, resubmit: true });
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
      {panel.ticketStatus === 'PUBLISHED' && (
        <SectionMessage appearance="success">
          <Text>{PANEL_LABELS.published}</Text>
        </SectionMessage>
      )}
      {feedback && (
        <SectionMessage appearance="warning">
          <Stack space="space.100">
            <Text>{message(feedback.code, feedback.params)}</Text>
            {panel.mayResubmit && (
              <Inline>
                <Button appearance="primary" onClick={openResubmission}>
                  {PANEL_LABELS.editAndResubmit}
                </Button>
              </Inline>
            )}
          </Stack>
        </SectionMessage>
      )}
      {!feedback && panel.mayResubmit && (
        <Inline>
          <Button appearance="primary" onClick={openResubmission}>
            {PANEL_LABELS.editAndResubmit}
          </Button>
        </Inline>
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
          resubmit={form.resubmit === true}
          feedback={form.resubmit ? feedback : null}
          onDone={(outcome) => {
            setForm(null);
            showFlag(doneFlag(issueKey, outcome));
            load();
          }}
        />
      )}
    </Stack>
  );
}

