/** 5.2 Import Preview — SRS III.5.2, shared by a CSV upload and a Jira pull */
import { Alert, Button, Flex, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { Navigate, useBlocker, useNavigate, useSearchParams } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetImportPreviewQueryKey,
  getGetImportPreviewRowsQueryKey,
  useGetImportPreview,
  type ApiError,
  type ImportPreviewRowSummary,
} from '@/shared/api';
import { formatDate, TICKET_SOURCE_LABELS } from '@/shared/lib';
import { isMsgCode, msg, showMessage } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { ConfirmDialog, ErrorState, LoadingState, SectionCard, StatCard } from '@/shared/ui';
import { ImportPreviewTable } from '../components/ImportPreviewTable';
import { ImportTicketPreviewModal } from '../components/ImportTicketPreviewModal';
import { SolutionFormModal } from '../components/SolutionFormModal';
import { useConfirmPreview, useRemovePreviewRows, useSaveDraft } from '../hooks/useImportPreview';

/** The address keeps the preview id (D11); anything but a positive whole number opens Import Tickets (AC-009.11). */
export function ImportPreviewPage() {
  const [searchParams] = useSearchParams();
  const previewId = searchParams.get('previewId') ?? '';
  if (!/^[1-9]\d*$/.test(previewId)) return <Navigate to={paths.importTickets} replace />;
  return <ImportPreview key={previewId} previewId={Number(previewId)} />;
}

