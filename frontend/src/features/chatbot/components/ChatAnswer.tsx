import { Typography } from 'antd';
import type { ChatbotAnswer } from '@/shared/api';
import { msg } from '@/shared/messages';
import { CitationList } from './CitationList';
import styles from './Conversation.module.css';

type ChatAnswerProps = {
  answer: ChatbotAnswer;
  /** Cited Wiki documents the user can no longer view (MSG110). */
  unavailableIds: ReadonlySet<number>;
};

/**
 * 12.1 answer (callout 3): the AI module's text as plain text — never HTML — with its [n] markers as written, then its
 * citations; MSG109 when no source was found (GBR-36).
 */
export function ChatAnswer({ answer, unavailableIds }: ChatAnswerProps) {
  return (
    <div className={styles.answer}>
      <Typography.Paragraph className={styles.text}>
        {answer.outcome === 'ANSWERED' ? answer.text : msg('MSG109')}
      </Typography.Paragraph>
      {answer.citations.length > 0 && (
        <CitationList citations={answer.citations} unavailableIds={unavailableIds} />
      )}
    </div>
  );
}
