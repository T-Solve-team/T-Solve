import { Button, Card, Descriptions } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ROLE_LABELS, ROLES, useSignOut, type Session } from '@/shared/auth';
import { paths } from '@/shared/routing';

type AccountSectionProps = { session: Session };

/** Account card of My Profile (SRS 1.2.1): who you are, your Roles and Departments, and Log out. */
export function AccountSection({ session }: AccountSectionProps) {
  const signOut = useSignOut();
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleLogOut() {
    if (loggingOut) return;
    setLoggingOut(true);
    await signOut();
    await navigate(paths.login, { replace: true });
  }

  const roles = ROLES.filter((role) => session.roles.includes(role))
    .map((role) => ROLE_LABELS[role])
    .join(', ');
  const items = [
    { key: 'roles', label: 'Roles', children: roles },
    ...(session.roles.includes('PROJECT_MANAGER') && session.department
      ? [{ key: 'department', label: 'Department', children: session.department.name }]
      : []),
    ...(session.departments.length
      ? [
          {
            key: 'departments',
            label: 'Departments',
            children: session.departments.map((department) => department.name).join(', '),
          },
        ]
      : []),
  ];

  return (
    <Card
      title="Account"
      variant="borderless"
      role="region"
      aria-label="Account"
      extra={
        <Button loading={loggingOut} onClick={() => void handleLogOut()}>
          Log out
        </Button>
      }
    >
      <Descriptions
        column={1}
        items={[
          { key: 'fullName', label: 'Full name', children: session.user.fullName },
          { key: 'email', label: 'Email', children: session.user.email },
          ...items,
        ]}
      />
    </Card>
  );
}
