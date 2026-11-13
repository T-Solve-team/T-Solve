import { Input } from 'antd';
import { AppForm, EmptyState, ErrorState, FormField, LoadingState } from '@/shared/ui';
import { useProjects } from '../hooks/useProjects';
import { ProjectsTable } from './ProjectsTable';

/** Screen 3.1 for a Project Manager: the read-only Department and its Projects (spec STORY-005). */
export function DepartmentProjects() {
  const projects = useProjects();
  if (projects.isPending) return <LoadingState />;
  if (projects.isError) {
    return <ErrorState error={projects.error} onRetry={() => void projects.refetch()} />;
  }
  const rows = projects.data.projects;
  return (
    <>
      <AppForm>
        <FormField label="Department" htmlFor="department">
          <Input id="department" value={projects.data.department.name} readOnly />
        </FormField>
      </AppForm>
      {rows.length === 0 ? <EmptyState /> : <ProjectsTable rows={rows} />}
    </>
  );
}
