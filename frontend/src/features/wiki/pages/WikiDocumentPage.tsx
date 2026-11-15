/** 11.2 Wiki Document — SRS III.11.2, Report3 UC-49. */
import { Button, Descriptions, Flex, Space, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { formatDateTime } from '@/shared/lib';
import { paths } from '@/shared/routing';
import {
  ConfirmDialog,
  EmptyState,
  ErrorState,
  LoadingState,
  SectionCard,
  StatusTag,
} from '@/shared/ui';
import { useWikiDocument, wikiDocumentFileUrl } from '../hooks/useWikiDocument';
import { useWikiTrashMutations } from '../hooks/useWikiTrashMutations';
import styles from './WikiDocumentPage.module.css';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function WikiDocumentPage() {
  const { documentId = '' } = useParams();
  const isValidId = UUID_PATTERN.test(documentId);
  const document = useWikiDocument(isValidId ? documentId : '');
  const navigate = useNavigate();
  const [isDeleteOpen, setDeleteOpen] = useState(false);
  const { trash } = useWikiTrashMutations(document.data?.spaceId ?? '');
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
          <Space>
            <Typography.Title level={2}>{data.title}</Typography.Title>
            {data.inTrash && <StatusTag status="IN_TRASH" />}
          </Space>
          <Space>
            {!data.inTrash && (
              <Button href={wikiDocumentFileUrl(data.documentId)} download={data.fileName}>
                Download
              </Button>
            )}
            {data.canDelete && !data.inTrash && (
              <Button danger disabled={trash.isPending} onClick={() => setDeleteOpen(true)}>
                Delete
              </Button>
            )}
          </Space>
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
      {isDeleteOpen && (
        <ConfirmDialog
          isOpen
          code="MSG100"
          params={{ title: data.title }}
          isDanger
          isConfirming={trash.isPending}
          onCancel={() => {
            if (!trash.isPending) setDeleteOpen(false);
          }}
          onConfirm={() => {
            if (trash.isPending) return;
            trash.mutate(
              { documentId },
              {
                onSuccess: () => {
                  void navigate(`${paths.wikiHome}?space=${data.spaceId}`);
                },
              },
            );
          }}
        />
      )}
      <SectionCard>
        <section aria-label="Content" className={styles.content}>
          {data.content}
        </section>
      </SectionCard>
    </Flex>
  );
}

export default WikiDocumentPage;
