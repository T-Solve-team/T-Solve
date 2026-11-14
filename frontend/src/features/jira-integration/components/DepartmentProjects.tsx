import { Button, Flex, Input } from 'antd';
import { useState } from 'react';
import { AppForm, EmptyState, ErrorState, FormField, LoadingState } from '@/shared/ui';
import { useProjects } from '../hooks/useProjects';
import { AddProjectModal } from './AddProjectModal';
import { ProjectsTable } from './ProjectsTable';

/** Screen 3.1 for a Project Manager: the read-only Department, its Projects and Add project (STORY-005, STORY-006). */
export function DepartmentProjects() {
  const projects = useProjects();
  const [isAddOpen, setAddOpen] = useState(false);
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
      <Flex justify="end">
        <Button type="primary" onClick={() => setAddOpen(true)}>
          Add project
        </Button>
      </Flex>
      {rows.length === 0 ? <EmptyState /> : <ProjectsTable rows={rows} />}
      <AddProjectModal isOpen={isAddOpen} onClose={() => setAddOpen(false)} />
    </>
  );
}
