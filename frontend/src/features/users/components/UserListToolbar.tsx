import { PlusOutlined } from '@ant-design/icons';
import { Button, Flex, Input, Select, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Role, UserAccountStatus, type DepartmentRef } from '@/shared/api';
import { ROLE_LABELS, ROLES } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { paths, type useListSearchParams } from '@/shared/routing';
import styles from './UserListToolbar.module.css';

type Props = {
  list: ReturnType<typeof useListSearchParams>;
  q: string;
  role: Role | undefined;
  status: UserAccountStatus | undefined;
  departmentId: string | undefined;
  departments: DepartmentRef[] | undefined;
  departmentsPending: boolean;
  departmentsError: boolean;
};

/** Remounted for each URL q so browser navigation also resets local validation. */
export function UserListToolbar({
  list,
  q,
  role,
  status,
  departmentId,
  departments,
  departmentsPending,
  departmentsError,
}: Props) {
  const navigate = useNavigate();
  const [searchError, setSearchError] = useState(false);
  function search(value: string) {
    if (value.trim().length > 100) {
      setSearchError(true);
      return;
    }
    setSearchError(false);
    list.setFilter('q', value.trim());
  }
  return (
    <Flex justify="space-between" align="end" gap="middle" wrap>
      <Flex align="end" gap="middle" wrap>
        <Flex vertical gap="small" className={styles.search}>
          <label htmlFor="users-search">Search</label>
          <Input.Search
            id="users-search"
            defaultValue={q}
            allowClear
            onSearch={search}
            status={searchError ? 'error' : undefined}
            aria-invalid={searchError}
            aria-describedby={searchError ? 'users-search-error' : undefined}
          />
          {searchError && (
            <Typography.Text type="danger" id="users-search-error" role="alert">
              {msg('MSG02', { field_name: 'Search', max_length: 100 })}
            </Typography.Text>
          )}
        </Flex>
        <Flex vertical gap="small">
          <label htmlFor="users-role">Role</label>
          <Select
            id="users-role"
            virtual={false}
            className={styles.filter}
            value={role ?? ''}
            onChange={(value) => list.setFilter('role', value)}
            options={[
              { value: '', label: 'All roles' },
              ...ROLES.map((value) => ({
                value,
                label: value === Role.EMPLOYEE ? 'Employee only' : ROLE_LABELS[value],
              })),
            ]}
          />
        </Flex>
        <Flex vertical gap="small">
          <label htmlFor="users-department">Department</label>
          <Select
            id="users-department"
            virtual={false}
            className={styles.filter}
            value={
              departments?.some((item) => item.departmentId === departmentId)
                ? String(departmentId)
                : ''
            }
            loading={departmentsPending}
            disabled={departmentsPending || departmentsError}
            onChange={(value) => list.setFilter('departmentId', value)}
            options={[
              { value: '', label: 'All Departments' },
              ...(departments ?? []).map((item) => ({
                value: String(item.departmentId),
                label: item.name,
              })),
            ]}
          />
        </Flex>
        <Flex vertical gap="small">
          <label htmlFor="users-status">Status</label>
          <Select
            id="users-status"
            virtual={false}
            className={styles.filter}
            value={status ?? ''}
            onChange={(value) => list.setFilter('status', value)}
            options={[
              { value: '', label: 'All' },
              { value: UserAccountStatus.ACTIVE, label: 'Active' },
              { value: UserAccountStatus.DEACTIVATED, label: 'Deactivated' },
            ]}
          />
        </Flex>
      </Flex>
      <Button
        type="primary"
        icon={<PlusOutlined aria-hidden />}
        onClick={() => void navigate(paths.userNew)}
      >
        Add user
      </Button>
    </Flex>
  );
}
