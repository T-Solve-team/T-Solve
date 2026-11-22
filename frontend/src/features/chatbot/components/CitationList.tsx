import { Button, Typography } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router';
import type { ChatbotCitation, CitedSolution } from '@/shared/api';
import { msg } from '@/shared/messages';
import { paths } from '@/shared/routing';
import styles from './Citations.module.css';
import { TicketSolution } from './TicketSolution';

type CitationListProps = {
  citations: readonly ChatbotCitation[];
  /** Cited Wiki documents the user can no longer view: MSG110 in place of their link. */
  unavailableIds: ReadonlySet<string>;
  idPrefix: string;
  highlightedId?: string;
};

/**
 * 12.1 numbered citation notes (AC-001.7–AC-001.11), with a highlight when selected from the answer (AC-001.17).
 */
export function CitationList({
  citations,
  unavailableIds,
  idPrefix,
  highlightedId,
}: CitationListProps) {
  const ordered = [...citations].sort((a, b) => a.number - b.number);
  return (
    <ul aria-label="Citations" className={styles.list}>
      {ordered.map((citation) => {
        const id = `${idPrefix}-${citation.number}`;
        return (
          <li
            key={citation.number}
            id={id}
            className={id === highlightedId ? `${styles.item} ${styles.highlighted}` : styles.item}
          >
            <span className={styles.number}>[{citation.number}]</span>
            <div>
              {citation.type === 'WIKI_DOCUMENT' ? (
                unavailableIds.has(citation.wikiDocumentId) ? (
                  <Typography.Text type="secondary">{msg('MSG110')}</Typography.Text>
                ) : (
                  <Link to={paths.wikiDocument(citation.wikiDocumentId)} className={styles.link}>
                    {citation.title} · Wiki document
                  </Link>
                )
              ) : (
                <TicketSolutionNote id={`${id}-solution`} solution={citation.solution} />
              )}
            </div>
          </li>
        );
      })}
    </ul>
  );
}

function TicketSolutionNote({ id, solution }: { id: string; solution: CitedSolution }) {
  const [isExpanded, setIsExpanded] = useState(false);

  return (
    <>
      <span>Solution from a ticket</span>
      <Button
        type="link"
        size="small"
        className={styles.toggle}
        aria-expanded={isExpanded}
        aria-controls={id}
        onClick={() => setIsExpanded((expanded) => !expanded)}
      >
        {isExpanded ? 'Hide' : 'Show'}
      </Button>
      <div id={id} hidden={!isExpanded}>
        <TicketSolution solution={solution} />
      </div>
    </>
  );
}
