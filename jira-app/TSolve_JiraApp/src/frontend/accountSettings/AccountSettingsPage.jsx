// The "T-Solve account" personal settings page (jira-app STORY-014): links the Jira account to a T-Solve Account
// through identity's confirm page, and shows the linked Account.
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Inline, SectionMessage, Spinner, Stack, Text } from '@forge/react';
import { router } from '@forge/bridge';
import { getAccountLink, startAccountLink } from '../api/tsolveClient';
import { message } from '../messages';

export const SETTINGS_LABELS = {
  // Jira App labels, not SRS messages (AC-014.3, AC-014.4 — user, 2026-11-17).
  signIn: 'Sign in to T-Solve',
  linkAnother: 'Link another account',
};

/** The page re-reads the link this often after the click (AC-014.8). */
export const POLL_INTERVAL_MS = 5000;
/** ...and stops when the link request has expired (identity AC-001.69). */
export const POLL_LIMIT_MS = 10 * 60 * 1000;

// A new link: LINKED, and not the link the page knew at the click (AC-014.8). Comparing with the earlier link time
// rather than the browser's clock keeps a clock difference between the browser and the server out of it.
function isNewLink(answer, before) {
  return answer?.status === 'LINKED' && !(before?.status === 'LINKED' && before.linkedAt === answer.linkedAt);
}

export function AccountSettingsPage() {
  const [link, setLink] = useState(null);
  const [failed, setFailed] = useState(false);
  const [waiting, setWaiting] = useState(false);
  const [error, setError] = useState(null);
  const timer = useRef(null);

  const stopWaiting = useCallback(() => {
    if (timer.current) {
      clearInterval(timer.current);
      timer.current = null;
    }
    setWaiting(false);
  }, []);

  useEffect(() => {
    getAccountLink()
      .then((answer) => setLink(answer))
      .catch(() => setFailed(true));
    return () => {
      if (timer.current) {
        clearInterval(timer.current);
      }
    };
  }, []);

  const start = async () => {
    setError(null);
    let url;
    try {
      url = await startAccountLink();
    } catch (failure) {
      // MSG124, MSG08 or MSG06; the button stays (AC-014.9).
      setError(failure?.code ?? 'MSG06');
      return;
    }
    const before = link;
    const startedAt = Date.now();
    setWaiting(true);
    router.open(url).catch(() => {});
    timer.current = setInterval(async () => {
      if (Date.now() - startedAt >= POLL_LIMIT_MS) {
        stopWaiting();
        return;
      }
      try {
        const answer = await getAccountLink();
        if (isNewLink(answer, before)) {
          setLink(answer);
          stopWaiting();
        }
      } catch {
        // a failed check is retried at the next tick
      }
    }, POLL_INTERVAL_MS);
  };

  if (failed) {
    return (
      <SectionMessage appearance="error">
        <Text>{message('MSG06')}</Text>
      </SectionMessage>
    );
  }
  if (!link) {
    return <Spinner label="loading" />;
  }
  if (link.status === 'SITE_NOT_CONNECTED') {
    return <Text>{message('MSG124')}</Text>;
  }

  const linked = link.status === 'LINKED';
  return (
    <Stack space="space.200">
      <Text>{linked ? message('LINKED', { full_name: link.fullName, email: link.email }) : message('NOT_LINKED')}</Text>
      {error && (
        <SectionMessage appearance="error">
          <Text>{message(error)}</Text>
        </SectionMessage>
      )}
      {waiting ? (
        <SectionMessage appearance="information">
          <Text>{message('MSG74_PAGE')}</Text>
        </SectionMessage>
      ) : (
        <Inline>
          <Button appearance={linked ? 'default' : 'primary'} onClick={start}>
            {linked ? SETTINGS_LABELS.linkAnother : SETTINGS_LABELS.signIn}
          </Button>
        </Inline>
      )}
    </Stack>
  );
}
