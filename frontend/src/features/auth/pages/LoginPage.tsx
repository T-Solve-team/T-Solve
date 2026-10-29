/** 1.1.1 Login — SRS III.1.1.1 */
import { Button, Card, Flex, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { startSignIn, useSignInMessage } from '@/shared/auth';
import { msg } from '@/shared/messages';
import styles from './LoginPage.module.css';

export function LoginPage() {
  const [searchParams] = useSearchParams();
  const message = useSignInMessage();
  const [pending, setPending] = useState(false);
  const started = useRef(false);

  // Back from Google can restore this page from the back/forward cache: the button must work again.
  useEffect(() => {
    function handlePageShow(event: PageTransitionEvent) {
      if (!event.persisted) return;
      started.current = false;
      setPending(false);
    }
    window.addEventListener('pageshow', handlePageShow);
    return () => window.removeEventListener('pageshow', handlePageShow);
  }, []);

  function handleSignIn() {
    // A second click while the page is loading must not start a second sign-in (edge 40).
    if (started.current) return;
    started.current = true;
    setPending(true);
    startSignIn(searchParams.get('returnTo') ?? undefined);
  }

  return (
    <Flex justify="center" align="center" className={styles.page}>
      <Card className={styles.card}>
        <Flex vertical align="center" gap="middle">
          <img src="/favicon.svg" alt="T-Solve" className={styles.logo} />
          <Typography.Title level={1} className={styles.title}>
            Sign in to T-Solve
          </Typography.Title>
          {/* The first polite live region of the page; text only, never HTML (edge 39). */}
          <div aria-live="polite" className={styles.message}>
            {message ? (
              <Typography.Text type="danger">{msg(message.code, message.params)}</Typography.Text>
            ) : null}
          </div>
          <Button
            type="primary"
            size="large"
            block
            loading={pending}
            aria-busy={pending}
            onClick={handleSignIn}
          >
            Sign in with Google
          </Button>
        </Flex>
      </Card>
    </Flex>
  );
}

export default LoginPage;
