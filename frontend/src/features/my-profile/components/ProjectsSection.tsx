import { Typography } from 'antd';
import type { ProjectMembership } from '@/shared/api';
import { msg } from '@/shared/messages';
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

/** The user's Projects by Jira key; the card is always shown, with MSG143 when there are none (AC-003.16). */
export function ProjectsSection({ projects }: ProjectsSectionProps) {
  return (
    <SectionCard title="Projects">
      {projects.length ? (
        <DataTable
          label="Projects"
          columns={COLUMNS}
          rows={projects}
          rowKey={(project) => project.projectId}
        />
      ) : (
        <Typography.Text type="secondary">{msg('MSG143')}</Typography.Text>
      )}
    </SectionCard>
  );
}
