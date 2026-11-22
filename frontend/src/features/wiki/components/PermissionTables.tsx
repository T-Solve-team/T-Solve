import { LockOutlined } from '@ant-design/icons';
import { Button, Flex, Space, Tooltip, Typography } from 'antd';
import type { WikiSpaceSettings } from '@/shared/api';
import { formatDateTime } from '@/shared/lib';
import { msg } from '@/shared/messages';
import { cells, DataTable, SectionCard, type DataTableColumn } from '@/shared/ui';

type DepartmentRow = WikiSpaceSettings['ownDepartment'] | WikiSpaceSettings['departments'][number];
type UserRow = WikiSpaceSettings['users'][number];
type PermissionTablesProps = {
  settings: WikiSpaceSettings;
  isBusy: boolean;
  onRemove: (permission: { permissionId: string; name: string }) => void;
};

export function PermissionTables({ settings, isBusy, onRemove }: PermissionTablesProps) {
  const departments: DataTableColumn<DepartmentRow>[] = [
    {
      key: 'name',
      title: 'Department',
      width: 220,
      render: (_, row) => (
        <Space>
          {cells.text(row.name)}
          {'locked' in row && (
            <Tooltip title={msg('MSG107')}>
              <LockOutlined aria-label="Locked" />
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      key: 'grantedBy',
      title: 'Granted by',
      width: 200,
      render: (_, row) => cells.text('grantedBy' in row ? row.grantedBy : '—'),
    },
    {
      key: 'grantedAt',
      title: 'Date',
      width: 180,
      render: (_, row) => ('grantedAt' in row ? cells.dateTime(row.grantedAt) : '—'),
    },
    {
      key: 'remove',
      title: 'Remove',
      width: 120,
      render: (_, row) =>
        'permissionId' in row && (
          <Button
            danger
            disabled={isBusy}
            onClick={() => onRemove({ permissionId: row.permissionId, name: row.name })}
          >
            Remove
          </Button>
        ),
    },
  ];
  const userFields = [
    ['fullName', 'Full name', 200],
    ['email', 'Email', 240],
    ['homeDepartment', 'Home Department', 180],
    ['grantedBy', 'Granted by', 200],
    ['grantedAt', 'Date', 180],
  ] as const;
  const users: DataTableColumn<UserRow>[] = userFields.map(([key, title, width]) => ({
    key,
    title,
    width,
    render: (_, row) => {
      const value = key === 'grantedAt' ? formatDateTime(row[key]) : (row[key] ?? '—');
      return (
        <Typography.Text disabled={!row.active} ellipsis={{ tooltip: value }}>
          {value}
        </Typography.Text>
      );
    },
  }));
  users.push({
    key: 'remove',
    title: 'Remove',
    width: 120,
    render: (_, row) => (
      <Button
        danger
        disabled={isBusy}
        onClick={() => onRemove({ permissionId: row.permissionId, name: row.fullName })}
      >
        Remove
      </Button>
    ),
  });
  return (
    <Flex vertical gap="middle">
      <SectionCard title="Departments">
        <DataTable
          label="Departments"
          columns={departments}
          rows={[settings.ownDepartment, ...settings.departments]}
          rowKey={(row) => row.departmentId}
          scrollX={720}
        />
      </SectionCard>
      <SectionCard title="Users">
        <DataTable
          label="Users"
          columns={users}
          rows={settings.users}
          rowKey={(row) => row.permissionId}
          scrollX={1120}
        />
      </SectionCard>
    </Flex>
  );
}
