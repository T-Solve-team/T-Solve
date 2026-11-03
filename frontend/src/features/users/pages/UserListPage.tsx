/** 1.3.1 User List — SRS III.1.3.1, field table 98. */
import { Flex } from 'antd';
import { useEffect } from 'react';
import { useGetUserDepartments, UserAccountStatus, type UserListItem } from '@/shared/api';
import { isRole, ROLE_LABELS } from '@/shared/auth';
import { paths, useListSearchParams } from '@/shared/routing';
import {
  cells,
  DataTable,
  ErrorState,
  LoadingState,
  StatusTag,
  type DataTableColumn,
} from '@/shared/ui';
import { UserListToolbar } from '../components/UserListToolbar';
import { useUsers } from '../hooks/useUsers';
import styles from './UserListPage.module.css';

const COLUMNS: DataTableColumn<UserListItem>[] = [
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
];

export function UserListPage() {
  const list = useListSearchParams({ field: 'fullName', order: 'asc' });
  const legacySignInSort = list.sort.field === 'lastSignInAt';
  const setSort = list.setSort;
  useEffect(() => {
    if (legacySignInSort) setSort({ field: 'fullName', order: 'asc' });
  }, [legacySignInSort, setSort]);
  const q = list.getFilter('q') ?? '';
  const rawRole = list.getFilter('role');
  const role = rawRole && isRole(rawRole) ? rawRole : undefined;
  const rawStatus = list.getFilter('status');
  const status =
    rawStatus === UserAccountStatus.ACTIVE || rawStatus === UserAccountStatus.DEACTIVATED
      ? rawStatus
      : undefined;
  const departmentId = list.getFilter('departmentId') || undefined;
  const departments = useGetUserDepartments();
  const users = useUsers({
    q: q || undefined,
    role,
    status,
    departmentId,
    ...list.apiPaging,
    ...(legacySignInSort ? { page: 0, sort: ['fullName,asc'] } : {}),
  });

  return (
    <Flex vertical gap="large">
      <UserListToolbar
        key={q}
        list={list}
        q={q}
        role={role}
        status={status}
        departmentId={departmentId}
        departments={departments.data}
        departmentsPending={departments.isPending}
        departmentsError={departments.isError}
      />
      {departments.isError && (
        <ErrorState error={departments.error} onRetry={() => void departments.refetch()} />
      )}
      <div className={styles.table}>
        {users.isPending ? (
          <LoadingState />
        ) : (
          <DataTable<UserListItem>
            label="Workspace users"
            columns={COLUMNS}
            rows={users.data?.content}
            loading={users.isFetching}
            rowKey={(user) => user.id}
            error={users.isError ? users.error : null}
            onRetry={() => void users.refetch()}
            pagination={{
              page: legacySignInSort ? 1 : list.page,
              total: users.data?.page.totalElements ?? 0,
              onChange: list.setPage,
            }}
            sort={legacySignInSort ? { field: 'fullName', order: 'asc' } : list.sort}
            onSortChange={list.setSort}
            scrollX={845}
          />
        )}
      </div>
    </Flex>
  );
}

export default UserListPage;
