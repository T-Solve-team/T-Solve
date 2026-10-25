/** 7.1 Review Queue — SRS III.7.1, Figma "7.1 Review Queue" (decisions FE-10, FE-34). */
import { Flex } from 'antd';
import { useState } from 'react';
import { useSession } from '@/shared/auth';
import { useListSearchParams } from '@/shared/routing';
import { ConfirmDialog } from '@/shared/ui';
import { RejectTicketModal } from '../components/RejectTicketModal';
import { RequestChangesModal } from '../components/RequestChangesModal';
import { ReviewQueueTable } from '../components/ReviewQueueTable';
import { ReviewQueueToolbar } from '../components/ReviewQueueToolbar';
import { useQueueTicketIds } from '../hooks/useQueueTicketIds';
import { useApproveSelectedTickets } from '../hooks/useReviewDecisions';
import { useReviewQueue } from '../hooks/useReviewQueue';
import styles from './ReviewQueuePage.module.css';

type OpenDialog = 'confirmApprove' | 'reject' | 'requestChanges' | null;

/** "?departmentId=<uuid>" → the id; missing → all Departments. */
function toDepartmentId(value: string | null): string | undefined {
  return value || undefined;
}

export function ReviewQueuePage() {
  const { session } = useSession();
  const list = useListSearchParams({ field: 'reviewDueDate', order: 'asc' }); // SRS: earliest due first
  const departmentId = toDepartmentId(list.getFilter('departmentId'));
  const queue = useReviewQueue({ departmentId, ...list.apiPaging });
  const loadAllTicketIds = useQueueTicketIds(departmentId);
  const approve = useApproveSelectedTickets();

  // Selected tickets, across pages. Local state: it belongs to this screen only (guideline 05 C8).
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [openDialog, setOpenDialog] = useState<OpenDialog>(null);

  function finishDecision() {
    setOpenDialog(null);
    setSelectedIds([]);
  }

  function handleDepartmentChange(nextDepartmentId: string | undefined) {
    list.setFilter('departmentId', nextDepartmentId ?? null);
    setSelectedIds([]); // a selection belongs to the filter it was made in
  }

  async function handleSelectAll(isChecked: boolean) {
    setSelectedIds(isChecked ? await loadAllTicketIds() : []);
  }

  function runApprove() {
    approve.mutate({ data: { ticketIds: selectedIds } }, { onSuccess: finishDecision });
  }

  // SRS 7.1: approving two or more tickets asks first (MSG19); one ticket is approved at once.
  function handleApprove() {
    if (selectedIds.length >= 2) setOpenDialog('confirmApprove');
    else runApprove();
  }

  return (
    <Flex vertical gap="large">
      <ReviewQueueToolbar
        departments={session?.departments ?? []}
        departmentId={departmentId}
        onDepartmentChange={handleDepartmentChange}
        selectedCount={selectedIds.length}
        onRequestChanges={() => setOpenDialog('requestChanges')}
        onReject={() => setOpenDialog('reject')}
        onApprove={handleApprove}
      />
      <div className={styles.card}>
        <ReviewQueueTable
          rows={queue.data?.content}
          total={queue.data?.page.totalElements ?? 0}
          isLoading={queue.isPending}
          error={queue.isError ? queue.error : null}
          onRetry={() => void queue.refetch()}
          page={list.page}
          onPageChange={list.setPage}
          sort={list.sort}
          onSortChange={list.setSort}
          selectedIds={selectedIds}
          onSelectionChange={setSelectedIds}
          onSelectAll={(isChecked) => void handleSelectAll(isChecked)}
        />
      </div>

      <ConfirmDialog
        isOpen={openDialog === 'confirmApprove'}
        code="MSG19"
        params={{ count: selectedIds.length }}
        isConfirming={approve.isPending}
        onConfirm={runApprove}
        onCancel={() => setOpenDialog(null)}
      />
      <RejectTicketModal
        ticketIds={selectedIds}
        isOpen={openDialog === 'reject'}
        onClose={() => setOpenDialog(null)}
        onRejected={finishDecision}
      />
      <RequestChangesModal
        ticketIds={selectedIds}
        isOpen={openDialog === 'requestChanges'}
        onClose={() => setOpenDialog(null)}
        onSent={finishDecision}
      />
    </Flex>
  );
}

export default ReviewQueuePage;
