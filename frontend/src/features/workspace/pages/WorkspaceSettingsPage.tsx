/** 2.2 Workspace Settings — New Department, UC-12; Show deleted and Restore, UC-15; SRS v3460 layout. */
import { keepPreviousData } from '@tanstack/react-query';
import { Button, Flex, Switch, Typography } from 'antd';
import { useId, useState } from 'react';
import { useListDepartments, type DepartmentSummary } from '@/shared/api';
import { paths } from '@/shared/routing';
import { cells, DataTable, LoadingState, type DataTableColumn } from '@/shared/ui';
import { NewDepartmentModal } from '../components/NewDepartmentModal';
import { RestoreDepartmentDialog } from '../components/RestoreDepartmentDialog';

const isDeleted = (d: DepartmentSummary) => d.status === 'DELETED';

export function WorkspaceSettingsPage() {
  const switchId = useId();
  const [showDeleted, setShowDeleted] = useState(false);
  // The table stays while the other list loads after the switch is turned.
  const departments = useListDepartments(showDeleted ? { includeDeleted: true } : undefined, {
    query: { placeholderData: keepPreviousData },
  });
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [toRestore, setToRestore] = useState<DepartmentSummary | null>(null);

  const columns: DataTableColumn<DepartmentSummary>[] = [
    {
      key: 'name',
      title: 'Name',
      // 2.3 serves Active Departments only (K122-D12).
      render: (_, d) =>
        isDeleted(d)
          ? cells.text(d.name)
          : cells.link(paths.departmentDetail(d.departmentId), d.name),
    },
  ];
  if (showDeleted) {
    columns.push(
      {
        key: 'status',
        title: 'Status',
        render: (_, d) => (isDeleted(d) ? cells.tag('DELETED') : null),
      },
      {
        key: 'actions',
        title: 'Actions',
        render: (_, d) =>
          isDeleted(d) ? <Button onClick={() => setToRestore(d)}>Restore</Button> : null,
      },
    );
  }

  return (
    <Flex vertical gap="large">
      <Flex justify="space-between" align="center">
        <Flex gap="small" align="center">
          <Switch id={switchId} checked={showDeleted} onChange={setShowDeleted} />
          <Typography.Text>
            <label htmlFor={switchId}>Show deleted</label>
          </Typography.Text>
        </Flex>
        <Button type="primary" onClick={() => setIsModalOpen(true)}>
          New Department
        </Button>
      </Flex>
      {departments.isPending ? (
        <LoadingState />
      ) : (
        <DataTable<DepartmentSummary>
          label="Departments"
          columns={columns}
          rows={departments.data}
          rowKey={(d) => d.departmentId}
          error={departments.isError ? departments.error : null}
          onRetry={() => void departments.refetch()}
        />
      )}
      <NewDepartmentModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} />
      {toRestore && (
        <RestoreDepartmentDialog department={toRestore} onClose={() => setToRestore(null)} />
      )}
    </Flex>
  );
}

export default WorkspaceSettingsPage;
