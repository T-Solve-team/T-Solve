/** 5.2 Import Preview — SRS III.5.2 */
import { Alert, Flex, Typography } from 'antd';
import { useState } from 'react';
import { Navigate, useSearchParams } from 'react-router';
import { ApiError, useGetImportPreview } from '@/shared/api';
import { isMsgCode, msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import { ErrorState, LoadingState, SectionCard, StatCard } from '@/shared/ui';
import { ImportPreviewTable } from '../components/ImportPreviewTable';

/** The address keeps the preview id (D11); anything but a positive whole number opens Import Tickets (AC-009.11). */
export function ImportPreviewPage() {
  const [searchParams] = useSearchParams();
  const previewId = searchParams.get('previewId') ?? '';
  if (!/^[1-9]\d*$/.test(previewId)) return <Navigate to={paths.importTickets} replace />;
  return <ImportPreview key={previewId} previewId={Number(previewId)} />;
}

function ImportPreview({ previewId }: { previewId: number }) {
  const preview = useGetImportPreview(previewId);
  const [selectedRowNos, setSelectedRowNos] = useState<number[]>([]);

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
        <ImportPreviewTable
          previewId={previewId}
          selectedRowNos={selectedRowNos}
          onSelectionChange={setSelectedRowNos}
        />
      </SectionCard>
    </Flex>
  );
}

export default ImportPreviewPage;
