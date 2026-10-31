import type { ProjectMembership } from '@/shared/api';
import { cells, DataTable, SectionCard, type DataTableColumn } from '@/shared/ui';
import { PROJECT_ROLE_LABELS } from '../lib/labels';

const COLUMNS: DataTableColumn<ProjectMembership>[] = [
  {
    key: 'jiraProjectKey',
    title: 'Jira project key',
    width: 120,
    render: (_, project) => cells.text(project.jiraProjectKey),
  },
  {
    key: 'department',
    title: 'Department',
    width: 180,
    render: (_, project) => cells.text(project.departmentName),
  },
  {
    key: 'roleInProject',
    title: 'Role in Project',
    width: 170,
    render: (_, project) => cells.text(PROJECT_ROLE_LABELS[project.roleInProject]),
  },
];

type ProjectsSectionProps = { projects: readonly ProjectMembership[] };

/** The user's Projects by Jira key; nothing is shown when there are none. */
export function ProjectsSection({ projects }: ProjectsSectionProps) {
  if (!projects.length) return null;
  return (
    <SectionCard title="Projects">
      <DataTable
        label="Projects"
        columns={COLUMNS}
        rows={projects}
        rowKey={(project) => project.projectId}
      />
    </SectionCard>
  );
}
