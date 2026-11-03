import { Descriptions, Flex, Typography } from 'antd';
import type { ReviewTicketDetail } from '@/shared/api';
import { formatDateTime, TICKET_SOURCE_LABELS } from '@/shared/lib';
import { msg } from '@/shared/messages';
import { SectionCard, StatusTag } from '@/shared/ui';
import styles from './ReviewTicketDetail.module.css';

function safeReference(value: string | null): string | undefined {
  if (!value || /^[/\\]{2}/.test(value.trim())) return undefined;
  try {
    const url = new URL(value, window.location.origin);
    if (!['http:', 'https:'].includes(url.protocol)) return undefined;
    return /^https?:\/\//i.test(value) || url.origin === window.location.origin ? value : undefined;
  } catch {
    return undefined;
  }
}

function readableSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Number((bytes / 1024).toFixed(1))} KB`;
  return `${Number((bytes / (1024 * 1024)).toFixed(1))} MB`;
}

export function ReviewTicketContent({ ticket }: { ticket: ReviewTicketDetail }) {
  return (
    <Flex vertical gap="large" className={styles.content}>
      <SectionCard title="Ticket">
        <Typography.Title level={2}>{ticket.sourceTicketId}</Typography.Title>
        <Descriptions
          column={1}
          items={[
            { key: 'title', label: 'Title', children: ticket.title },
            {
              key: 'description',
              label: 'Description',
              children: <span className={styles.text}>{ticket.description}</span>,
            },
            { key: 'project', label: 'Project', children: ticket.project.name },
            { key: 'source', label: 'Source', children: TICKET_SOURCE_LABELS[ticket.source] },
            { key: 'status', label: 'Status', children: <StatusTag status={ticket.status} /> },
            { key: 'author', label: 'Author', children: ticket.authorName ?? '—' },
            {
              key: 'due',
              label: 'Review due date',
              children: ticket.reviewDueDate ? formatDateTime(ticket.reviewDueDate) : '—',
            },
          ]}
        />
        {ticket.source !== 'SOLUTION_FORM' && ticket.status === 'PUBLISHED' && (
          <p role="note">{msg('MSG18')}</p>
        )}
      </SectionCard>
      <SectionCard title="Solution">
        <Descriptions
          column={1}
          items={[
            { key: 'symptoms', label: 'Symptoms', children: ticket.solution.symptoms },
            { key: 'rootCause', label: 'Root cause', children: ticket.solution.rootCause },
            {
              key: 'resolutionSteps',
              label: 'Resolution steps',
              children: ticket.solution.resolutionSteps,
            },
            { key: 'workaround', label: 'Workaround', children: ticket.solution.workaround ?? '—' },
            { key: 'scope', label: 'Scope', children: ticket.solution.scope ?? '—' },
          ].map((item) => ({
            ...item,
            children: <span className={styles.text}>{item.children}</span>,
          }))}
        />
        {ticket.lastReviewComment && (
          <>
            <Typography.Title level={3}>Review comment</Typography.Title>
            <p className={styles.text}>{ticket.lastReviewComment}</p>
          </>
        )}
      </SectionCard>
      <SectionCard title="Evidence">
        {ticket.evidence.length === 0 ? (
          <p>{msg('MSG04')}</p>
        ) : (
          <ul className={styles.evidence}>
            {ticket.evidence.map((file) => {
              const href = safeReference(file.url);
              return (
                <li key={file.id}>
                  {href ? (
                    <a href={href} target="_blank" rel="noopener noreferrer">
                      {file.filename}
                    </a>
                  ) : (
                    <span>{file.filename}</span>
                  )}{' '}
                  <span>({readableSize(file.sizeBytes)})</span>
                </li>
              );
            })}
          </ul>
        )}
      </SectionCard>
    </Flex>
  );
}
