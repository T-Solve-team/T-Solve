import { keepPreviousData, useQueryClient } from '@tanstack/react-query';
import { Flex, Form, Input, Tabs } from 'antd';
import { useEffect, useState } from 'react';
import {
  getSearchProjectMemberCandidatesQueryKey,
  useSearchProjectMemberCandidates,
  type JiraRolePeopleResponse,
  type MemberCandidateResponse,
} from '@/shared/api';
import { cells, DataTable, FormModal, type DataTableColumn } from '@/shared/ui';
import { isJiraRolePeopleOf, useAddMembersToProject } from '../hooks/useAddMembersToProject';
import { JiraRolePeople } from './JiraRolePeople';

const SEARCH_DELAY_MS = 300; // AC-002.10

const COLUMNS: DataTableColumn<MemberCandidateResponse>[] = [
  { key: 'fullName', title: 'Full name', render: (_, row) => cells.text(row.fullName) },
  { key: 'email', title: 'Email', render: (_, row) => cells.text(row.email) },
];

type AddMemberModalProps = { projectId: string; onClose: () => void };

/**
 * 3.2 Project Detail — Add member popup, UC-15: the "From Jira" tab (STORY-011) and the "Search" tab (AC-002.10) share
 * one selection. Mounted only while open, so it always starts empty.
 */
export function AddMemberModal({ projectId, onClose }: AddMemberModalProps) {
  const [form] = Form.useForm();
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const candidates = useSearchProjectMemberCandidates(
    projectId,
    { q },
    { query: { placeholderData: keepPreviousData } },
  );
  const add = useAddMembersToProject(projectId);
  const client = useQueryClient();

  useEffect(() => {
    const timer = setTimeout(() => setQ(search.trim()), SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [search]);

  function handleSubmit() {
    if (selectedIds.length === 0 || add.isPending) return;
    add.mutate(
      { projectId, data: { userIds: selectedIds } },
      {
        onSuccess: onClose,
        // The hook has reloaded the lists by now: keep only the people still offered on either tab.
        onError: () => {
          const listed = client.getQueryData<MemberCandidateResponse[]>(
            getSearchProjectMemberCandidatesQueryKey(projectId, { q }),
          );
          const fromJira = client
            .getQueriesData<JiraRolePeopleResponse>({ predicate: isJiraRolePeopleOf(projectId) })
            .flatMap(([, data]) => data?.people ?? [])
            .filter((person) => person.status === 'ADDABLE');
          if (!listed && fromJira.length === 0) return;
          const offeredIds = new Set([
            ...(listed ?? []).map((row) => row.userId),
            ...fromJira.flatMap((person) => (person.userId ? [person.userId] : [])),
          ]);
          setSelectedIds((current) => current.filter((id) => offeredIds.has(id)));
        },
      },
    );
  }

  return (
    <FormModal
      isOpen
      title="Add member"
      form={form}
      okText="Add"
      isOkDisabled={selectedIds.length === 0}
      isSubmitting={add.isPending}
      onSubmit={handleSubmit}
      onCancel={() => {
        if (!add.isPending) onClose();
      }}
    >
      <Tabs
        defaultActiveKey="jira"
        items={[
          {
            key: 'jira',
            label: 'From Jira',
            children: (
              <JiraRolePeople
                projectId={projectId}
                selectedIds={selectedIds}
                onSelectedIdsChange={setSelectedIds}
              />
            ),
          },
          {
            key: 'search',
            label: 'Search',
            children: (
              <Flex vertical gap="middle">
                <Input
                  aria-label="Search by name or email"
                  placeholder="Search by name or email"
                  allowClear
                  maxLength={100}
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  onPressEnter={(event) => event.preventDefault()}
                />
                <DataTable
                  label="Member candidates"
                  columns={COLUMNS}
                  rows={candidates.data}
                  rowKey={(row) => row.userId}
                  loading={candidates.isPending}
                  error={candidates.error}
                  onRetry={() => void candidates.refetch()}
                  rowSelection={{
                    selectedRowKeys: selectedIds,
                    preserveSelectedRowKeys: true, // the selection survives a new search
                    onChange: (keys) => setSelectedIds(keys.map(String)),
                    getCheckboxProps: (row) => ({
                      'aria-label': `Select ${row.fullName ?? row.email}`,
                    }),
                  }}
                />
              </Flex>
            ),
          },
        ]}
      />
    </FormModal>
  );
}
