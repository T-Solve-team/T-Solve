import { Layout, Typography } from 'antd';
import { Outlet } from 'react-router';
import { useSession } from '@/shared/auth';
import { usePageTitle } from '../router/usePageTitle';
import { SideMenu } from './SideMenu';
import { UserMenu } from './UserMenu';
import styles from './AppShell.module.css';

/** One shell for all roles (Figma "Web/App Shell"); the menu comes from the signed-in user's roles. */
export function AppShell() {
  const { session } = useSession();
  const title = usePageTitle();

  if (!session) return null; // RequireAuth renders the shell only for a signed-in user

  return (
    <Layout className={styles.shell}>
      <Layout.Sider width={252} className={styles.sider}>
        <div className={styles.logo}>
          <span className={styles.logoMark} aria-hidden="true">
            T
          </span>
          <span className={styles.productName}>T-Solve</span>
        </div>
        <SideMenu roles={session.roles} />
      </Layout.Sider>
      <Layout>
        <Layout.Header className={styles.header}>
          <Typography.Title level={1} className={styles.title} ellipsis>
            {title}
          </Typography.Title>
          <UserMenu fullName={session.user.fullName} />
        </Layout.Header>
        <Layout.Content className={styles.content}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  );
}
