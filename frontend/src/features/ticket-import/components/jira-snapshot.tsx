import { Flex, Typography, type DescriptionsProps } from 'antd';
import type { ImportPreviewRowResponse } from '@/shared/api';
import { formatDateTime } from '@/shared/lib';
import styles from './ImportTicketPreviewModal.module.css';

/**
 * SRS III.5.3 "Comments" and "source link" of a Jira issue as pulled (AC-001.2, AC-002.2); none for a CSV row, which
 * has no link.
 */
export function jiraSnapshotItems({
  sourceUrl,
  comments = [],
}: ImportPreviewRowResponse): NonNullable<DescriptionsProps['items']> {
  if (!sourceUrl) return [];
  return [
    {
      key: 'comments',
      label: 'Comments',
      children: (
        <Flex vertical gap="small">
          {comments.map((comment, i) => (
            <Flex key={i} vertical>
              <Typography.Text type="secondary">
                {[comment.author, formatDateTime(comment.created)].filter(Boolean).join(' · ')}
              </Typography.Text>
              <Typography.Text className={styles.fullText}>{comment.body}</Typography.Text>
            </Flex>
          ))}
        </Flex>
      ),
    },
    {
      key: 'sourceUrl',
      label: 'Source link',
      children: (
        <Typography.Link href={sourceUrl} target="_blank" rel="noreferrer">
          {sourceUrl}
        </Typography.Link>
      ),
    },
  ];
}
