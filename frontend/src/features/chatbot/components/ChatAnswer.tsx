import { Typography } from 'antd';
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
};

const ANSWER_MESSAGES = {
  NO_SOURCE: 'MSG109',
  GREETING: 'MSG_NEW_5',
  OFF_TOPIC: 'MSG_NEW_6',
  INJECTION: 'MSG_NEW_7',
} as const;

/**
 * 12.1 answer (callout 3): the AI module's text as plain text — never HTML — with its [n] markers as written, then its
 * citations; MSG109 when no source was found (GBR-36).
 */
export function ChatAnswer({ answer, unavailableIds, rating, isRating, onRate }: ChatAnswerProps) {
  const canRate = answer.outcome === 'ANSWERED' || answer.outcome === 'NO_SOURCE';

  return (
    <div className={styles.answer}>
      <Typography.Paragraph className={styles.text}>
        {answer.outcome === 'ANSWERED' ? answer.text : msg(ANSWER_MESSAGES[answer.outcome])}
      </Typography.Paragraph>
      {answer.outcome === 'ANSWERED' && answer.citations.length > 0 && (
        <CitationList citations={answer.citations} unavailableIds={unavailableIds} />
      )}
      {canRate && <RatingButtons rating={rating} disabled={isRating} onRate={onRate} />}
    </div>
  );
}
