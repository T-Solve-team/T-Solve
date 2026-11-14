/** 2.3 Department Detail — UC-13, UC-14; SRS v3460 layout. */
import { Flex } from 'antd';
import { Link, useParams } from 'react-router';
import { useGetDepartment } from '@/shared/api';
import { paths } from '@/shared/routing';
import { ErrorState, LoadingState, SectionCard } from '@/shared/ui';
import { DepartmentNameForm } from '../components/DepartmentNameForm';

export function DepartmentDetailPage() {
  const { departmentId = '' } = useParams();
  const department = useGetDepartment(departmentId);
  if (department.isPending) return <LoadingState />;
  if (department.isError && !department.data)
    return <ErrorState error={department.error} onRetry={() => void department.refetch()} />;
  return (
    <Flex vertical gap="middle">
      <Link to={paths.workspaceSettings}>Back to Workspace Settings</Link>
      <SectionCard title="Department Detail">
        <DepartmentNameForm department={department.data} />
      </SectionCard>
    </Flex>
  );
}

export default DepartmentDetailPage;
