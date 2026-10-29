import { useQueryClient } from '@tanstack/react-query';
import { Button, Card, Divider, Flex, Typography } from 'antd';
import { useNavigate, useSearchParams } from 'react-router';
import { resetSiteState } from '@/shared/api';
import {
  DEV_USERS,
  isSameSitePath,
  resetSession,
  setDevSite,
  setDevUser,
  type DevUserId,
} from '@/shared/auth';
import styles from './MockSignInPage.module.css';

const OUTCOMES = [
  { label: 'Cancel', to: '/login?error=MSG06' },
  { label: 'Unknown account', to: '/login?error=MSG09' },
  { label: 'Deactivated account', to: '/login?error=MSG10' },
  { label: 'Wrong Google domain', to: '/login?error=MSG122&google_domain=company.example' },
  { label: 'Expired link', to: '/login?error=MSG141' },
] as const;

const DEV_USER_IDS = Object.keys(DEV_USERS) as DevUserId[];

/**
 * DEV ONLY, mock mode (Q5) — stands in for the central Google sign-in: pick a fixture user or one of the failure
 * outcomes the identity callback can send back to Login. Registered only under `import.meta.env.DEV`.
 */
export function MockSignInPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const returnTo = searchParams.get('returnTo');

  async function pickUser(id: DevUserId) {
    setDevUser(id);
    resetSiteState();
    await resetSession(queryClient);
    // "/" sends the user to the landing page of their roles.
    await navigate(returnTo !== null && isSameSitePath(returnTo) ? returnTo : '/');
  }

  async function suspendWorkspace() {
    setDevSite('suspended-workspace');
    resetSiteState();
    await resetSession(queryClient);
    await navigate('/login?error=MSG120');
  }

  return (
    <Flex justify="center" align="center" className={styles.page}>
      <Card className={styles.card} title="Mock sign-in (dev only)">
        <Flex vertical gap="small">
          <Typography.Text type="secondary">Sign in as</Typography.Text>
          {DEV_USER_IDS.map((id) => (
            <Button key={id} block onClick={() => void pickUser(id)}>
              {DEV_USERS[id]}
            </Button>
          ))}
          <Divider />
          <Typography.Text type="secondary">Or end the sign-in with</Typography.Text>
          {OUTCOMES.map(({ label, to }) => (
            <Button key={label} block onClick={() => void navigate(to)}>
              {label}
            </Button>
          ))}
          <Button block onClick={() => void suspendWorkspace()}>
            Suspended Workspace
          </Button>
        </Flex>
      </Card>
    </Flex>
  );
}
