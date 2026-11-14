import type { ProjectRow } from '@/shared/api';
import { msg } from '@/shared/messages';
import { cells, DataTable, type DataTableColumn } from '@/shared/ui';

/** Screen 3.1 Projects table (spec AC-005.2–AC-005.5); no paging or client sorting (D26). */
const COLUMNS: DataTableColumn<ProjectRow>[] = [
  { key: 'projectId', title: 'Project ID', width: 320, render: (_, p) => cells.text(p.projectId) },
  {
    key: 'jiraProjectKey',
    title: 'Jira project key',
    width: 160,
    render: (_, p) => cells.text(p.jiraProjectKey),
  },
  {
    key: 'projectManager',
    title: 'Project Manager',
    render: (_, p) => cells.text(p.projectManager?.fullName ?? msg('MSG54')),
  },
  { key: 'memberCount', title: 'Members', width: 100, render: (_, p) => p.memberCount },
  { key: 'status', title: 'Status', width: 120, render: (_, p) => cells.tag(p.status) },
];

type ProjectsTableProps = { rows: ProjectRow[] };

/** The Projects of the caller's Department, in the returned order. */
export function ProjectsTable({ rows }: ProjectsTableProps) {
  return (
    <DataTable<ProjectRow>
      label="Projects"
      columns={COLUMNS}
      rows={rows}
      rowKey={(p) => p.projectId}
    />
  );
}
