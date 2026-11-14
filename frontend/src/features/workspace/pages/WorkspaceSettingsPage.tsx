/** 2.2 Workspace Settings — company information (UC-11) and New Department (UC-12); SRS v3460 layout. */
import { Button, Flex } from 'antd';
import { useState } from 'react';
import { useGetUserDepartments, useGetWorkspace, type DepartmentRef } from '@/shared/api';
import { cells, DataTable, ErrorState, LoadingState, type DataTableColumn } from '@/shared/ui';
import { NewDepartmentModal } from '../components/NewDepartmentModal';
import { WorkspaceInfoForm } from '../components/WorkspaceInfoForm';

const COLUMNS: DataTableColumn<DepartmentRef>[] = [
  { key: 'name', title: 'Name', render: (_, d) => cells.text(d.name) },
];

export function WorkspaceSettingsPage() {
  const workspace = useGetWorkspace();
  const departments = useGetUserDepartments();
  const [isModalOpen, setIsModalOpen] = useState(false);

  // One loading state for the page until the company information arrives (KAN-120 AC-002.2 expects a single one).
  if (workspace.isPending) return <LoadingState />;

  return (
    <Flex vertical gap="large">
      {workspace.isError ? (
        <ErrorState error={workspace.error} onRetry={() => void workspace.refetch()} />
      ) : (
        <WorkspaceInfoForm workspace={workspace.data} />
      )}
      <Flex justify="flex-end">
        <Button type="primary" onClick={() => setIsModalOpen(true)}>
          New Department
        </Button>
      </Flex>
      {departments.isPending ? (
        <LoadingState />
      ) : (
        <DataTable<DepartmentRef>
          label="Departments"
          columns={COLUMNS}
          rows={departments.data}
          rowKey={(d) => d.departmentId}
          error={departments.isError ? departments.error : null}
          onRetry={() => void departments.refetch()}
        />
      )}
      <NewDepartmentModal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} />
    </Flex>
  );
}

export default WorkspaceSettingsPage;
