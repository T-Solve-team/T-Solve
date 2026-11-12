/** 2.2 Workspace Settings — New Department, UC-12; SRS v3460 layout. */
import { Button, Flex } from 'antd';
import { useState } from 'react';
import { useGetUserDepartments, type DepartmentRef } from '@/shared/api';
import { cells, DataTable, LoadingState, type DataTableColumn } from '@/shared/ui';
import { NewDepartmentModal } from '../components/NewDepartmentModal';

const COLUMNS: DataTableColumn<DepartmentRef>[] = [
  { key: 'name', title: 'Name', render: (_, d) => cells.text(d.name) },
];

export function WorkspaceSettingsPage() {
  const departments = useGetUserDepartments();
  const [isModalOpen, setIsModalOpen] = useState(false);

  return (
    <Flex vertical gap="large">
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
