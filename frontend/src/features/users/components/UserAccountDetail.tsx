import { Descriptions, Flex, Typography } from 'antd';
import { Link } from 'react-router';
import { ROLE_LABELS } from '@/shared/auth';
import { formatDateTime } from '@/shared/lib';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { ErrorState, LoadingState, SectionCard, StatusTag } from '@/shared/ui';
import { useUserDetail } from '../hooks/useUserDetail';
import { UserStatusActions } from './UserStatusActions';

type UserAccountDetailProps = { userId: string };

export function UserAccountDetail({ userId }: UserAccountDetailProps) {
  const detail = useUserDetail(userId);
  if (detail.isPending) return <LoadingState />;
  if (detail.isError)
    return <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />;
  const user = detail.data;
  return (
    <Flex vertical gap="middle">
      <Flex justify="space-between" align="center" wrap>
        <Link to={paths.userList}>Back to User List</Link>
        <UserStatusActions user={user} />
      </Flex>
      <SectionCard title="User Detail">
        <Descriptions
          column={1}
          items={[
            { key: 'fullName', label: 'Full name', children: user.fullName ?? '' },
            { key: 'email', label: 'Email', children: user.email },
            { key: 'department', label: 'Department', children: user.department?.name ?? '' },
            {
              key: 'roles',
              label: 'Roles',
              children: user.roles.map((role) => ROLE_LABELS[role]).join(', '),
            },
            { key: 'status', label: 'Status', children: <StatusTag status={user.status} /> },
            {
              key: 'lastSignIn',
              label: 'Last sign-in',
              children: user.lastSignInAt ? formatDateTime(user.lastSignInAt) : '',
            },
          ]}
        />
      </SectionCard>
      <SectionCard title="Projects">
        {user.projects.length ? (
          <ul>
            {user.projects.map((project) => (
              <li key={project.projectId}>{project.name}</li>
            ))}
          </ul>
        ) : (
          <Typography.Text>{msg('MSG04')}</Typography.Text>
        )}
      </SectionCard>
      <SectionCard title="Managed Spaces">
        {user.managedSpaces.length ? (
          <ul>
            {user.managedSpaces.map((space) => (
              <li key={space.spaceId}>{space.name}</li>
            ))}
          </ul>
        ) : (
          <Typography.Text>{msg('MSG04')}</Typography.Text>
        )}
      </SectionCard>
    </Flex>
  );
}
