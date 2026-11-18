import { DownloadOutlined } from '@ant-design/icons';
import { Button, Flex, Typography } from 'antd';
import { useNavigate } from 'react-router';
import { formatDateTime } from '@/shared/lib';
import { paths } from '@/shared/routing';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui';
import { useWikiDocument, wikiDocumentFileUrl } from '../hooks/useWikiDocument';
import styles from '../pages/WikiHomePage.module.css';

type WikiPreviewProps = {
  /** The previewed row; undefined when the list is empty (MSG04) or not loaded yet. */
  documentId: string | undefined;
  isListPending: boolean;
};

/** 11.1 preview pane (AC-002.24, AC-002.25). */
export function WikiPreview({ documentId, isListPending }: WikiPreviewProps) {
  const navigate = useNavigate();
  const document = useWikiDocument(documentId ?? '');
  if (isListPending) return <LoadingState />;
  if (!documentId) return <EmptyState />;
  if (document.isPending) return <LoadingState />;
  if (document.isError) {
    if (document.error.status === 404 && document.error.code === 'MSG110')
      return <EmptyState code="MSG110" />;
    return <ErrorState error={document.error} onRetry={() => void document.refetch()} />;
  }
  const data = document.data;
  return (
    <Flex vertical gap="large">
      <Flex vertical gap="small">
        <Typography.Title level={3} className={styles.previewTitle}>
          {data.title}
        </Typography.Title>
        <Typography.Text type="secondary">
          {data.visibility === 'COMPANY_WIDE' ? 'Company-wide' : 'Department'}
        </Typography.Text>
      </Flex>
      <dl className={styles.details}>
        <dt>Space</dt>
        <dd>{data.spaceName}</dd>
        <dt>Folder</dt>
        <dd>{data.folderName ?? '—'}</dd>
        <dt>Owner</dt>
        <dd>{data.owner}</dd>
        <dt>Updated</dt>
        <dd>{formatDateTime(data.updatedAt)}</dd>
        <dt>File</dt>
        <dd>{data.fileName}</dd>
        <dt>Size</dt>
        <dd>{`${Math.max(1, Math.round(data.fileSize / 1024))} KB`}</dd>
      </dl>
      <div className={styles.excerpt}>{data.content.slice(0, 420)}</div>
      <Flex gap="small">
        <Button
          type="primary"
          block
          onClick={() => void navigate(paths.wikiDocument(data.documentId))}
        >
          Open
        </Button>
        <Button
          block
          icon={<DownloadOutlined />}
          href={wikiDocumentFileUrl(data.documentId)}
          download={data.fileName}
        >
          Download
        </Button>
      </Flex>
    </Flex>
  );
}
