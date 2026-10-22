import { useQueryClient } from '@tanstack/react-query';
import { Card, Flex, Select, Typography } from 'antd';
import { useNavigate } from 'react-router';
import { DEV_USERS, getDevUser, resetSession, setDevUser, type DevUserId } from '@/shared/auth';
import styles from './DevUserSwitcher.module.css';

const SIGNED_OUT = 'SIGNED_OUT';
type DevChoice = DevUserId | typeof SIGNED_OUT;

const OPTIONS = [
  ...Object.entries(DEV_USERS).map(([value, label]) => ({ value: value as DevUserId, label })),
  { value: SIGNED_OUT, label: 'Signed out' } as const,
];

/**
 * DEV ONLY, mock mode — pick which fixture user the `/api/v1/me` mock returns (src/mocks/session.ts), to see each
 * role and role combination without a backend (decision FE-11). Never part of a production build.
 */
export function DevUserSwitcher() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  async function handleChange(value: DevChoice) {
    setDevUser(value === SIGNED_OUT ? null : value);
    await resetSession(queryClient);
    await navigate('/');
  }

  return (
    <Card size="small" className={styles.panel}>
      <Flex vertical gap="small">
        <Typography.Text type="secondary">Dev · signed in as</Typography.Text>
        <Select<DevChoice>
          aria-label="Dev user"
          className={styles.select}
          value={getDevUser() ?? SIGNED_OUT}
          onChange={(value) => void handleChange(value)}
          options={OPTIONS}
        />
      </Flex>
    </Card>
  );
}
