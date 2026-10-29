import { LikeOutlined } from '@ant-design/icons';
import { Flex, Typography } from 'antd';
import { Link } from 'react-router';
import type { TicketSearchResult } from '@/shared/api';
import { formatDateTime } from '@/shared/lib';
import { paths } from '@/shared/routing';
import styles from './SearchResults.module.css';

/**
 * One result of the Search (Figma 6.1, spec AC-002.7): every API text is React text, never HTML (AC-002.14).
 */
export function SearchResultCard({ result }: { result: TicketSearchResult }) {
  return (
    <article className={styles.card}>
      <Flex vertical gap="small">
        <Link to={paths.ticketDetail(result.ticketId)} className={styles.title}>
          {result.title}
        </Link>
        <Flex align="center" gap="middle" wrap>
          <Typography.Text type="secondary">
            {`${result.project.name} · ${result.department.name} · Resolved ${formatDateTime(result.resolvedAt)}`}
          </Typography.Text>
          {result.helpfulRate !== null && ( // null = no Feedback yet: no rate (decision Q4, AC-002.8)
            <Typography.Text type="secondary">
              <LikeOutlined aria-hidden /> {`${result.helpfulRate}% helpful`}
            </Typography.Text>
          )}
        </Flex>
        <Typography.Paragraph className={styles.excerpt}>{result.excerpt}</Typography.Paragraph>
      </Flex>
    </article>
  );
}
