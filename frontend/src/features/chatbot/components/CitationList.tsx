import { FileTextOutlined, ReadOutlined } from '@ant-design/icons';
import { Button, Typography } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router';
import type { ChatbotCitation, TicketSolutionCitation } from '@/shared/api';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import styles from './Citations.module.css';
import { TicketSolution } from './TicketSolution';

type CitationListProps = {
  citations: readonly ChatbotCitation[];
  /** Cited Wiki documents the user can no longer view: MSG110 in place of their link. */
  unavailableIds: ReadonlySet<number>;
};

/**
 * 12.1 citations (callout 4), in number order. A Wiki document opens 11.2 Wiki Document; a ticket shows only "Solution
 * from a ticket" and its Solution — never its id, link, Project, writer or Evidence (GBR-36).
 */
export function CitationList({ citations, unavailableIds }: CitationListProps) {
  const ordered = [...citations].sort((a, b) => a.number - b.number);
  return (
    <ul aria-label="Citations" className={styles.list}>
      {ordered.map((citation) => (
        <li key={citation.number} className={styles.item}>
          <span className={styles.number}>[{citation.number}]</span>
          {citation.type === 'WIKI_DOCUMENT' ? (
            <>
              <FileTextOutlined className={styles.icon} />
              {unavailableIds.has(citation.wikiDocumentId) ? (
                <Typography.Text type="secondary">{msg('MSG110')}</Typography.Text>
              ) : (
                <Link to={paths.wikiDocument(citation.wikiDocumentId)}>
                  {citation.title} · Wiki document
                </Link>
              )}
            </>
          ) : (
            <TicketCitation citation={citation} />
          )}
        </li>
      ))}
    </ul>
  );
}

function TicketCitation({ citation }: { citation: TicketSolutionCitation }) {
  const [isOpen, setIsOpen] = useState(false);
  return (
    <>
      <ReadOutlined className={styles.icon} />
      <span>Solution from a ticket</span>
      <Button type="link" size="small" aria-expanded={isOpen} onClick={() => setIsOpen(!isOpen)}>
        {isOpen ? 'Hide' : 'Show'}
      </Button>
      {isOpen && <TicketSolution solution={citation.solution} />}
    </>
  );
}
