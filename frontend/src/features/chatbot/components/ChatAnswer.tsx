import { Button } from 'antd';
import { useId } from 'react';
import type { ChatbotAnswer } from '@/shared/api';
import { msg } from '@/shared/messages';
import type { ChatTurn } from '../model';
import { CitationList } from './CitationList';
import { RatingButtons } from './RatingButtons';
import styles from './Conversation.module.css';

type ChatAnswerProps = {
  answer: ChatbotAnswer;
  /** Cited Wiki documents the user can no longer view (MSG110). */
  unavailableIds: ReadonlySet<string>;
  rating: ChatTurn['rating'];
  /** A rating request is running (AC-003.2). */
  isRating: boolean;
  onRate: (rating: ChatTurn['rating']) => void;
  highlightedCitationId?: string;
  onHighlightCitation: (id: string) => void;
};

const ANSWER_MESSAGES = {
  NO_SOURCE: 'MSG109',
  GREETING: 'MSG_NEW_5',
  OFF_TOPIC: 'MSG_NEW_6',
  INJECTION: 'MSG_NEW_7',
} as const;

/**
 * 12.1 answer (callout 3): plain text and numbered notes (AC-001.7, AC-001.8), with markers that jump to
 * their citations (AC-001.17). Other outcomes use the MSG catalog (AC-001.12, AC-001.15).
 */
export function ChatAnswer({
  answer,
  unavailableIds,
  rating,
  isRating,
  onRate,
  highlightedCitationId,
  onHighlightCitation,
}: ChatAnswerProps) {
  const citationPrefix = useId();
  const { outcome } = answer;
  const isAnswered = outcome === 'ANSWERED';
  const canRate = isAnswered || outcome === 'NO_SOURCE';

  function renderMarker(digits: string, index: number) {
    const number = Number(digits);
    const hasCitation = answer.citations.some((citation) => citation.number === number);
    const targetId = `${citationPrefix}-${number}`;

    return (
      <Button
        key={index}
        size="small"
        shape="round"
        className={styles.marker}
        disabled={!hasCitation}
        aria-controls={hasCitation ? targetId : undefined}
        aria-pressed={highlightedCitationId === targetId}
        onClick={() => {
          onHighlightCitation(targetId);
          document.getElementById(targetId)?.scrollIntoView?.({ block: 'nearest' });
        }}
      >
        {`[${digits}]`}
      </Button>
    );
  }

  return (
    <div className={styles.answer}>
      <p className={styles.text}>
        {isAnswered
          ? answer.text
              .split(/\[(\d+)\]/)
              .map((part, index) => (index % 2 === 0 ? part : renderMarker(part, index)))
          : msg(ANSWER_MESSAGES[outcome])}
      </p>
      {isAnswered && answer.citations.length > 0 && (
        <CitationList
          citations={answer.citations}
          unavailableIds={unavailableIds}
          idPrefix={citationPrefix}
          highlightedId={highlightedCitationId}
        />
      )}
      {canRate && <RatingButtons rating={rating} disabled={isRating} onRate={onRate} />}
    </div>
  );
}
