/** Confirm page of a Jira account link — STORY-001 AC-001.70, AC-001.71, AC-001.72, AC-001.75. */
import { Button, Card, Flex, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { confirmJiraLink, isApiError, useGetJiraLinkRequest } from '@/shared/api';
import { startSignIn, useSession } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { LoadingState } from '@/shared/ui';
import styles from './LoginPage.module.css';

/** The shared http client adds no CSRF header, so the page sends the value of the `XSRF-TOKEN` cookie itself. */
function csrfHeaders(): Record<string, string> {
  const cookie = document.cookie.split('; ').find((part) => part.startsWith('XSRF-TOKEN='));
  return cookie ? { 'X-XSRF-TOKEN': decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) } : {};
}

const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{43}$/;

export function JiraLinkPage() {
  const { session, isPending } = useSession();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const rawRequestId = searchParams.get('request') ?? '';
  // A malformed id is treated like a missing one and never reaches the GET path.
  const requestId = REQUEST_ID_PATTERN.test(rawRequestId) ? rawRequestId : '';
  const returnTo = location.pathname + location.search;
  const signedIn = session !== null;

  const request = useGetJiraLinkRequest(requestId, {
    query: { enabled: signedIn && requestId !== '', retry: false },
  });
  const [pending, setPending] = useState(false);
  const [failure, setFailure] = useState<'MSG141' | 'MSG06' | null>(null);
  const [linked, setLinked] = useState(false);
  const [usedUp, setUsedUp] = useState(false);
  const sending = useRef(false);
  const signInStarted = useRef(false);

  // A signed-out person goes through sign-in and comes back to this URL.
  useEffect(() => {
    if (isPending || signedIn || signInStarted.current) return;
    signInStarted.current = true;
    startSignIn(returnTo);
  }, [isPending, signedIn, returnTo]);

  async function handleLink() {
    if (sending.current) return; // a double click sends one request
    sending.current = true;
    setPending(true);
    setFailure(null);
    try {
      await confirmJiraLink({ requestId }, { headers: csrfHeaders() });
      // the request is used up: the page keeps the busy state and shows only the result
      setLinked(true);
      return;
    } catch (error) {
      if (isApiError(error) && error.status === 401) {
        startSignIn(returnTo);
        return; // the page is leaving; Link stays busy
      }
      if (isApiError(error) && error.status === 400) setFailure('MSG141');
      else setFailure('MSG06');
      if (isApiError(error) && error.status >= 500) {
        // the request is used up: show MSG06 and keep the Link button hidden
        setUsedUp(true);
        return;
      }
    }
    sending.current = false;
    setPending(false);
  }

  if (isPending || !signedIn) return <LoadingState />;

  const invalid = requestId === '' || request.isError || failure === 'MSG141';
  const loading = !invalid && request.isPending;

  return (
    <Flex justify="center" align="center" className={styles.page}>
      <Card className={styles.card}>
        <Flex vertical align="center" gap="middle">
          {linked ? (
            <Typography.Text role="status">
              {msg('MSG75', { tracker_name: 'Jira' })}
            </Typography.Text>
          ) : loading ? (
            <LoadingState />
          ) : invalid ? (
            <Typography.Text type="danger" role="alert">
              {msg('MSG141')}
            </Typography.Text>
          ) : (
            <>
              <Typography.Paragraph>
                Link Jira user <strong>{request.data?.jiraUserName}</strong> (site{' '}
                <strong>{request.data?.jiraSiteName}</strong>) to your account{' '}
                <strong>
                  {session.user.fullName} ({session.user.email})
                </strong>
                ?
              </Typography.Paragraph>
              <div aria-live="polite">
                {failure ? <Typography.Text type="danger">{msg(failure)}</Typography.Text> : null}
              </div>
              <Flex gap="small">
                {usedUp ? null : (
                  <Button
                    type="primary"
                    disabled={pending}
                    aria-busy={pending}
                    onClick={() => void handleLink()}
                  >
                    Link
                  </Button>
                )}
                <Button onClick={() => void navigate('/')}>Cancel</Button>
              </Flex>
            </>
          )}
        </Flex>
      </Card>
    </Flex>
  );
}

export default JiraLinkPage;
