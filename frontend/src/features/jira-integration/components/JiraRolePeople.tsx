import { Alert, Flex, Select, Typography } from 'antd';
import { useState } from 'react';
import {
  useListProjectJiraRolePeople,
  useListProjectJiraRoles,
  type JiraRolePersonResponse,
} from '@/shared/api';
import { cells, DataTable, type DataTableColumn } from '@/shared/ui';
import { JIRA_TAB_TEXTS } from './jiraTabTexts';

const DEFAULT_ROLE = 'Member';

const COLUMNS: DataTableColumn<JiraRolePersonResponse>[] = [
  { key: 'displayName', title: 'Jira user', render: (_, row) => cells.text(row.displayName) },
  {
    key: 'account',
    title: 'T-Solve account',
    render: (_, row) =>
      row.status === 'ADDABLE' ? (
        cells.text(row.fullName ? `${row.fullName} (${row.email})` : row.email)
      ) : (
        <Typography.Text type="secondary">{JIRA_TAB_TEXTS.reasons[row.status]}</Typography.Text>
      ),
  },
];

type JiraRolePeopleProps = {
  projectId: string;
  /** The user ids ticked in the popup, on either tab (AC-011.6). */
  selectedIds: readonly string[];
  onSelectedIdsChange: (ids: string[]) => void;
};

/** The "From Jira" tab of the Add member popup (project-integration STORY-011). */
export function JiraRolePeople({
  projectId,
  selectedIds,
  onSelectedIdsChange,
}: JiraRolePeopleProps) {
  const roles = useListProjectJiraRoles(projectId);
  const [chosenRoleId, setRoleId] = useState<number | null>(null);
  // AC-011.7: Member when the project has it, otherwise the first role, until the Project Manager picks one.
  const initialRole =
    roles.data?.find((role) => role.name === DEFAULT_ROLE) ?? roles.data?.[0] ?? null;
  const roleId = chosenRoleId ?? initialRole?.id ?? null;

  const people = useListProjectJiraRolePeople(projectId, roleId ?? 0, {
    query: { enabled: roleId !== null },
  });

  if (roles.isError || people.isError) {
    return <Alert type="warning" showIcon message={JIRA_TAB_TEXTS.notAvailable} />;
  }

  const rows = people.data?.people;
  const listedIds = new Set((rows ?? []).flatMap((row) => (row.userId ? [row.userId] : [])));
  return (
    <Flex vertical gap="middle">
      <Select
        aria-label="Jira role"
        loading={roles.isPending}
        value={roleId ?? undefined}
        options={(roles.data ?? []).map((role) => ({ value: role.id, label: role.name }))}
        onChange={(value: number) => setRoleId(value)}
      />
      {people.data && people.data.groups.length > 0 && (
        <Typography.Text type="secondary">
          {JIRA_TAB_TEXTS.groups(people.data.groups)}
        </Typography.Text>
      )}
      <DataTable
        label="People of the Jira role"
        columns={COLUMNS}
        rows={roleId === null ? (roles.data?.length === 0 ? [] : undefined) : rows}
        rowKey={(row) => row.jiraAccountId}
        loading={roles.isPending || (roleId !== null && people.isPending)}
        rowSelection={{
          selectedRowKeys: (rows ?? [])
            .filter((row) => row.userId !== undefined && selectedIds.includes(row.userId))
            .map((row) => row.jiraAccountId),
          onChange: (_keys, selected) => {
            // Keep the people ticked elsewhere (other roles, the Search tab); replace this list's own ticks.
            const kept = selectedIds.filter((id) => !listedIds.has(id));
            onSelectedIdsChange([
              ...kept,
              ...selected.flatMap((row) => (row.userId ? [row.userId] : [])),
            ]);
          },
          getCheckboxProps: (row) => ({
            disabled: row.status !== 'ADDABLE',
            'aria-label': `Select ${row.displayName}`,
          }),
        }}
      />
    </Flex>
  );
}
