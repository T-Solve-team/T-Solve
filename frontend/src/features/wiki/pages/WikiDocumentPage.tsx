/** 11.2 Wiki Document — SRS III.11.2, Report3 UC-49. */
import { Button, Descriptions, Flex, Typography } from 'antd';
import { useParams } from 'react-router';
import { formatDateTime } from '@/shared/lib';
import { EmptyState, ErrorState, LoadingState, SectionCard } from '@/shared/ui';
import { useWikiDocument, wikiDocumentFileUrl } from '../hooks/useWikiDocument';
import styles from './WikiDocumentPage.module.css';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function WikiDocumentPage() {
  const { documentId = '' } = useParams();
  const isValidId = UUID_PATTERN.test(documentId);
  const document = useWikiDocument(isValidId ? documentId : '');
  if (!isValidId) return <EmptyState code="MSG110" />;
  if (document.isPending) return <LoadingState />;
  if (document.isError) {
    if (document.error.status === 404 && document.error.code === 'MSG110')
      return <EmptyState code="MSG110" />;
    return <ErrorState error={document.error} onRetry={() => void document.refetch()} />;
  }
  const data = document.data;
  return (
    <Flex vertical gap="large">
      <SectionCard>
        <Flex justify="space-between" align="center" gap="middle">
          <Typography.Title level={2}>{data.title}</Typography.Title>
          <Button href={wikiDocumentFileUrl(data.documentId)} download={data.fileName}>
            Download
          </Button>
        </Flex>
        <Descriptions
          items={[
            { key: 'space', label: 'Space', children: data.spaceName },
            { key: 'folder', label: 'Folder', children: data.folderName ?? '—' },
            {
              key: 'visibility',
              label: 'Visibility',
              children: data.visibility === 'COMPANY_WIDE' ? 'Company-wide' : 'Department',
            },
            { key: 'owner', label: 'Owner', children: data.owner },
            { key: 'updated', label: 'Updated', children: formatDateTime(data.updatedAt) },
          ]}
        />
      </SectionCard>
      <SectionCard>
        <section aria-label="Content" className={styles.content}>
          {data.content}
        </section>
      </SectionCard>
    </Flex>
  );
}

export default WikiDocumentPage;