function ImportPreview({ previewId }: { previewId: number }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const preview = useGetImportPreview(previewId);
  const remove = useRemovePreviewRows(previewId);
  const confirm = useConfirmPreview();
  const saveDraft = useSaveDraft(previewId);
  const [selectedRows, setSelectedRows] = useState<
    Pick<ImportPreviewRowSummary, 'rowNo' | 'status'>[]
  >([]);
  const selectedRowNos = selectedRows.map((row) => row.rowNo);
  const [isFillOpen, setIsFillOpen] = useState(false);
  const [previewRow, setPreviewRow] = useState<ImportPreviewRowSummary | null>(null);

  // A double click reaches the handler twice before `isPending` renders: one Confirm only (AC-009.8).
  const isConfirming = useRef(false);
  // Set before the move a successful Confirm makes, so only that move skips MSG37 (AC-009.6).
  const isFinished = useRef(false);
  // Every move to another screen before Confirm asks MSG37, Cancel included (AC-009.9, AC-009.10).
  // A preview that could not be read has nothing to lose. Only the path is compared: nothing in the app
  // moves from one previewId to another on this page (Upload always starts from Import Tickets).
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      preview.isSuccess &&
      !isFinished.current &&
      currentLocation.pathname !== nextLocation.pathname,
  );

  /** To Import Tickets without MSG37. The preview is gone, so browser Back must not show it from the cache. */
  function leave() {
    isFinished.current = true;
    // Removed once the page has left, so its own queries do not refetch it.
    void Promise.resolve(navigate(paths.importTickets)).then(() => {
      for (const queryKey of [
        getGetImportPreviewQueryKey(previewId),
        getGetImportPreviewRowsQueryKey(previewId),
      ]) {
        queryClient.removeQueries({ queryKey });
      }
    });
  }

  const isJira = preview.data?.source === 'JIRA_IMPORT';
  // A new pull in another tab deletes this Jira preview: any 404 about it goes back to Import Tickets (design Edge 10).
  const leaveIfGone = (error: ApiError) => {
    if (isJira && error.status === 404 && !isFinished.current) leave();
  };
  const isGone = isJira && preview.error?.status === 404;
  // Declared after useBlocker, so the blocker already knows the preview could not be read.
  useEffect(() => {
    if (isGone && !isFinished.current) leave();
  });

  if (preview.isPending) return <LoadingState />;
  if (preview.isError) {
    const { error } = preview;
    // 404 carries MSG04 and 403 MSG08. Neither changes on a retry (AC-009.12).
    const isFinal = error.status === 404 || error.status === 403;
    return (
      <ErrorState error={error} onRetry={isFinal ? undefined : () => void preview.refetch()} />
    );
  }

  function handleRemove() {
    remove.mutate(
      { previewId, data: { rowNumbers: selectedRowNos } },
      { onSuccess: () => setSelectedRows([]), onError: leaveIfGone },
    );
  }

  function handleConfirm() {
    if (isConfirming.current) return;
    isConfirming.current = true;
    confirm.mutate(
      { previewId },
      {
        onSettled: () => {
          isConfirming.current = false;
        },
        onSuccess: leave,
        onError: (error) => {
          // The global MSG07 dialog is the one message for a 401 (guideline 06 §6).
          if (error.status !== 401) showMessage(error.code, error.params);
          leaveIfGone(error);
        },
      },
    );
  }

  const {
    fileName,
    resolvedFrom,
    resolvedTo,
    importType,
    validCount,
    errorCount,
    removedCount,
    needsSolutionCount,
    message,
  } = preview.data;
  const isWithoutSolution = importType === 'WITHOUT_SOLUTION';
  const unreadyCount = needsSolutionCount + errorCount;
  // A Jira preview is WITHOUT_SOLUTION too: Finish waits for every issue's Solution (AC-002.10).
  const isConfirmBlocked = isWithoutSolution && unreadyCount > 0;
  const canFill =
    selectedRows.length > 0 &&
    selectedRows.every((row) => row.status === 'NEEDS_SOLUTION' || row.status === 'VALID');
  const replaceCount = selectedRows.filter((row) => row.status === 'VALID').length;
  // A row of a file without Solutions that can take a Solution opens with its sections as fields (AC-015.2).
  const isWritable =
    isWithoutSolution &&
    (previewRow?.status === 'NEEDS_SOLUTION' || previewRow?.status === 'VALID');
  return (
    <Flex vertical gap="middle">
      <SectionCard title="Summary">
        <Flex vertical gap="middle">
          {isJira ? (
            <Flex gap="middle" wrap>
              <Typography.Text strong>{TICKET_SOURCE_LABELS.JIRA_IMPORT}</Typography.Text>
              <Typography.Text>Resolved from: {formatDate(resolvedFrom)}</Typography.Text>
              <Typography.Text>Resolved to: {formatDate(resolvedTo)}</Typography.Text>
            </Flex>
          ) : (
            <Typography.Text strong>{fileName}</Typography.Text>
          )}
          <Flex gap="middle" wrap>
            <StatCard label="Valid" value={validCount} />
            {!isJira && <StatCard label="Error" value={errorCount} />}
            <StatCard label="Removed" value={removedCount} />
            {isWithoutSolution && <StatCard label="Needs Solution" value={needsSolutionCount} />}
          </Flex>
          {message && isMsgCode(message.code) && (
            <Alert type="warning" showIcon title={msg(message.code, message.params)} />
          )}
          {isConfirmBlocked && (
            <Alert type="warning" showIcon title={msg('MSG_NEW_4', { count: unreadyCount })} />
          )}
        </Flex>
      </SectionCard>
      <SectionCard>
        <Flex vertical gap="middle">
          <Flex gap="small">
            {isWithoutSolution && (
              <Button
                disabled={!canFill || remove.isPending || confirm.isPending}
                onClick={() => setIsFillOpen(true)}
              >
                Fill solution
              </Button>
            )}
            <Button
              disabled={selectedRowNos.length === 0 || confirm.isPending}
              loading={remove.isPending}
              onClick={handleRemove}
            >
              Remove
            </Button>
          </Flex>
          <ImportPreviewTable
            previewId={previewId}
            selectedRowNos={selectedRowNos}
            onSelectionChange={(rows) =>
              setSelectedRows(rows.map(({ rowNo, status }) => ({ rowNo, status })))
            }
            onPreview={setPreviewRow}
          />
          <Flex gap="small" justify="flex-end">
            <Button onClick={() => void navigate(paths.importTickets)}>Cancel</Button>
            {/* No Save as draft for a CSV preview (import-ticket-list D13). */}
            {isJira && (
              <Button
                disabled={remove.isPending || confirm.isPending}
                loading={saveDraft.isPending}
                onClick={() => saveDraft.mutate({ previewId }, { onError: leaveIfGone })}
              >
                Save as draft
              </Button>
            )}
            <Button
              type="primary"
              // One action at a time: a Confirm during a Remove could import a removed row.
              disabled={
                validCount === 0 || isConfirmBlocked || remove.isPending || saveDraft.isPending
              }
              loading={confirm.isPending}
              onClick={handleConfirm}
            >
              {isJira ? 'Finish' : 'Confirm'}
            </Button>
          </Flex>
        </Flex>
      </SectionCard>
      <ImportTicketPreviewModal
        previewId={previewId}
        rowNo={isWritable ? null : (previewRow?.rowNo ?? null)}
        onClose={() => setPreviewRow(null)}
      />
      <SolutionFormModal
        key={isFillOpen ? 'selection' : 'row'}
        previewId={previewId}
        rowNo={isWritable ? (previewRow?.rowNo ?? null) : null}
        rowNumbers={isFillOpen ? selectedRowNos : undefined}
        replaceCount={isFillOpen ? replaceCount : 0}
        onSuccess={() => {
          if (isFillOpen) setSelectedRows([]);
          else {
            setSelectedRows((rows) =>
              rows.map((row) =>
                row.rowNo === previewRow?.rowNo ? { ...row, status: 'VALID' } : row,
              ),
            );
          }
        }}
        onError={leaveIfGone}
        onClose={() => {
          setPreviewRow(null);
          setIsFillOpen(false);
        }}
      />
      <ConfirmDialog
        isOpen={blocker.state === 'blocked'}
        code="MSG37"
        isDanger
        onConfirm={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      />
    </Flex>
  );
}

export default ImportPreviewPage;
