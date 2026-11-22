import type { ProjectRow } from '@/shared/api';
import { useSession } from '@/shared/auth';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { cells, DataTable, type DataTableColumn } from '@/shared/ui';

/** Screen 3.1 Projects table (spec AC-008.2–AC-008.5); no paging or client sorting (D44). */
const COLUMNS: DataTableColumn<ProjectRow>[] = [
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
  const { session } = useSession();
  return (
    <DataTable<ProjectRow>
      label="Projects"
      columns={[
        {
          key: 'rowNumber',
          title: 'No.',
          width: 64,
          ellipsis: false,
          render: (_, _p, index) => index + 1,
        },
        {
          key: 'jiraProjectKey',
          title: 'Jira project key',
          width: 160,
          render: (_, p) =>
            session && p.projectManager?.userId === session.user.id
              ? cells.link(paths.projectDetail(p.projectId), p.jiraProjectKey)
              : cells.text(p.jiraProjectKey),
        },
        ...COLUMNS,
      ]}
      rows={rows}
      rowKey={(p) => p.projectId}
    />
  );
}
