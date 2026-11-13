import { keepPreviousData } from '@tanstack/react-query';
import { Flex, Form, Input } from 'antd';
import { useEffect, useState } from 'react';
import { useSearchProjectMemberCandidates, type MemberCandidateResponse } from '@/shared/api';
import { cells, DataTable, FormModal, type DataTableColumn } from '@/shared/ui';
import { useAddMembersToProject } from '../hooks/useAddMembersToProject';

const SEARCH_DELAY_MS = 300; // AC-002.10

const COLUMNS: DataTableColumn<MemberCandidateResponse>[] = [
  { key: 'fullName', title: 'Full name', render: (_, row) => cells.text(row.fullName) },
  { key: 'email', title: 'Email', render: (_, row) => cells.text(row.email) },
];

type AddMemberModalProps = { projectId: string; onClose: () => void };

/** 3.2 Project Detail — Add member popup, UC-15. Mounted only while open, so it always starts empty. */
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

  useEffect(() => {
    const timer = setTimeout(() => setQ(search.trim()), SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [search]);

  function handleSubmit() {
    if (selectedIds.length === 0 || add.isPending) return;
    add.mutate({ projectId, data: { userIds: selectedIds } }, { onSuccess: onClose });
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
            getCheckboxProps: (row) => ({ 'aria-label': `Select ${row.fullName ?? row.email}` }),
          }}
        />
      </Flex>
    </FormModal>
  );
}
