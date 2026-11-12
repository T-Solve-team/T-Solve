import { DownOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { Avatar, Button, Dropdown, Typography } from 'antd';
import { Link, useNavigate } from 'react-router';
import { useSignOut } from '@/shared/auth';
import { paths } from '@/shared/routing';
import styles from './AppShell.module.css';

type UserMenuProps = { fullName: string };

/** Avatar + name + caret (Figma App Shell header). My Profile and Log out (SRS 1.2.1). */
export function UserMenu({ fullName }: UserMenuProps) {
  const signOut = useSignOut();
  const navigate = useNavigate();

  async function handleLogOut() {
    const navigatedAway = await signOut();
    if (!navigatedAway) await navigate(paths.login);
  }

  return (
    <Dropdown
      trigger={['click']}
      menu={{
        items: [
          {
            key: 'profile',
            icon: <UserOutlined />,
            label: <Link to={paths.profile}>My Profile</Link>,
          },
          {
            key: 'logout',
            icon: <LogoutOutlined />,
            label: 'Log out',
            onClick: () => void handleLogOut(),
          },
        ],
      }}
    >
      <Button type="text" className={styles.userButton}>
        <Avatar size={32} icon={<UserOutlined />} className={styles.avatar} />
        <Typography.Text strong>{fullName}</Typography.Text>
        <DownOutlined className={styles.caret} />
      </Button>
    </Dropdown>
  );
}
