import { useQueryClient } from '@tanstack/react-query';
import { Card, Flex, Select, Typography } from 'antd';
import { useNavigate } from 'react-router';
import { resetSiteState } from '@/shared/api';
import {
  DEV_SITES,
  DEV_USERS,
  getDevSite,
  getDevUser,
  resetSession,
  setDevSite,
  setDevUser,
  type DevSiteId,
  type DevUserId,
} from '@/shared/auth';
import styles from './DevUserSwitcher.module.css';

const SIGNED_OUT = 'SIGNED_OUT';
type DevChoice = DevUserId | DevSiteId | typeof SIGNED_OUT;

const OPTIONS = [
  ...Object.entries(DEV_USERS).map(([value, label]) => ({ value: value as DevUserId, label })),
  { value: SIGNED_OUT, label: 'Signed out' } as const,
  ...Object.entries(DEV_SITES).map(([value, label]) => ({ value: value as DevSiteId, label })),
];

function isSite(value: DevChoice): value is DevSiteId {
  return Object.hasOwn(DEV_SITES, value);
}

/**
 * DEV ONLY, mock mode — pick which fixture user the `/api/v1/me` mock returns (src/mocks/session.ts), to see each
 * role and role combination without a backend (decision FE-11), or a site that does not exist / a suspended
 * Workspace (MSG119, MSG120). Never part of a production build.
 */
export function DevUserSwitcher() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  async function handleChange(value: DevChoice) {
    if (isSite(value)) setDevSite(value);
    else setDevUser(value === SIGNED_OUT ? null : value);
    // Every pick starts from a normal site: the site page of the previous pick is left.
    resetSiteState();
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
          value={getDevSite() ?? getDevUser() ?? SIGNED_OUT}
          onChange={(value) => void handleChange(value)}
          options={OPTIONS}
          virtual={false}
        />
      </Flex>
    </Card>
  );
}
