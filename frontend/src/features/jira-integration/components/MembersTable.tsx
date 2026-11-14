import { Button } from 'antd';
import { useState } from 'react';
import type { ProjectMemberResponse, ProjectRole, ProjectSummaryResponse } from '@/shared/api';
import { formatDate } from '@/shared/lib';
import { cells, ConfirmDialog, DataTable, type DataTableColumn } from '@/shared/ui';
import { useRemoveMemberFromProject } from '../hooks/useRemoveMemberFromProject';

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

const memberName = (member: ProjectMemberResponse) => member.fullName ?? member.email;

/**
 * 3.2 Project Detail — Members tab (AC-001.5). Remove on MEMBER rows while the Project is Active, never on the
 * Project Manager's row (AC-001.7, AC-003.6); asks MSG55 first (AC-003.7, AC-003.10).
 */
export function MembersTable({
  project,
  members,
}: {
  project: ProjectSummaryResponse;
  members: readonly ProjectMemberResponse[];
}) {
  const [toRemove, setToRemove] = useState<ProjectMemberResponse | null>(null);
  const remove = useRemoveMemberFromProject(project.id);

  const columns: DataTableColumn<ProjectMemberResponse>[] =
    project.status === 'ACTIVE'
      ? [
          ...COLUMNS,
          {
            key: 'actions',
            title: '',
            render: (_, row) =>
              row.role === 'MEMBER' && (
                <Button
                  type="link"
                  danger
                  aria-label={`Remove ${memberName(row)}`}
                  onClick={() => setToRemove(row)}
                >
                  Remove
                </Button>
              ),
          },
        ]
      : COLUMNS;

  return (
    <>
      <DataTable label="Members" columns={columns} rows={members} rowKey={(row) => row.userId} />
      <ConfirmDialog
        isOpen={toRemove !== null}
        code="MSG55"
        params={{
          user_name: toRemove ? memberName(toRemove) : '',
          project_key: project.jiraProjectKey,
        }}
        isDanger
        okText="Remove"
        isConfirming={remove.isPending}
        onConfirm={() =>
          toRemove &&
          remove.mutate(
            { projectId: project.id, userId: toRemove.userId },
            { onSettled: () => setToRemove(null) },
          )
        }
        onCancel={() => {
          if (!remove.isPending) setToRemove(null);
        }}
      />
    </>
  );
}
