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
  const resolved = result.resolvedAt === null ? '—' : formatDateTime(result.resolvedAt);

  return (
    <article className={styles.card}>
      <Link to={paths.ticketDetail(result.ticketId)} className={styles.title}>
        {result.title}
      </Link>
      <Flex align="baseline" gap="small" wrap>
        <Typography.Text type="secondary">
          {`${result.project.name} · ${result.department.name} · Resolved ${resolved}`}
        </Typography.Text>
        {result.helpfulRate !== null && ( // null = no Feedback yet: no rate (decision Q4, AC-002.8)
          <Typography.Text type="secondary" className={styles.helpful}>
            <LikeOutlined aria-hidden /> {`${result.helpfulRate}% helpful`}
          </Typography.Text>
        )}
      </Flex>
      <p className={styles.excerpt}>{result.excerpt}</p>
    </article>
  );
}
