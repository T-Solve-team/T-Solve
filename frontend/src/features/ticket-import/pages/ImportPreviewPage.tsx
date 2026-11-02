/** 5.2 Import Preview — SRS III.5.2 */
import { Alert, Button, Flex, Typography } from 'antd';
import { useRef, useState } from 'react';
import { Navigate, useBlocker, useNavigate, useSearchParams } from 'react-router';
import { ApiError, useGetImportPreview } from '@/shared/api';
import { isMsgCode, msg, showMessage } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { ConfirmDialog, ErrorState, LoadingState, SectionCard, StatCard } from '@/shared/ui';
import { ImportPreviewTable } from '../components/ImportPreviewTable';
import { useConfirmPreview, useRemovePreviewRows } from '../hooks/useImportPreview';

/** The address keeps the preview id (D11); anything but a positive whole number opens Import Tickets (AC-009.11). */
export function ImportPreviewPage() {
  const [searchParams] = useSearchParams();
  const previewId = searchParams.get('previewId') ?? '';
  if (!/^[1-9]\d*$/.test(previewId)) return <Navigate to={paths.importTickets} replace />;
  return <ImportPreview key={previewId} previewId={Number(previewId)} />;
}

function ImportPreview({ previewId }: { previewId: number }) {
  const navigate = useNavigate();
  const preview = useGetImportPreview(previewId);
  const remove = useRemovePreviewRows(previewId);
  const confirm = useConfirmPreview();
  const [selectedRowNos, setSelectedRowNos] = useState<number[]>([]);

  // A double click reaches the handler twice before `isPending` renders: one Confirm only (AC-009.8).
  const isConfirming = useRef(false);
  // Set before the move a successful Confirm makes, so only that move skips MSG37 (AC-009.6).
  const isFinished = useRef(false);
  // Every move to another screen before Confirm asks MSG37, Cancel included (AC-009.9, AC-009.10).
  // A preview that could not be read has nothing to lose.
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      preview.isSuccess &&
      !isFinished.current &&
      currentLocation.pathname !== nextLocation.pathname,
  );

  if (preview.isPending) return <LoadingState />;
  if (preview.isError) {
    const { error } = preview;
    // 404 comes without a code (AC-007.2); 403 carries MSG08. Neither changes on a retry (AC-009.12).
    const isFinal = error.status === 404 || error.status === 403;
    return (
      <ErrorState
        error={error.status === 404 ? new ApiError({ status: 404, code: 'MSG04' }) : error}
        onRetry={isFinal ? undefined : () => void preview.refetch()}
      />
    );
  }

  function handleRemove() {
    remove.mutate(
      { previewId, data: { rowNumbers: selectedRowNos } },
      { onSuccess: () => setSelectedRowNos([]) },
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
        onSuccess: () => {
          isFinished.current = true;
          void navigate(paths.importTickets);
        },
        onError: (error) => showMessage(error.code, error.params),
      },
    );
  }

  const { fileName, validCount, errorCount, removedCount, message } = preview.data;
  return (
    <Flex vertical gap="middle">
      <SectionCard title="Summary">
        <Flex vertical gap="middle">
          <Typography.Text strong>{fileName}</Typography.Text>
          <Flex gap="middle" wrap>
            <StatCard label="Valid" value={validCount} />
            <StatCard label="Error" value={errorCount} />
            <StatCard label="Removed" value={removedCount} />
          </Flex>
          {message && isMsgCode(message.code) && (
            <Alert type="warning" showIcon title={msg(message.code, message.params)} />
          )}
        </Flex>
      </SectionCard>
      <SectionCard>
        <Flex vertical gap="middle">
          <Flex gap="small">
            <Button
              disabled={selectedRowNos.length === 0}
              loading={remove.isPending}
              onClick={handleRemove}
            >
              Remove
            </Button>
          </Flex>
          <ImportPreviewTable
            previewId={previewId}
            selectedRowNos={selectedRowNos}
            onSelectionChange={setSelectedRowNos}
          />
          <Flex gap="small" justify="flex-end">
            <Button onClick={() => void navigate(paths.importTickets)}>Cancel</Button>
            <Button
              type="primary"
              disabled={validCount === 0}
              loading={confirm.isPending}
              onClick={handleConfirm}
            >
              Confirm
            </Button>
          </Flex>
        </Flex>
      </SectionCard>
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
