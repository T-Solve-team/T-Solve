/** 1.3.1 User List — SRS III.1.3.1, field table 98. */
import { Flex } from 'antd';
import { useGetUserDepartments, UserAccountStatus, type UserAccount } from '@/shared/api';
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
  const list = useListSearchParams({ field: 'fullName', order: 'asc' });
  const q = list.getFilter('q') ?? '';
  const rawRole = list.getFilter('role');
  const role = rawRole && isRole(rawRole) ? rawRole : undefined;
  const rawStatus = list.getFilter('status');
  const status =
    rawStatus === UserAccountStatus.ACTIVE || rawStatus === UserAccountStatus.DEACTIVATED
      ? rawStatus
      : undefined;
  const department = Number(list.getFilter('departmentId'));
  const departmentId = Number.isSafeInteger(department) && department > 0 ? department : undefined;
  const departments = useGetUserDepartments();
  const users = useUsers({
    q: q || undefined,
    role,
    status,
    departmentId,
    ...list.apiPaging,
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
          <DataTable<UserAccount>
            label="Workspace users"
            columns={COLUMNS}
            rows={users.data?.content}
            loading={users.isFetching}
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
