/** 7.1 Review Queue — SRS III.7.1, Figma "7.1 Review Queue" (decisions FE-10, FE-34). */
import { Flex } from 'antd';
import { useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { useSession } from '@/shared/auth';
import { useListSearchParams } from '@/shared/routing';
import { ConfirmDialog, ErrorState } from '@/shared/ui';
import { RejectTicketModal } from '../components/RejectTicketModal';
import { RequestChangesModal } from '../components/RequestChangesModal';
import { ReviewQueueTable } from '../components/ReviewQueueTable';
import { ReviewQueueToolbar } from '../components/ReviewQueueToolbar';
import { useQueueTicketIds } from '../hooks/useQueueTicketIds';
import { useApproveSelectedTickets } from '../hooks/useReviewDecisions';
import { useReviewQueue } from '../hooks/useReviewQueue';
import styles from './ReviewQueuePage.module.css';
import { MAX_REVIEW_SELECTION } from '../review-limits';

type OpenDialog = 'confirmApprove' | 'reject' | 'requestChanges' | null;

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** "?departmentId=<uuid>" → the id; missing or not a UUID → all Departments. */
function toDepartmentId(value: string | null): string | undefined {
  return value && UUID_PATTERN.test(value) ? value : undefined;
}

export function ReviewQueuePage() {
  const [params] = useSearchParams();
  const departmentId = toDepartmentId(params.get('departmentId'));
  // A filter is a selection boundary, including Back/Forward navigation and in-flight Select all.
  return <ReviewQueueContent key={departmentId ?? 'all'} departmentId={departmentId} />;
}

function ReviewQueueContent({ departmentId }: { departmentId: string | undefined }) {
  const { session } = useSession();
  const list = useListSearchParams({ field: 'reviewDueDate', order: 'asc' }); // SRS: earliest due first
  const queue = useReviewQueue({ departmentId, ...list.apiPaging });
  const approve = useApproveSelectedTickets();

  // Selected tickets, across pages. Local state: it belongs to this screen only (guideline 05 C8).
  const [selection, setSelectedIds] = useState<string[]>([]);
  const eligibility = useQueueTicketIds(departmentId, selection.length > 0, queue.dataUpdatedAt);
  const selectedIds = eligibility.isFresh
    ? selection.filter((id) => eligibility.data?.ticketIds.includes(id))
    : selection;
  // Persist pruning so a ticket returning later is not silently selected again.
  if (selectedIds.length !== selection.length) setSelectedIds(selectedIds);
  const [openDialog, setOpenDialog] = useState<OpenDialog>(null);
  const [isSelectingAll, setSelectingAll] = useState(false);
  const submitting = useRef(false);
  const invalidSelection =
    selectedIds.length === 0 || selectedIds.length > MAX_REVIEW_SELECTION || !eligibility.isFresh;

  function finishDecision() {
    setOpenDialog(null);
    setSelectedIds([]);
  }

  function handleDepartmentChange(nextDepartmentId: string | undefined) {
    list.setFilter('departmentId', nextDepartmentId ?? null);
    setSelectedIds([]); // a selection belongs to the filter it was made in
  }

  async function handleSelectAll(isChecked: boolean) {
    if (!isChecked) {
      setSelectedIds([]);
      return;
    }
    setSelectingAll(true);
    try {
      setSelectedIds(await eligibility.loadAll());
    } catch {
      /* The eligibility query exposes its catalog error and retry below. */
    } finally {
      setSelectingAll(false);
    }
  }

  function runApprove() {
    if (invalidSelection || submitting.current) return;
    submitting.current = true;
    approve.mutate(
      { data: { ticketIds: selectedIds } },
      {
        onSuccess: finishDecision,
        onError: () => setOpenDialog(null),
        onSettled: () => {
          submitting.current = false;
        },
      },
    );
  }

  // SRS 7.1: approving two or more tickets asks first (MSG19); one ticket is approved at once.
  function handleApprove() {
    if (invalidSelection || submitting.current) return;
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
        isDisabled={invalidSelection || approve.isPending || isSelectingAll || openDialog !== null}
        onRequestChanges={() => setOpenDialog('requestChanges')}
        onReject={() => setOpenDialog('reject')}
        onApprove={handleApprove}
      />
      {eligibility.isError && (
        <ErrorState error={eligibility.error} onRetry={() => void eligibility.refetch()} />
      )}
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
          isDisabled={approve.isPending || isSelectingAll || openDialog !== null}
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
        onCancel={() => {
          if (!submitting.current) setOpenDialog(null);
        }}
      />
      <RejectTicketModal
        ticketIds={selectedIds}
        isDisabled={invalidSelection}
        isOpen={openDialog === 'reject'}
        onClose={() => setOpenDialog(null)}
        onRejected={finishDecision}
      />
      <RequestChangesModal
        ticketIds={selectedIds}
        isDisabled={invalidSelection}
        isOpen={openDialog === 'requestChanges'}
        onClose={() => setOpenDialog(null)}
        onSent={finishDecision}
      />
    </Flex>
  );
}

export default ReviewQueuePage;
