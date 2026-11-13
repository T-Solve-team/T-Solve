import type { ProjectMemberResponse, ProjectRole } from '@/shared/api';
import { formatDate } from '@/shared/lib';
import { cells, DataTable, type DataTableColumn } from '@/shared/ui';

const ROLE_LABELS: Record<ProjectRole, string> = {
  PROJECT_MANAGER: 'Project Manager',
  MEMBER: 'Member',
};

const COLUMNS: DataTableColumn<ProjectMemberResponse>[] = [
  { key: 'fullName', title: 'Full name', render: (_, row) => cells.text(row.fullName) },
  { key: 'email', title: 'Email', render: (_, row) => cells.text(row.email) },
  { key: 'role', title: 'Role', render: (_, row) => ROLE_LABELS[row.role] },
  { key: 'dateAdded', title: 'Added on', render: (_, row) => formatDate(row.dateAdded) },
];

/** 3.2 Project Detail — Members tab (AC-001.5). No Remove action until KAN-129 (AC-001.7). */
export function MembersTable({ members }: { members: readonly ProjectMemberResponse[] }) {
  return (
    <DataTable label="Members" columns={COLUMNS} rows={members} rowKey={(row) => row.userId} />
  );
}
