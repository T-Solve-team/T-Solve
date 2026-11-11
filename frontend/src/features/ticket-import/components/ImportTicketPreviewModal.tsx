/** 5.3 Import Ticket Preview — SRS III.5.3 */
import { Descriptions, Flex, Typography } from 'antd';
import { useGetImportPreviewRow } from '@/shared/api';
import { DetailModal, ErrorState, LoadingState, StatusTag } from '@/shared/ui';
import { errorText, SECTIONS } from '../lib/import-row';
import { jiraSnapshotItems } from './jira-snapshot';
import styles from './ImportTicketPreviewModal.module.css';

type ImportTicketPreviewModalProps = {
  previewId: number;
  /** The row to show; null keeps the popup closed. */
  rowNo: number | null;
  onClose: () => void;
};

/** One preview row in full, read-only (AC-010.1); Close leaves the Import Preview as it was (AC-010.2). */
export function ImportTicketPreviewModal({
  previewId,
  rowNo,
  onClose,
}: ImportTicketPreviewModalProps) {
  return (
    <DetailModal
      isOpen={rowNo !== null}
      title="Import Ticket Preview"
      width={720}
      onClose={onClose}
    >
      {rowNo !== null && <RowDetail previewId={previewId} rowNo={rowNo} />}
    </DetailModal>
  );
}

function RowDetail({ previewId, rowNo }: { previewId: number; rowNo: number }) {
  const row = useGetImportPreviewRow(previewId, rowNo);
  if (row.isPending) return <LoadingState />;
  // Inside the popup; the page behind it stays (AC-010.3).
  if (row.isError) return <ErrorState error={row.error} onRetry={() => void row.refetch()} />;

  const { data } = row;
  return (
    <Descriptions
      column={1}
      bordered
      size="small"
      items={[
        { key: 'ticketId', label: 'Ticket ID', children: data.ticketId },
        { key: 'title', label: 'Title', children: data.title },
        { key: 'description', label: 'Description', children: data.description },
        { key: 'author', label: 'Author', children: data.author },
        { key: 'resolvedDate', label: 'Resolved date', children: data.resolvedDate },
        ...jiraSnapshotItems(data),
        ...SECTIONS.map(([label, field]) => ({
          key: field,
          label,
          children: <Typography.Text className={styles.fullText}>{data[field]}</Typography.Text>,
        })),
        { key: 'status', label: 'Result', children: <StatusTag status={data.status} /> },
        {
          key: 'errors',
          label: 'Error details',
          children: (
            <Flex vertical>
              {data.errors.map((error, i) => (
                <Typography.Text key={i} type="danger">
                  {errorText(error)}
                </Typography.Text>
              ))}
            </Flex>
          ),
        },
      ]}
    />
  );
}
