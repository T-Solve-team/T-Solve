/** 1.3.1 User List — SRS III.1.3.1, field table 98. */
import { PlusOutlined } from '@ant-design/icons';
import { Button, Flex, Input, Select, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useGetUserDepartments, type UserAccount } from '@/shared/api';
import { isRole, ROLE_LABELS, ROLES } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { paths, useListSearchParams } from '@/shared/routing';
import {
  cells,
  DataTable,
  ErrorState,
  LoadingState,
  StatusTag,
  type DataTableColumn,
} from '@/shared/ui';
import { useUsers } from '../hooks/useUsers';
import styles from './UserListPage.module.css';

const COLUMNS: DataTableColumn<UserAccount>[] = [
  {
    key: 'fullName',
    title: 'Full name',
    width: 170,
    sortable: true,
    render: (_, user) =>
      user.fullName ? cells.link(paths.userDetail(user.id), user.fullName) : null,
  },
  {
    key: 'email',
    title: 'Email',
    width: 225,
    sortable: true,
    render: (_, user) => cells.link(paths.userDetail(user.id), user.email),
  },
  {
    key: 'department',
    title: 'Department',
    width: 120,
    render: (_, user) => cells.text(user.department?.name),
  },
  {
    key: 'roles',
    title: 'Roles',
    width: 200,
    render: (_, user) => cells.text(user.roles.map((role) => ROLE_LABELS[role]).join(', ')),
  },
  {
    key: 'status',
    title: 'Status',
    width: 130,
    render: (_, user) => <StatusTag status={user.status} />,
  },
  {
    key: 'lastSignInAt',
    title: 'Last sign-in',
    width: 160,
    sortable: true,
    render: (_, user) => cells.dateTime(user.lastSignInAt),
  },
];

export function UserListPage() {
  const navigate = useNavigate();
  const list = useListSearchParams({ field: 'fullName', order: 'asc' });
  const q = list.getFilter('q') ?? '';
  const role = list.getFilter('role');
  const status = list.getFilter('status');
  const department = Number(list.getFilter('departmentId'));
  const [searchError, setSearchError] = useState(false);
  const departments = useGetUserDepartments();
  const users = useUsers({
    q: q || undefined,
    role: role && isRole(role) ? role : undefined,
    status: status === 'ACTIVE' || status === 'DEACTIVATED' ? status : undefined,
    departmentId: Number.isSafeInteger(department) && department > 0 ? department : undefined,
    ...list.apiPaging,
  });

  function search(value: string) {
    if (value.trim().length > 100) {
      setSearchError(true);
      return;
    }
    setSearchError(false);
    list.setFilter('q', value.trim());
  }

  return (
    <Flex vertical gap="large">
      <Flex justify="space-between" align="end" gap="middle" wrap>
        <Flex align="end" gap="middle" wrap>
          <Flex vertical gap="small" className={styles.search}>
            <label htmlFor="users-search">Search</label>
            <Input.Search
              key={q}
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
              className={styles.filter}
              value={role ?? ''}
              onChange={(value) => list.setFilter('role', value)}
              options={[
                { value: '', label: 'All roles' },
                ...ROLES.map((value) => ({
                  value,
                  label: value === 'EMPLOYEE' ? 'Employee only' : ROLE_LABELS[value],
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
              value={list.getFilter('departmentId') ?? ''}
              loading={departments.isPending}
              disabled={departments.isPending || departments.isError}
              onChange={(value) => list.setFilter('departmentId', value)}
              options={[
                { value: '', label: 'All Departments' },
                ...(departments.data ?? []).map((item) => ({
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
              className={styles.filter}
              value={status ?? ''}
              onChange={(value) => list.setFilter('status', value)}
              options={[
                { value: '', label: 'All' },
                { value: 'ACTIVE', label: 'Active' },
                { value: 'DEACTIVATED', label: 'Deactivated' },
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
      {departments.isError && (
        <ErrorState error={departments.error} onRetry={() => void departments.refetch()} />
      )}
      <div className={styles.table}>
        {users.isPending ? (
          <LoadingState />
        ) : (
          <DataTable<UserAccount>
            label="Workspace users"
            columns={COLUMNS}
            rows={users.data?.content}
            rowKey={(user) => user.id}
            error={users.isError ? users.error : null}
            onRetry={() => void users.refetch()}
            pagination={{
              page: list.page,
              total: users.data?.page.totalElements ?? 0,
              onChange: list.setPage,
            }}
            sort={list.sort}
            onSortChange={list.setSort}
            scrollX={1005}
          />
        )}
      </div>
    </Flex>
  );
}

export default UserListPage;
