/** 7.1 Review Queue — SRS III.7.1, Figma "7.1 Review Queue" (decisions FE-10, FE-34). */
import { Flex } from 'antd';
import { useState } from 'react';
import { isApiError, useGetReviewQueueTicketIds } from '@/shared/api';
import { useSession } from '@/shared/auth';
import { showMessage } from '@/shared/messages';
import { useListSearchParams } from '@/shared/routing';
import { ConfirmDialog } from '@/shared/ui';
import { RejectTicketModal } from '../components/RejectTicketModal';
import { RequestChangesModal } from '../components/RequestChangesModal';
import { ReviewQueueTable } from '../components/ReviewQueueTable';
import { ReviewQueueToolbar } from '../components/ReviewQueueToolbar';
import { useQueueTicketIds } from '../hooks/useQueueTicketIds';
import { useApproveSelectedTickets } from '../hooks/useReviewDecisions';
import { REVIEW_QUEUE_POLL_MS, useReviewQueue } from '../hooks/useReviewQueue';
import styles from './ReviewQueuePage.module.css';

type OpenDialog = 'confirmApprove' | 'reject' | 'changes' | null;

/** Missing or invalid Project id means all managed Projects. */
function toProjectId(value: string | null): number | undefined {
  const id = Number(value);
  return value && Number.isInteger(id) && id > 0 ? id : undefined;
}

export function ReviewQueuePage() {
  const list = useListSearchParams({ field: 'reviewDueDate', order: 'asc' });
  const projectId = toProjectId(list.getFilter('projectId'));
  return <ProjectReviewQueue key={projectId ?? 'all'} projectId={projectId} list={list} />;
}

function ProjectReviewQueue({
  projectId,
  list,
}: {
  projectId: number | undefined;
  list: ReturnType<typeof useListSearchParams>;
}) {
  const { session } = useSession();
  const queue = useReviewQueue({ projectId, ...list.apiPaging });
  const loadAllTicketIds = useQueueTicketIds(projectId);
  const approve = useApproveSelectedTickets();

  // Selected tickets, across pages. Local state: it belongs to this screen only (guideline 05 C8).
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [openDialog, setOpenDialog] = useState<OpenDialog>(null);
  const [validatedAt, setValidatedAt] = useState(0);
  const eligibility = useGetReviewQueueTicketIds(
    projectId === undefined ? undefined : { projectId },
    {
      query: { enabled: selectedIds.length > 0, refetchInterval: REVIEW_QUEUE_POLL_MS },
    },
  );

  // Reconcile user selection against the complete queue, never just this page. Updating guarded
  // state during render prevents a stale selection being committed between a refetch and an effect.
  if (eligibility.data && eligibility.dataUpdatedAt !== validatedAt) {
    const available = new Set(eligibility.data.ticketIds);
    const remaining = selectedIds.filter((id) => available.has(id));
    setValidatedAt(eligibility.dataUpdatedAt);
    if (remaining.length !== selectedIds.length) {
      setSelectedIds(remaining);
      if (remaining.length === 0 || openDialog === 'confirmApprove') setOpenDialog(null);
    }
  }

  function finishDecision() {
    setOpenDialog(null);
    setSelectedIds([]);
  }

  function handleProjectChange(nextProjectId: number | undefined) {
    list.setFilter('projectId', nextProjectId === undefined ? null : String(nextProjectId));
    setSelectedIds([]); // a selection belongs to the filter it was made in
  }

  async function handleSelectAll(isChecked: boolean) {
    try {
      setSelectedIds(isChecked ? await loadAllTicketIds() : []);
    } catch (error) {
      showMessage(isApiError(error) ? error.code : 'MSG06', isApiError(error) ? error.params : {});
    }
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
        projects={
          session?.projects.filter((project) => project.roleInProject === 'PROJECT_MANAGER') ?? []
        }
        projectId={projectId}
        onProjectChange={handleProjectChange}
        selectedCount={selectedIds.length}
        isBusy={approve.isPending || openDialog !== null}
        onRequestChanges={() => setOpenDialog('changes')}
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
      <RequestChangesModal
        ticketIds={selectedIds}
        isOpen={openDialog === 'changes'}
        onClose={() => setOpenDialog(null)}
        onSent={finishDecision}
      />
      <RejectTicketModal
        ticketIds={selectedIds}
        isOpen={openDialog === 'reject'}
        onClose={() => setOpenDialog(null)}
        onRejected={finishDecision}
      />
    </Flex>
  );
}

export default ReviewQueuePage;
